package com.example.seckill.service.impl;

import cn.hutool.core.util.IdUtil;
import com.example.seckill.entity.Order;
import com.example.seckill.entity.Product;
import com.example.seckill.mapper.OrderMapper;
import com.example.seckill.mapper.ProductMapper;
import com.example.seckill.mq.OrderTimeoutProducer;
import com.example.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀核心服务实现
 *
 * <p><b>三层防护体系：</b>
 * <ol>
 *   <li>Redis + Lua 原子性库存预扣减 → 拦截绝大部分无效请求</li>
 *   <li>MySQL 乐观锁（version 字段）最终扣减 → 消除超卖的最后一道防线</li>
 *   <li>RocketMQ 延迟消息超时取消 → 自动回收未支付订单占用的库存</li>
 * </ol>
 */
@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    private static final String REDIS_STOCK_KEY = "seckill:stock:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Long> deductStockScript;
    private final RedisScript<Long> rollbackStockScript;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;

    /** MQ 生产者可选注入 — 无 RocketMQ 环境时不影响核心流程 */
    @Autowired(required = false)
    private OrderTimeoutProducer orderTimeoutProducer;

    public SeckillServiceImpl(RedisTemplate<String, Object> redisTemplate,
                              RedisScript<Long> deductStockScript,
                              RedisScript<Long> rollbackStockScript,
                              ProductMapper productMapper,
                              OrderMapper orderMapper) {
        this.redisTemplate = redisTemplate;
        this.deductStockScript = deductStockScript;
        this.rollbackStockScript = rollbackStockScript;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
    }

    // ===================================================================
    //  1. 下单 — Redis 预扣减 + 创建订单 + 发送延迟消息
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> seckill(Long productId, String userId, Integer quantity) {

        // ---- Step 1: Redis + Lua 原子性库存预扣减 ----
        String stockKey = REDIS_STOCK_KEY + productId;
        Long remaining = redisTemplate.execute(
                deductStockScript,
                List.of(stockKey),
                quantity
        );

        if (remaining == null || remaining < 0) {
            log.warn("[秒杀] 库存不足 productId={} userId={} remaining={}", productId, userId, remaining);
            return error("库存不足，抢购失败");
        }

        log.info("[秒杀] Redis 预扣减成功 productId={} userId={} remaining={}", productId, userId, remaining);

        // ---- Step 2: MySQL 创建订单（状态 UNPAID） ----
        Product product = productMapper.selectById(productId);
        if (product == null) {
            // 理论上不会走到这里；兜底回滚 Redis
            rollbackRedisStock(productId, quantity);
            return error("商品不存在");
        }

        String orderNo = IdUtil.getSnowflakeNextIdStr();
        Order order = Order.builder()
                .orderNo(orderNo)
                .productId(productId)
                .userId(userId)
                .quantity(quantity)
                .totalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)))
                .status(Order.STATUS_UNPAID)
                .build();

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 订单号冲突（极低概率），回滚 Redis
            rollbackRedisStock(productId, quantity);
            log.error("[秒杀] 订单号冲突 orderNo={}", orderNo, e);
            return error("下单失败，请重试");
        }

        // ---- Step 3: 发送 RocketMQ 延迟消息（超时取消） ----
        if (orderTimeoutProducer != null) {
            try {
                orderTimeoutProducer.sendTimeoutCheck(order.getId());
            } catch (Exception e) {
                // 消息发送失败不影响主流程，但需记录告警
                log.error("[秒杀] 延迟消息发送失败 orderId={}", order.getId(), e);
            }
        } else {
            log.warn("[秒杀] MQ 未配置，跳过延迟消息发送 orderId={}", order.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "抢购成功，请在10秒内完成支付");
        result.put("orderId", order.getId());
        result.put("orderNo", orderNo);
        result.put("remainingStock", remaining);
        return result;
    }

    // ===================================================================
    //  2. 支付 — MySQL 乐观锁最终扣减库存
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long orderId) {

        // ---- 加载订单 ----
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!Order.STATUS_UNPAID.equals(order.getStatus())) {
            throw new RuntimeException("订单状态不允许支付，当前状态: " + order.getStatus());
        }

        // ---- 加载商品（获取当前版本号） ----
        Product product = productMapper.selectById(order.getProductId());
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }

        // ---- MySQL 乐观锁扣减库存 ----
        int rows = productMapper.deductStock(
                product.getId(),
                order.getQuantity(),
                product.getVersion()
        );

        if (rows <= 0) {
            // 乐观锁更新失败 = 版本号已变或库存不足
            log.error("[支付] 乐观锁扣减失败 orderId={} productId={} version={} stock={}",
                    orderId, product.getId(), product.getVersion(), product.getStock());
            throw new RuntimeException("库存扣减失败，可能存在并发超卖");
        }

        // ---- 更新订单状态为 PAID ----
        int updated = orderMapper.updateStatus(orderId, Order.STATUS_PAID, Order.STATUS_UNPAID);
        if (updated <= 0) {
            // 订单状态已被改变（可能被超时取消先执行了），需要把库存加回去
            log.error("[支付] 订单状态更新失败 orderId={} 可能已超时取消", orderId);
            // 回滚 MySQL 库存
            rollbackMysqlStock(product.getId(), order.getQuantity());
            throw new RuntimeException("订单已超时取消，支付失败");
        }

        log.info("[支付] 支付成功 orderId={} productId={} quantity={}",
                orderId, order.getProductId(), order.getQuantity());
    }

    // ===================================================================
    //  3. 超时取消 — MQ 消费者触发，回滚 Redis 库存
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("[取消] 订单不存在 orderId={}", orderId);
            return;
        }

        // 幂等性：如果已经是 PAID 或 CANCELED，直接返回
        if (!Order.STATUS_UNPAID.equals(order.getStatus())) {
            log.info("[取消] 订单状态已变更，跳过 orderId={} status={}", orderId, order.getStatus());
            return;
        }

        // CAS 更新订单状态为 CANCELED
        int updated = orderMapper.updateStatus(orderId, Order.STATUS_CANCELED, Order.STATUS_UNPAID);
        if (updated <= 0) {
            // 并发支付已经抢先修改了状态
            log.info("[取消] 状态更新失败，支付可能已完成 orderId={}", orderId);
            return;
        }

        // 回滚 Redis 库存
        rollbackRedisStock(order.getProductId(), order.getQuantity());

        log.info("[取消] 订单已超时取消 orderId={} productId={} quantity={}",
                orderId, order.getProductId(), order.getQuantity());
    }

    // ===================================================================
    //  内部工具方法
    // ===================================================================

    /**
     * Redis 库存回滚（Lua 原子操作）
     */
    private void rollbackRedisStock(Long productId, Integer quantity) {
        String stockKey = REDIS_STOCK_KEY + productId;
        Long after = redisTemplate.execute(
                rollbackStockScript,
                List.of(stockKey),
                quantity
        );
        log.info("[回滚] Redis 库存已回滚 productId={} quantity={} after={}", productId, quantity, after);
    }

    /**
     * MySQL 库存回滚（支付后发现订单已取消时使用）
     */
    private void rollbackMysqlStock(Long productId, Integer quantity) {
        Product product = productMapper.selectById(productId);
        if (product != null) {
            product.setStock(product.getStock() + quantity);
            productMapper.updateById(product);
        }
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }
}

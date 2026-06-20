package com.example.seckill.service;

import java.util.Map;

/**
 * 秒杀核心服务接口
 */
public interface SeckillService {

    /**
     * 下单 — Redis 预扣减 + 创建订单 + 发送延迟消息
     *
     * @param productId 商品ID
     * @param userId    用户ID
     * @param quantity  购买数量
     * @return 订单信息（包含 orderId）
     */
    Map<String, Object> seckill(Long productId, String userId, Integer quantity);

    /**
     * 支付 — MySQL 乐观锁扣减库存 + 更新订单状态为 PAID
     *
     * @param orderId 订单ID
     */
    void pay(Long orderId);

    /**
     * 超时取消 — 由 MQ 消费者触发，回滚 Redis 库存
     *
     * @param orderId 订单ID
     */
    void cancelOrder(Long orderId);
}

package com.example.seckill.mq;

import com.example.seckill.config.MqConstants;
import com.example.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 订单超时取消 — 延迟消息消费者
 *
 * <p>监听到延迟消息后检查订单状态：
 * <ul>
 *   <li>订单 UNPAID → 取消订单，回滚 Redis 库存</li>
 *   <li>订单 PAID / CANCELED → 不做任何处理（幂等）</li>
 * </ul>
 *
 * <p>仅在 seckill.mq.enabled=true 时激活（需要本地 RocketMQ broker）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "seckill.mq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_ORDER_TIMEOUT,
        consumerGroup = MqConstants.CONSUMER_GROUP_ORDER_TIMEOUT,
        selectorExpression = "cancel"
)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final SeckillService seckillService;

    public OrderTimeoutConsumer(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @Override
    public void onMessage(String message) {
        try {
            Long orderId = Long.valueOf(message);
            log.info("[MQ] 收到超时取消消息 orderId={}", orderId);
            seckillService.cancelOrder(orderId);
        } catch (NumberFormatException e) {
            log.error("[MQ] 消息格式错误 message={}", message, e);
        } catch (Exception e) {
            log.error("[MQ] 超时取消处理异常 message={}", message, e);
            // 生产环境建议在此处增加重试或死信队列处理
            throw e;   // 抛出异常触发 RocketMQ 重试
        }
    }
}

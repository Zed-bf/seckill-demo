package com.example.seckill.mq;

import com.example.seckill.config.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 订单超时取消 — 延迟消息生产者
 * 仅在 seckill.mq.enabled=true 时激活（需要本地 RocketMQ broker）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "seckill.mq.enabled", havingValue = "true")
public class OrderTimeoutProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public OrderTimeoutProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 发送延迟取消检查消息
     *
     * @param orderId 订单ID
     */
    public void sendTimeoutCheck(Long orderId) {
        String payload = String.valueOf(orderId);

        Message<String> message = MessageBuilder
                .withPayload(payload)
                .build();

        // RocketMQ 发送延迟消息：
        // destination = "topic:tag"
        // delayLevel = 延迟等级（见 MqConstants）
        SendResult result = rocketMQTemplate.syncSend(
                MqConstants.TOPIC_ORDER_TIMEOUT + ":cancel",
                message,
                3000,                          // timeout
                MqConstants.DELAY_LEVEL         // delay level
        );

        if (result.getSendStatus() == SendStatus.SEND_OK) {
            log.info("[MQ] 延迟消息发送成功 orderId={} msgId={}", orderId, result.getMsgId());
        } else {
            log.error("[MQ] 延迟消息发送失败 orderId={} status={}", orderId, result.getSendStatus());
        }
    }
}

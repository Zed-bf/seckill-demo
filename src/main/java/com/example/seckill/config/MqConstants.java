package com.example.seckill.config;

/**
 * RocketMQ 常量定义
 */
public final class MqConstants {

    private MqConstants() {}

    /** 订单超时取消 — Topic */
    public static final String TOPIC_ORDER_TIMEOUT = "seckill-order-timeout";

    /** 订单超时取消 — Consumer Group */
    public static final String CONSUMER_GROUP_ORDER_TIMEOUT = "seckill-order-timeout-consumer";

    /**
     * RocketMQ 延迟消息等级：
     * 1=1s, 2=5s, 3=10s, 4=30s, 5=1m, ...
     * 这里用 level=3（10秒）作为演示；
     * 生产环境建议调大（如 level=14 = 10min）
     */
    public static final int DELAY_LEVEL = 3;
}

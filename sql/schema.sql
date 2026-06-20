-- =============================================================
-- 高并发抢购（秒杀）系统 — MySQL 建表脚本
-- =============================================================

CREATE DATABASE IF NOT EXISTS seckill_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE seckill_db;

-- -------------------------------------------------------
-- 商品表 (product)
-- stock：剩余库存
-- version：乐观锁版本号（每次扣减 +1）
-- -------------------------------------------------------
DROP TABLE IF EXISTS `t_product`;
CREATE TABLE `t_product` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键',
    `product_name`  VARCHAR(100)    NOT NULL                   COMMENT '商品名称',
    `total_stock`   INT             NOT NULL DEFAULT 0          COMMENT '总库存（仅作参考）',
    `stock`         INT             NOT NULL DEFAULT 0          COMMENT '剩余库存（MySQL 最终扣减目标）',
    `price`         DECIMAL(10,2)   NOT NULL DEFAULT 0.00       COMMENT '单价',
    `version`       INT             NOT NULL DEFAULT 0          COMMENT '乐观锁版本号',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- -------------------------------------------------------
-- 订单表 (order)
-- status 取值：UNPAID 待支付 / PAID 已支付 / CANCELED 已取消
-- -------------------------------------------------------
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键',
    `order_no`      VARCHAR(64)     NOT NULL                   COMMENT '订单编号（唯一）',
    `product_id`    BIGINT          NOT NULL                   COMMENT '商品ID',
    `user_id`       VARCHAR(64)     NOT NULL                   COMMENT '用户ID',
    `quantity`      INT             NOT NULL DEFAULT 1          COMMENT '购买数量',
    `total_amount`  DECIMAL(10,2)   NOT NULL DEFAULT 0.00       COMMENT '订单金额',
    `status`        VARCHAR(20)     NOT NULL DEFAULT 'UNPAID'  COMMENT '订单状态：UNPAID/PAID/CANCELED',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status_create_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- -------------------------------------------------------
-- 初始化测试数据：一个商品，库存 100
-- -------------------------------------------------------
INSERT INTO `t_product` (`id`, `product_name`, `total_stock`, `stock`, `price`)
VALUES (1, 'iPhone 16 Pro Max 秒杀', 100, 100, 6999.00);

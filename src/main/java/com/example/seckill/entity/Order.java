package com.example.seckill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单编号（唯一） */
    private String orderNo;

    /** 商品ID */
    private Long productId;

    /** 用户ID */
    private String userId;

    /** 购买数量 */
    private Integer quantity;

    /** 订单金额 */
    private BigDecimal totalAmount;

    /** 订单状态：UNPAID / PAID / CANCELED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ======== 状态常量 ========
    public static final String STATUS_UNPAID   = "UNPAID";
    public static final String STATUS_PAID     = "PAID";
    public static final String STATUS_CANCELED = "CANCELED";
}

package com.example.seckill.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 抢购下单请求
 */
@Data
public class SeckillRequest {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity = 1;
}

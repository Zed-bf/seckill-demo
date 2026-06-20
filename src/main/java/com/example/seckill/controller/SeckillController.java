package com.example.seckill.controller;

import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 秒杀系统 REST API
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * 下单接口
     *
     * <pre>
     * POST /api/seckill/place-order
     * Body: { "productId": 1, "userId": "user123", "quantity": 1 }
     * </pre>
     */
    @PostMapping("/place-order")
    public Map<String, Object> placeOrder(@Valid @RequestBody SeckillRequest request) {
        return seckillService.seckill(
                request.getProductId(),
                request.getUserId(),
                request.getQuantity()
        );
    }

    /**
     * 支付接口
     *
     * <pre>
     * POST /api/seckill/pay?orderId=1
     * </pre>
     */
    @PostMapping("/pay")
    public Map<String, Object> pay(@RequestParam Long orderId) {
        seckillService.pay(orderId);
        return Map.of("success", true, "message", "支付成功");
    }
}

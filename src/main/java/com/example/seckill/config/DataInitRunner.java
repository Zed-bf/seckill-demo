package com.example.seckill.config;

import com.example.seckill.entity.Product;
import com.example.seckill.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 应用启动时，将 MySQL 中的商品库存加载到 Redis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitRunner implements CommandLineRunner {

    private static final String REDIS_STOCK_KEY = "seckill:stock:";

    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(String... args) {
        List<Product> products = productMapper.selectList(null);
        for (Product product : products) {
            String key = REDIS_STOCK_KEY + product.getId();

            // 仅在 key 不存在时设置，避免重复启动覆盖
            Boolean absent = redisTemplate.opsForValue()
                    .setIfAbsent(key, product.getStock(), 24, TimeUnit.HOURS);

            if (Boolean.TRUE.equals(absent)) {
                log.info("[初始化] Redis 库存加载 productId={} stock={}", product.getId(), product.getStock());
            } else {
                log.info("[初始化] Redis 库存已存在 productId={} 跳过", product.getId());
            }
        }
    }
}

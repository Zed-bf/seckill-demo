package com.example.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 乐观锁扣减 MySQL 库存 — 必须在 SQL 层面做版本号校验
     *
     * @param productId 商品ID
     * @param quantity  扣减数量
     * @param version   当前读到的版本号
     * @return 影响行数（0 表示版本号已变，扣减失败）
     */
    @Update("UPDATE t_product SET stock = stock - #{quantity}, version = version + 1 " +
            "WHERE id = #{productId} AND version = #{version} AND stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId,
                    @Param("quantity") Integer quantity,
                    @Param("version") Integer version);
}

package com.example.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 订单 Mapper
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 更新订单状态 — 带前置状态校验（防止重复处理）
     *
     * @param orderId     订单ID
     * @param newStatus   新状态
     * @param expectStatus 期望的当前状态（CAS 条件）
     * @return 影响行数
     */
    @Update("UPDATE t_order SET status = #{newStatus} WHERE id = #{orderId} AND status = #{expectStatus}")
    int updateStatus(@Param("orderId") Long orderId,
                     @Param("newStatus") String newStatus,
                     @Param("expectStatus") String expectStatus);
}

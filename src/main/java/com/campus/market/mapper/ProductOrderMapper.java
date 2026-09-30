package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.ProductOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductOrderMapper extends BaseMapper<ProductOrder> {

    /**
     * 把订单「比较并交换」地改成已取消。
     * <p>
     * 刻意用显式 SQL 而不是 {@code LambdaUpdateWrapper}，原因有三：
     * <ol>
     *   <li>与 {@code ProductMapper.casUpdateStatus} 保持同一套写法，两处 CAS 一眼能对照</li>
     *   <li>CAS 的原子性意图写在一行 SQL 里更显式 —— {@code WHERE status = #{expect}}
     *       就是「只有此刻仍是这个状态才允许改」，不是先查后改</li>
     *   <li>不依赖 MyBatis-Plus 的实体映射初始化，能脱离 Spring 容器直接做单元测试</li>
     * </ol>
     * 用户主动取消和定时任务超时取消都走这里，区别只在 {@code reason}。
     *
     * @param expect 期望的当前状态；与库中实际值不一致时影响行数为 0
     * @return 影响行数，1 表示取消成功，0 表示状态已被别人改过
     */
    @Update("UPDATE product_order "
            + "SET status = 'CANCELLED', cancel_time = #{cancelTime}, cancel_reason = #{reason} "
            + "WHERE id = #{id} AND status = #{expect}")
    int casCancel(@Param("id") Long id,
                  @Param("expect") String expect,
                  @Param("cancelTime") LocalDateTime cancelTime,
                  @Param("reason") String reason);

    /**
     * 查出「在该状态停留过久」的订单，供超时任务批量处理。
     * <p>
     * 两类订单分别以不同的时间戳起算，这样语义才准确：
     * <ul>
     *   <li>{@code CREATED}（待卖家确认）—— 卖家迟迟不响应，从下单时间算起</li>
     *   <li>{@code CONFIRMED}（待面交）—— 卖家已确认但交易一直没完成，从确认时间算起</li>
     * </ul>
     * 只取前 {@code limit} 条：一次全捞出来会把内存和行锁都占满，
     * 剩下的等下一轮扫描继续处理。
     *
     * @param deadline 早于这个时间点的订单视为超时
     * @param limit    单批上限
     */
    @Select("SELECT * FROM product_order "
            + "WHERE (status = 'CREATED'   AND create_time  < #{deadline}) "
            + "   OR (status = 'CONFIRMED' AND confirm_time < #{deadline}) "
            + "ORDER BY id "
            + "LIMIT #{limit}")
    List<ProductOrder> selectTimeoutOrders(@Param("deadline") LocalDateTime deadline,
                                           @Param("limit") int limit);
}

package com.campus.market.service.impl;

import com.campus.market.entity.ProductOrder;
import com.campus.market.enums.OrderStatus;
import com.campus.market.enums.ProductStatus;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.ProductOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 单笔订单的超时取消。
 * <p>
 * <b>为什么单独抽成一个 Bean，而不是放在 OrderServiceImpl 里当私有方法？</b>
 * <p>
 * Spring 的 {@code @Transactional} 是基于动态代理实现的。同类内部的方法调用走的是
 * {@code this}，不经过代理对象，事务注解会被静默忽略 —— 这是很常见的踩坑点。
 * 抽成独立的 Bean 后，调用方拿到的是代理对象，事务才真正生效。
 * <p>
 * <b>为什么必须每笔订单独立事务？</b>
 * <p>
 * 定时任务是批量处理的。如果整批共用一个事务，那么第 50 笔订单出问题时，
 * 前面 49 笔已经成功取消的订单会被一起回滚 —— 下一轮扫描还得从头再来，
 * 白干活。每笔独立事务则互不影响，单笔失败只是这一笔留到下一轮重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCanceller {

    /** 超时取消时写入的取消原因，用于和用户主动取消区分开 */
    public static final String TIMEOUT_REASON = "超时未处理，系统自动取消";

    private final ProductMapper productMapper;
    private final ProductOrderMapper orderMapper;

    /**
     * 尝试取消一笔订单，并把商品释放回在售。
     *
     * @return true 表示本次调用真的取消了；false 表示订单状态已被别人改过，本次什么都没做
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOne(ProductOrder order) {
        OrderStatus from = OrderStatus.valueOf(order.getStatus());

        // CAS：只有订单「此刻仍是」扫描时看到的状态，才允许取消。
        // 定时任务和用户操作可能同时发生 —— 比如买家刚好在这一刻点了取消，
        // 或者卖家点了确认。带上 status 条件后，两边只有一方能更新成功，
        // 另一方影响行数为 0，自然放弃，不会出现「取消了一笔已确认订单」这种事。
        int updated = orderMapper.casCancel(order.getId(), from.name(),
                LocalDateTime.now(), TIMEOUT_REASON);
        if (updated == 0) {
            return false;
        }

        // 商品重新上架。同样用 CAS：只有当前确实是 LOCKED 才改回 ON_SALE，
        // 避免把已经 SOLD 的商品错误地改回在售。
        productMapper.casUpdateStatus(order.getProductId(),
                ProductStatus.LOCKED.name(), ProductStatus.ON_SALE.name());

        log.debug("订单 {} 因超时被自动取消，商品 {} 已释放回在售", order.getId(), order.getProductId());
        return true;
    }
}

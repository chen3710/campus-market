package com.campus.market.service.impl;

import com.campus.market.entity.ProductOrder;
import com.campus.market.enums.OrderStatus;
import com.campus.market.mapper.CreditLogMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.ProductOrderMapper;
import com.campus.market.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时扫描的批量编排测试。
 * <p>
 * 重点验证「单笔失败不拖累整批」—— 这是把事务粒度定在「每笔订单」而不是「整批」
 * 的意义所在，也是这个功能最容易被写错的地方。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTimeoutTest {

    @Mock private ProductMapper productMapper;
    @Mock private ProductOrderMapper orderMapper;
    @Mock private UserMapper userMapper;
    @Mock private CreditLogMapper creditLogMapper;
    @Mock private OrderTimeoutCanceller timeoutCanceller;

    @InjectMocks private OrderServiceImpl orderService;

    private ProductOrder order(Long id) {
        ProductOrder o = new ProductOrder();
        o.setId(id);
        o.setProductId(id * 10);
        o.setStatus(OrderStatus.CREATED.name());
        return o;
    }

    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 9, 28, 12, 0);
    private static final int BATCH = 100;

    @Test
    @DisplayName("没有超时订单时直接返回 0，不做任何取消动作")
    void returnsZeroWhenNothingExpired() {
        when(orderMapper.selectTimeoutOrders(DEADLINE, BATCH)).thenReturn(List.of());

        assertThat(orderService.cancelTimeoutOrders(DEADLINE, BATCH)).isZero();
        verify(timeoutCanceller, never()).cancelOne(any());
    }

    @Test
    @DisplayName("竞争落败的订单不计入取消数")
    void onlyCountsActuallyCancelled() {
        when(orderMapper.selectTimeoutOrders(DEADLINE, BATCH))
                .thenReturn(List.of(order(1L), order(2L), order(3L)));
        // 第 2 笔被用户抢先处理了，CAS 失败
        when(timeoutCanceller.cancelOne(any()))
                .thenReturn(true, false, true);

        assertThat(orderService.cancelTimeoutOrders(DEADLINE, BATCH)).isEqualTo(2);
        verify(timeoutCanceller, times(3)).cancelOne(any());
    }

    @Test
    @DisplayName("单笔抛异常不会中断整批，其余订单照常处理")
    void oneFailureDoesNotAbortBatch() {
        when(orderMapper.selectTimeoutOrders(DEADLINE, BATCH))
                .thenReturn(List.of(order(1L), order(2L), order(3L)));
        // 第 2 笔处理时抛异常
        when(timeoutCanceller.cancelOne(any()))
                .thenReturn(true)
                .thenThrow(new RuntimeException("模拟单笔失败"))
                .thenReturn(true);

        int cancelled = orderService.cancelTimeoutOrders(DEADLINE, BATCH);

        // 第 1、3 笔必须成功 —— 如果整批共用一个事务，第 2 笔失败会把它们一起回滚
        assertThat(cancelled).isEqualTo(2);
        verify(timeoutCanceller, times(3)).cancelOne(any());
    }

    @Test
    @DisplayName("批量大小透传给查询，避免一次捞太多")
    void passesBatchSizeToQuery() {
        when(orderMapper.selectTimeoutOrders(any(), anyInt())).thenReturn(List.of());

        orderService.cancelTimeoutOrders(DEADLINE, 25);

        verify(orderMapper).selectTimeoutOrders(DEADLINE, 25);
    }
}

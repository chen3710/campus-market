package com.campus.market.service.impl;

import com.campus.market.entity.ProductOrder;
import com.campus.market.enums.OrderStatus;
import com.campus.market.enums.ProductStatus;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.ProductOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时取消的核心行为测试。
 * <p>
 * 这里最值得测的是「竞争」那一条：定时任务和用户操作同时发生时，
 * CAS 必须保证只有一方生效 —— 这是整个功能最容易出错的地方。
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutCancellerTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductOrderMapper orderMapper;

    @InjectMocks
    private OrderTimeoutCanceller canceller;

    private ProductOrder order(Long id, Long productId, OrderStatus status) {
        ProductOrder o = new ProductOrder();
        o.setId(id);
        o.setProductId(productId);
        o.setStatus(status.name());
        return o;
    }

    @Test
    @DisplayName("取消成功：返回 true，且商品被释放回 ON_SALE")
    void cancelSucceedsAndReleasesProduct() {
        ProductOrder o = order(1L, 100L, OrderStatus.CREATED);
        when(orderMapper.casCancel(any(), anyString(), any(), anyString())).thenReturn(1);
        when(productMapper.casUpdateStatus(100L,
                ProductStatus.LOCKED.name(), ProductStatus.ON_SALE.name())).thenReturn(1);

        boolean result = canceller.cancelOne(o);

        assertThat(result).isTrue();
        // CAS 的期望状态必须是扫描时读到的状态，否则条件就不成立了
        verify(orderMapper).casCancel(eq(1L), eq(OrderStatus.CREATED.name()), any(), anyString());
        // 关键：商品必须从 LOCKED 回到 ON_SALE，否则商品就永远卖不出去了
        verify(productMapper).casUpdateStatus(100L,
                ProductStatus.LOCKED.name(), ProductStatus.ON_SALE.name());
    }

    @Test
    @DisplayName("竞争落败：订单状态已被用户改掉时返回 false，且绝不碰商品")
    void skipsWhenOrderAlreadyChanged() {
        ProductOrder o = order(2L, 200L, OrderStatus.CREATED);
        // 影响行数 0 —— 说明这一瞬间订单状态已经变了（比如卖家刚点了确认）
        when(orderMapper.casCancel(any(), anyString(), any(), anyString())).thenReturn(0);

        boolean result = canceller.cancelOne(o);

        assertThat(result).isFalse();
        // 这是最重要的一条断言：订单没取消成功，就绝不能去动商品状态。
        // 否则会把一笔「已确认」订单对应的在售商品错误地释放出去。
        verify(productMapper, never()).casUpdateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("CONFIRMED 状态同样可以被超时取消")
    void confirmedOrdersCanAlsoTimeOut() {
        ProductOrder o = order(3L, 300L, OrderStatus.CONFIRMED);
        when(orderMapper.casCancel(any(), anyString(), any(), anyString())).thenReturn(1);
        when(productMapper.casUpdateStatus(eq(300L), any(), any())).thenReturn(1);

        assertThat(canceller.cancelOne(o)).isTrue();
        verify(orderMapper).casCancel(eq(3L), eq(OrderStatus.CONFIRMED.name()), any(), anyString());
        verify(productMapper).casUpdateStatus(300L,
                ProductStatus.LOCKED.name(), ProductStatus.ON_SALE.name());
    }

    @Test
    @DisplayName("超时取消的原因可识别，便于和用户主动取消区分")
    void timeoutReasonIsDistinguishable() {
        assertThat(OrderTimeoutCanceller.TIMEOUT_REASON).isNotBlank();
        assertThat(OrderTimeoutCanceller.TIMEOUT_REASON).contains("超时");
        // 原因文案里不应出现「买家」「卖家」—— 超时是系统行为，不归咎于某一方
        assertThat(OrderTimeoutCanceller.TIMEOUT_REASON).doesNotContain("买家", "卖家");
    }
}

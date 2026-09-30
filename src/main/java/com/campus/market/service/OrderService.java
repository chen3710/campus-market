package com.campus.market.service;

import com.campus.market.common.PageResult;
import com.campus.market.dto.OrderCreateRequest;
import com.campus.market.vo.OrderVO;

import java.time.LocalDateTime;

public interface OrderService {

    OrderVO create(Long buyerId, OrderCreateRequest request);

    void confirm(Long userId, Long orderId);

    void finish(Long userId, Long orderId);

    void cancel(Long userId, Long orderId, String reason);

    OrderVO detail(Long userId, Long orderId);

    PageResult<OrderVO> pageBought(Long userId, long page, long size);

    PageResult<OrderVO> pageSold(Long userId, long page, long size);

    /**
     * 取消一批超时未处理的订单，并把对应商品释放回在售。
     * <p>
     * 由定时任务调用。刻意不放在一个大事务里：每笔订单各自独立提交，
     * 单笔失败不影响同批其他订单（详见 {@code OrderTimeoutCanceller}）。
     *
     * @param deadline  早于这个时间点仍未推进的订单视为超时
     * @param batchSize 单批最多处理多少笔
     * @return 实际取消的订单数
     */
    int cancelTimeoutOrders(LocalDateTime deadline, int batchSize);
}

package com.campus.market.task;

import com.campus.market.config.AppProperties;
import com.campus.market.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单超时自动取消。
 * <p>
 * 解决的问题：买家下单后商品会被锁定为 LOCKED。如果卖家一直不确认（或确认了但双方
 * 一直没完成交易），这笔订单会永远挂着，商品也永远回不到在售 —— 相当于这件商品被
 * 一个「僵尸订单」占死了。这个任务定期把这类订单清理掉。
 * <p>
 * <b>为什么用 fixedDelay 而不是 fixedRate？</b>
 * <p>
 * {@code fixedRate} 按固定频率触发，不管上一轮有没有跑完 —— 数据量上来后会出现任务
 * 重叠执行，同一批订单被两个线程同时处理。{@code fixedDelay} 是「上一轮结束后再等
 * N 毫秒」，天然不会重叠。
 * <p>
 * <b>已知局限（单机可用，多实例部署会重复执行）</b>：
 * <p>
 * 多个实例同时跑这个任务时，每个实例都会扫到同一批订单。因为取消动作本身是 CAS 的，
 * 不会造成数据错乱（只有一方能成功），但会产生无谓的重复查询。要彻底解决得加分布式锁
 * （Redis SETNX / 数据库行锁 / ShedLock）。这个项目是单机部署，暂不需要。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.order.enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutScheduler {

    private final OrderService orderService;
    private final AppProperties properties;

    @Scheduled(fixedDelayString = "${app.order.scan-interval-ms:60000}",
            initialDelayString = "${app.order.initial-delay-ms:30000}")
    public void cancelTimeoutOrders() {
        AppProperties.Order config = properties.order();
        if (config == null || config.timeoutMinutes() < 0) {
            return;
        }

        LocalDateTime deadline = LocalDateTime.now().minusMinutes(config.timeoutMinutes());
        try {
            int cancelled = orderService.cancelTimeoutOrders(deadline, config.batchSize());
            if (cancelled > 0) {
                log.info("超时订单清理完成，本次取消 {} 笔（判定线：{} 之前未推进）", cancelled, deadline);
            }
        } catch (Exception e) {
            // 定时任务里抛异常会导致后续调度被取消（Spring 的行为），所以必须自己兜住
            log.error("超时订单清理任务执行异常，将在下一轮重试", e);
        }
    }
}

package com.campus.market.enums;

import com.campus.market.common.BusinessException;
import lombok.Getter;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机。
 * <p>
 * 把「哪些状态可以流转到哪些状态」收敛成一张表，而不是散落在各个 if-else 里。
 * 好处：新增状态时只需改这里；任何非法流转（比如已取消的订单又被确认）都会在这里被拦下。
 * <pre>
 *   CREATED ──确认──> CONFIRMED ──完成──> FINISHED（终态）
 *      │                   │
 *      └────取消───────────┴──取消──> CANCELLED（终态）
 * </pre>
 */
@Getter
public enum OrderStatus {

    CREATED("待卖家确认"),
    CONFIRMED("待面交"),
    FINISHED("已完成"),
    CANCELLED("已取消");

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(CREATED, EnumSet.of(CONFIRMED, CANCELLED));
        TRANSITIONS.put(CONFIRMED, EnumSet.of(FINISHED, CANCELLED));
        TRANSITIONS.put(FINISHED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final String desc;

    OrderStatus(String desc) {
        this.desc = desc;
    }

    public boolean canTransferTo(OrderStatus target) {
        return TRANSITIONS.getOrDefault(this, EnumSet.noneOf(OrderStatus.class)).contains(target);
    }

    /**
     * 校验流转合法性，非法直接抛业务异常。
     */
    public void assertTransferTo(OrderStatus target) {
        if (!canTransferTo(target)) {
            throw new BusinessException(
                    "订单状态不允许从「" + this.desc + "」变更为「" + target.desc + "」");
        }
    }

    public boolean isFinal() {
        return TRANSITIONS.getOrDefault(this, EnumSet.noneOf(OrderStatus.class)).isEmpty();
    }

    /**
     * 是否为占用商品的状态（占用期间商品不能再次被下单）。
     */
    public boolean isActive() {
        return this == CREATED || this == CONFIRMED;
    }
}

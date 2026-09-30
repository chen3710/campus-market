package com.campus.market.enums;

import lombok.Getter;

/**
 * 商品状态。状态流转由业务代码显式控制，不做自由赋值。
 * <pre>
 * ON_SALE --下单锁定--> LOCKED --交易完成--> SOLD
 *                            \--订单取消--> ON_SALE
 * ON_SALE --卖家下架--> OFF_SHELF --卖家上架--> ON_SALE
 * </pre>
 */
@Getter
public enum ProductStatus {

    ON_SALE("在售"),
    LOCKED("已锁定"),
    SOLD("已售出"),
    OFF_SHELF("已下架");

    private final String desc;

    ProductStatus(String desc) {
        this.desc = desc;
    }

    public boolean isTradable() {
        return this == ON_SALE;
    }
}

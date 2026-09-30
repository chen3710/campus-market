package com.campus.market.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表查询条件。用对象承载而不是一堆方法参数，方便后续扩展筛选项。
 */
@Data
public class ProductQuery {

    private String keyword;

    private Long categoryId;

    private Long sellerId;

    private String status;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    /** createTimeDesc / priceAsc / priceDesc / hot */
    private String sort;

    private long page = 1;

    private long size = 12;
}

package com.campus.market.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品视图对象。相比实体额外带上了分类名、卖家信息、图片列表和当前用户的收藏状态。
 */
@Data
public class ProductVO {

    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String cover;
    private String conditionLevel;
    private String status;
    private Integer viewCount;
    private Integer favoriteCount;
    private LocalDateTime createTime;

    private Long categoryId;
    private String categoryName;

    private Long sellerId;
    private String sellerNickname;
    private String sellerAvatar;
    private String sellerCampus;
    private Integer sellerCreditScore;

    private List<String> images;

    /** 当前登录用户是否已收藏，未登录时为 false */
    private Boolean favorited = false;

    /** 是否属于当前登录用户发布的商品 */
    private Boolean mine = false;
}

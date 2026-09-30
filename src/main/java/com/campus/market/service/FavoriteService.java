package com.campus.market.service;

import com.campus.market.common.PageResult;
import com.campus.market.vo.ProductVO;

public interface FavoriteService {

    /**
     * 收藏 / 取消收藏，返回操作后的收藏状态。
     */
    boolean toggle(Long userId, Long productId);

    PageResult<ProductVO> pageMine(Long userId, long page, long size);
}

package com.campus.market.service;

import com.campus.market.common.PageResult;
import com.campus.market.dto.ProductQuery;
import com.campus.market.dto.ProductSaveRequest;
import com.campus.market.vo.ProductVO;

public interface ProductService {

    PageResult<ProductVO> page(ProductQuery query, Long viewerId);

    ProductVO detail(Long productId, Long viewerId);

    Long create(Long sellerId, ProductSaveRequest request);

    void update(Long operatorId, boolean admin, Long productId, ProductSaveRequest request);

    void remove(Long operatorId, boolean admin, Long productId);

    void changeShelf(Long operatorId, Long productId, boolean onShelf);
}

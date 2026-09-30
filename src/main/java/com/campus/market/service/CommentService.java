package com.campus.market.service;

import com.campus.market.common.PageResult;
import com.campus.market.dto.CommentCreateRequest;
import com.campus.market.vo.CommentVO;
import com.campus.market.vo.ProductVO;

import java.util.List;

public interface CommentService {

    List<CommentVO> listByProduct(Long productId);

    CommentVO create(Long userId, Long productId, CommentCreateRequest request);
}

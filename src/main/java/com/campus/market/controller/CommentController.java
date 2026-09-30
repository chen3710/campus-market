package com.campus.market.controller;

import com.campus.market.auth.LoginRequired;
import com.campus.market.auth.UserContext;
import com.campus.market.common.Result;
import com.campus.market.dto.CommentCreateRequest;
import com.campus.market.service.CommentService;
import com.campus.market.vo.CommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public Result<List<CommentVO>> list(@PathVariable Long productId) {
        return Result.ok(commentService.listByProduct(productId));
    }

    @PostMapping
    @LoginRequired
    public Result<CommentVO> create(@PathVariable Long productId,
                                    @Valid @RequestBody CommentCreateRequest request) {
        return Result.ok(commentService.create(UserContext.requireUserId(), productId, request), "留言成功");
    }
}

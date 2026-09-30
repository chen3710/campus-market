package com.campus.market.controller;

import com.campus.market.auth.LoginRequired;
import com.campus.market.auth.UserContext;
import com.campus.market.common.PageResult;
import com.campus.market.common.Result;
import com.campus.market.service.FavoriteService;
import com.campus.market.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
@LoginRequired
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 收藏 / 取消收藏（幂等切换），返回操作后的状态。
     */
    @PostMapping("/{productId}")
    public Result<Boolean> toggle(@PathVariable Long productId) {
        boolean favorited = favoriteService.toggle(UserContext.requireUserId(), productId);
        return Result.ok(favorited, favorited ? "已收藏" : "已取消收藏");
    }

    @GetMapping("/mine")
    public Result<PageResult<ProductVO>> mine(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "12") long size) {
        return Result.ok(favoriteService.pageMine(UserContext.requireUserId(), page, size));
    }
}

package com.campus.market.controller;

import com.campus.market.auth.LoginRequired;
import com.campus.market.auth.LoginUser;
import com.campus.market.auth.UserContext;
import com.campus.market.common.PageResult;
import com.campus.market.common.Result;
import com.campus.market.dto.ProductQuery;
import com.campus.market.dto.ProductSaveRequest;
import com.campus.market.service.ProductService;
import com.campus.market.vo.ProductVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 商品列表。公开接口，未登录也能浏览。
     */
    @GetMapping
    public Result<PageResult<ProductVO>> page(ProductQuery query) {
        return Result.ok(productService.page(query, currentUserIdOrNull()));
    }

    /**
     * 我发布的商品。注意这个方法要写在 /{id} 之前阅读上更清晰，
     * Spring 的路径匹配会优先选择字面量路径 /mine 而不是变量 /{id}。
     */
    @GetMapping("/mine")
    @LoginRequired
    public Result<PageResult<ProductVO>> mine(ProductQuery query) {
        Long userId = UserContext.requireUserId();
        query.setSellerId(userId);
        return Result.ok(productService.page(query, userId));
    }

    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id, currentUserIdOrNull()));
    }

    @PostMapping
    @LoginRequired
    public Result<Long> create(@Valid @RequestBody ProductSaveRequest request) {
        return Result.ok(productService.create(UserContext.requireUserId(), request), "发布成功");
    }

    @PutMapping("/{id}")
    @LoginRequired
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSaveRequest request) {
        LoginUser me = UserContext.require();
        productService.update(me.id(), me.isAdmin(), id, request);
        return Result.ok(null, "保存成功");
    }

    @DeleteMapping("/{id}")
    @LoginRequired
    public Result<Void> remove(@PathVariable Long id) {
        LoginUser me = UserContext.require();
        productService.remove(me.id(), me.isAdmin(), id);
        return Result.ok(null, "已删除");
    }

    @PostMapping("/{id}/off-shelf")
    @LoginRequired
    public Result<Void> offShelf(@PathVariable Long id) {
        productService.changeShelf(UserContext.requireUserId(), id, false);
        return Result.ok(null, "已下架");
    }

    @PostMapping("/{id}/on-shelf")
    @LoginRequired
    public Result<Void> onShelf(@PathVariable Long id) {
        productService.changeShelf(UserContext.requireUserId(), id, true);
        return Result.ok(null, "已重新上架");
    }

    private Long currentUserIdOrNull() {
        LoginUser user = UserContext.get();
        return user == null ? null : user.id();
    }
}

package com.campus.market.controller;

import com.campus.market.auth.LoginRequired;
import com.campus.market.auth.UserContext;
import com.campus.market.common.PageResult;
import com.campus.market.common.Result;
import com.campus.market.dto.CancelOrderRequest;
import com.campus.market.dto.OrderCreateRequest;
import com.campus.market.service.OrderService;
import com.campus.market.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@LoginRequired
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateRequest request) {
        return Result.ok(orderService.create(UserContext.requireUserId(), request), "下单成功");
    }

    @GetMapping("/bought")
    public Result<PageResult<OrderVO>> bought(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.pageBought(UserContext.requireUserId(), page, size));
    }

    @GetMapping("/sold")
    public Result<PageResult<OrderVO>> sold(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.pageSold(UserContext.requireUserId(), page, size));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        orderService.confirm(UserContext.requireUserId(), id);
        return Result.ok(null, "已确认订单，请与买家约定面交");
    }

    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        orderService.finish(UserContext.requireUserId(), id);
        return Result.ok(null, "交易完成，信用分 +2");
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestBody(required = false) CancelOrderRequest request) {
        orderService.cancel(UserContext.requireUserId(), id, request == null ? null : request.reason());
        return Result.ok(null, "订单已取消");
    }
}

package com.campus.market.controller;

import com.campus.market.auth.LoginRequired;
import com.campus.market.auth.UserContext;
import com.campus.market.common.Result;
import com.campus.market.dto.ProfileUpdateRequest;
import com.campus.market.entity.CreditLog;
import com.campus.market.service.UserService;
import com.campus.market.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me/credit-logs")
    @LoginRequired
    public Result<List<CreditLog>> creditLogs() {
        return Result.ok(userService.creditLogs(UserContext.requireUserId()));
    }

    @PutMapping("/me")
    @LoginRequired
    public Result<UserVO> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return Result.ok(userService.updateProfile(UserContext.requireUserId(), request), "资料已更新");
    }

    /**
     * 查看他人主页。公开接口，但不返回手机号等隐私字段。
     */
    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        UserVO vo = userService.getById(id);
        vo.setPhone(null);
        return Result.ok(vo);
    }
}

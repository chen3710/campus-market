package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.auth.JwtUtil;
import com.campus.market.auth.LoginUser;
import com.campus.market.auth.UserContext;
import com.campus.market.common.BusinessException;
import com.campus.market.common.ResultCode;
import com.campus.market.dto.LoginRequest;
import com.campus.market.dto.RegisterRequest;
import com.campus.market.entity.User;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.AuthService;
import com.campus.market.vo.LoginResponse;
import com.campus.market.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int INITIAL_CREDIT_SCORE = 100;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        // 先查一次给出友好提示；真正的兜底是数据库 username 唯一索引，
        // 高并发下两个请求同时通过这层检查时，后写入的那个会触发 DuplicateKeyException，
        // 被全局异常处理器转成「数据已存在，请勿重复提交」。
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该用户名已被注册");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setCampus(request.campus());
        user.setPhone(request.phone());
        user.setCreditScore(INITIAL_CREDIT_SCORE);
        user.setRole(LoginUser.ROLE_USER);
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);

        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));

        // 用户不存在与密码错误返回完全相同的提示，避免攻击者通过错误信息枚举出系统里存在哪些账号。
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被封禁，请联系管理员");
        }
        return buildLoginResponse(user);
    }

    @Override
    public UserVO currentUser() {
        User user = userMapper.selectById(UserContext.requireUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在或已被删除");
        }
        return UserVO.from(user);
    }

    private LoginResponse buildLoginResponse(User user) {
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRole());
        String token = jwtUtil.generate(loginUser);
        return new LoginResponse(token, jwtUtil.getExpireMillis() / 1000, UserVO.from(user));
    }
}

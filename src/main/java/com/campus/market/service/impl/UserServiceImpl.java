package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.auth.LoginUser;
import com.campus.market.common.BusinessException;
import com.campus.market.common.PageResult;
import com.campus.market.common.ResultCode;
import com.campus.market.dto.ProfileUpdateRequest;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.User;
import com.campus.market.mapper.CreditLogMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.UserService;
import com.campus.market.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final CreditLogMapper creditLogMapper;

    @Override
    public UserVO getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return UserVO.from(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 只更新前端传了的字段，没传的保持原值，避免把昵称清空。
        if (StringUtils.hasText(request.nickname())) {
            user.setNickname(request.nickname());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.campus() != null) {
            user.setCampus(request.campus());
        }
        userMapper.updateById(user);
        return UserVO.from(user);
    }

    @Override
    public List<CreditLog> creditLogs(Long userId) {
        return creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, userId)
                .orderByDesc(CreditLog::getCreateTime)
                .last("LIMIT 50"));
    }

    @Override
    public PageResult<UserVO> pageUsers(long page, long size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getNickname, keyword));
        }
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, UserVO::from);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (LoginUser.ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException("管理员账号不允许被封禁");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }
}

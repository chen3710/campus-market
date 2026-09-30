package com.campus.market.service;

import com.campus.market.common.PageResult;
import com.campus.market.dto.ProfileUpdateRequest;
import com.campus.market.entity.CreditLog;
import com.campus.market.vo.UserVO;

import java.util.List;

public interface UserService {

    UserVO getById(Long userId);

    UserVO updateProfile(Long userId, ProfileUpdateRequest request);

    List<CreditLog> creditLogs(Long userId);

    PageResult<UserVO> pageUsers(long page, long size, String keyword);

    void updateStatus(Long userId, Integer status);
}

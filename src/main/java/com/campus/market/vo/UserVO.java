package com.campus.market.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户公开视图，绝不包含密码字段。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private String campus;
    private Integer creditScore;
    private String role;
    private Integer status;
    private LocalDateTime createTime;

    public static UserVO from(com.campus.market.entity.User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setCampus(user.getCampus());
        vo.setCreditScore(user.getCreditScore());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}

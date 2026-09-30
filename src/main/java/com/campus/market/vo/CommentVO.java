package com.campus.market.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {

    private Long id;
    private Long productId;
    private Long userId;
    private String userNickname;
    private String userAvatar;
    private String content;
    private Long parentId;
    private LocalDateTime createTime;
}

package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.common.BusinessException;
import com.campus.market.common.ResultCode;
import com.campus.market.dto.CommentCreateRequest;
import com.campus.market.entity.Comment;
import com.campus.market.entity.Product;
import com.campus.market.entity.User;
import com.campus.market.mapper.CommentMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.CommentService;
import com.campus.market.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;

    @Override
    public List<CommentVO> listByProduct(Long productId) {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getProductId, productId)
                .orderByDesc(Comment::getCreateTime));
        if (comments.isEmpty()) {
            return List.of();
        }

        // 一次批量查用户，避免逐条留言去查昵称
        Set<Long> userIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return comments.stream().map(comment -> {
            CommentVO vo = new CommentVO();
            vo.setId(comment.getId());
            vo.setProductId(comment.getProductId());
            vo.setUserId(comment.getUserId());
            vo.setContent(comment.getContent());
            vo.setParentId(comment.getParentId());
            vo.setCreateTime(comment.getCreateTime());
            User user = userMap.get(comment.getUserId());
            vo.setUserNickname(user == null ? "已注销用户" : user.getNickname());
            vo.setUserAvatar(user == null ? null : user.getAvatar());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO create(Long userId, Long productId, CommentCreateRequest request) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已被删除");
        }

        Comment comment = new Comment();
        comment.setProductId(productId);
        comment.setUserId(userId);
        comment.setContent(request.content());
        comment.setParentId(request.parentId() == null ? 0L : request.parentId());
        commentMapper.insert(comment);

        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setProductId(productId);
        vo.setUserId(userId);
        vo.setContent(comment.getContent());
        vo.setParentId(comment.getParentId());
        vo.setCreateTime(comment.getCreateTime());

        User user = userMapper.selectById(userId);
        if (user != null) {
            vo.setUserNickname(user.getNickname());
            vo.setUserAvatar(user.getAvatar());
        }
        return vo;
    }
}

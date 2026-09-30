package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.BusinessException;
import com.campus.market.common.PageResult;
import com.campus.market.common.ResultCode;
import com.campus.market.entity.Favorite;
import com.campus.market.entity.Product;
import com.campus.market.mapper.FavoriteMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.service.FavoriteService;
import com.campus.market.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggle(Long userId, Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已被删除");
        }

        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));

        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            // 计数用单条 SQL 增减，保证并发下不会算错
            productMapper.addFavoriteCount(productId, -1);
            return false;
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        // uk_user_product 唯一索引是并发重复收藏的最终防线：
        // 两个请求同时通过上面的存在性检查时，后插入的那个会触发 DuplicateKeyException。
        favoriteMapper.insert(favorite);
        productMapper.addFavoriteCount(productId, 1);
        return true;
    }

    @Override
    public PageResult<ProductVO> pageMine(Long userId, long page, long size) {
        Page<Favorite> favoritePage = favoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreateTime));

        PageResult<ProductVO> result = new PageResult<>();
        result.setTotal(favoritePage.getTotal());
        result.setPage(favoritePage.getCurrent());
        result.setSize(favoritePage.getSize());
        result.setPages(favoritePage.getPages());

        List<Long> productIds = favoritePage.getRecords().stream()
                .map(Favorite::getProductId).collect(Collectors.toList());
        if (productIds.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }

        Map<Long, ProductVO> voMap = productMapper.selectProductByIds(productIds).stream()
                .collect(Collectors.toMap(ProductVO::getId, vo -> vo, (a, b) -> a, LinkedHashMap::new));

        // 按收藏时间倒序还原顺序，并标记收藏态
        List<ProductVO> records = productIds.stream()
                .map(voMap::get)
                .filter(java.util.Objects::nonNull)
                .peek(vo -> {
                    vo.setFavorited(true);
                    vo.setMine(userId.equals(vo.getSellerId()));
                })
                .collect(Collectors.toList());

        result.setRecords(records);
        return result;
    }
}

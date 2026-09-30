package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.BusinessException;
import com.campus.market.common.PageResult;
import com.campus.market.common.ResultCode;
import com.campus.market.dto.ProductQuery;
import com.campus.market.dto.ProductSaveRequest;
import com.campus.market.entity.Favorite;
import com.campus.market.entity.Product;
import com.campus.market.entity.ProductImage;
import com.campus.market.enums.ProductStatus;
import com.campus.market.mapper.FavoriteMapper;
import com.campus.market.mapper.ProductImageMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.service.ProductService;
import com.campus.market.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final FavoriteMapper favoriteMapper;

    @Override
    public PageResult<ProductVO> page(ProductQuery query, Long viewerId) {
        Page<ProductVO> page = new Page<>(query.getPage(), query.getSize());
        IPage<ProductVO> result = productMapper.selectProductPage(page, query);
        decorate(result.getRecords(), viewerId);
        return PageResult.of(result);
    }

    @Override
    public ProductVO detail(Long productId, Long viewerId) {
        ProductVO vo = productMapper.selectProductDetail(productId);
        if (vo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已被删除");
        }
        // 浏览量自增走单条 SQL，而不是「读出来 +1 再写回」，避免并发下丢更新。
        productMapper.increaseViewCount(productId);
        vo.setViewCount(vo.getViewCount() == null ? 1 : vo.getViewCount() + 1);

        vo.setImages(productImageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                        .eq(ProductImage::getProductId, productId)
                        .orderByAsc(ProductImage::getSortNo))
                .stream().map(ProductImage::getUrl).collect(Collectors.toList()));

        if (viewerId != null) {
            vo.setFavorited(favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getUserId, viewerId)
                    .eq(Favorite::getProductId, productId)) > 0);
            vo.setMine(viewerId.equals(vo.getSellerId()));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long sellerId, ProductSaveRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        product.setSellerId(sellerId);
        product.setStatus(ProductStatus.ON_SALE.name());
        product.setViewCount(0);
        product.setFavoriteCount(0);
        product.setVersion(0);
        product.setDeleted(0);
        productMapper.insert(product);

        replaceImages(product.getId(), request);
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long operatorId, boolean admin, Long productId, ProductSaveRequest request) {
        Product product = requireProduct(productId);
        if (!admin && !product.getSellerId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能编辑自己发布的商品");
        }
        if (ProductStatus.LOCKED.name().equals(product.getStatus())
                || ProductStatus.SOLD.name().equals(product.getStatus())) {
            throw new BusinessException("商品已被下单或已售出，无法再编辑");
        }

        applyRequest(product, request);
        // updateById 时 MyBatis-Plus 会带上 version 条件：如果期间有别人改过，影响行数为 0。
        if (productMapper.updateById(product) == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "商品信息已被他人修改，请刷新后重试");
        }
        replaceImages(productId, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long operatorId, boolean admin, Long productId) {
        Product product = requireProduct(productId);
        if (!admin && !product.getSellerId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能删除自己发布的商品");
        }
        if (ProductStatus.LOCKED.name().equals(product.getStatus())) {
            throw new BusinessException("该商品有进行中的订单，请先处理订单");
        }
        // @TableLogic 会把 delete 语句改写成 UPDATE product SET deleted = 1
        productMapper.deleteById(productId);
        productImageMapper.delete(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, productId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeShelf(Long operatorId, Long productId, boolean onShelf) {
        Product product = requireProduct(productId);
        if (!product.getSellerId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己发布的商品");
        }
        String expect = onShelf ? ProductStatus.OFF_SHELF.name() : ProductStatus.ON_SALE.name();
        String target = onShelf ? ProductStatus.ON_SALE.name() : ProductStatus.OFF_SHELF.name();
        if (!expect.equals(product.getStatus())) {
            throw new BusinessException("商品当前状态不支持该操作");
        }
        if (productMapper.casUpdateStatus(productId, expect, target) == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "操作失败，请刷新后重试");
        }
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    /**
     * 批量补齐收藏状态与归属标记。
     * <p>
     * 关键点：用一次 IN 查询把所有收藏关系捞回来，而不是循环里逐个查 —— 后者就是典型的 N+1。
     */
    private void decorate(List<ProductVO> records, Long viewerId) {
        if (records == null || records.isEmpty()) {
            return;
        }
        records.forEach(vo -> {
            vo.setFavorited(false);
            vo.setMine(false);
        });
        if (viewerId == null) {
            return;
        }
        List<Long> productIds = records.stream().map(ProductVO::getId).collect(Collectors.toList());
        Set<Long> favoritedIds = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .select(Favorite::getProductId)
                        .eq(Favorite::getUserId, viewerId)
                        .in(Favorite::getProductId, productIds))
                .stream().map(Favorite::getProductId).collect(Collectors.toSet());

        for (ProductVO vo : records) {
            vo.setFavorited(favoritedIds.contains(vo.getId()));
            vo.setMine(viewerId.equals(vo.getSellerId()));
        }
    }

    private Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已被删除");
        }
        return product;
    }

    private void applyRequest(Product product, ProductSaveRequest request) {
        product.setTitle(request.title());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setOriginalPrice(request.originalPrice());
        product.setCategoryId(request.categoryId());
        product.setConditionLevel(StringUtils.hasText(request.conditionLevel())
                ? request.conditionLevel() : "九成新");
        product.setCover(resolveCover(request));
    }

    private String resolveCover(ProductSaveRequest request) {
        if (StringUtils.hasText(request.cover())) {
            return request.cover();
        }
        List<String> images = request.images();
        return (images != null && !images.isEmpty()) ? images.get(0) : null;
    }

    private void replaceImages(Long productId, ProductSaveRequest request) {
        productImageMapper.delete(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, productId));

        List<String> urls = new ArrayList<>();
        if (request.images() != null) {
            urls.addAll(request.images().stream().filter(StringUtils::hasText).toList());
        }
        if (urls.isEmpty() && StringUtils.hasText(request.cover())) {
            urls.add(request.cover());
        }

        int sort = 0;
        for (String url : urls) {
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setUrl(url);
            image.setSortNo(sort++);
            productImageMapper.insert(image);
        }
    }
}

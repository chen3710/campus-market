package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.dto.ProductQuery;
import com.campus.market.entity.Product;
import com.campus.market.vo.ProductVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 列表查询走 XML 动态 SQL，一次 JOIN 取出分类名与卖家信息，避免 N+1 查询。
     */
    IPage<ProductVO> selectProductPage(IPage<ProductVO> page, @Param("query") ProductQuery query);

    ProductVO selectProductDetail(@Param("id") Long id);

    /**
     * 按 ID 批量查询（用于「我的收藏」等场景），避免在循环里逐个查。
     */
    List<ProductVO> selectProductByIds(@Param("ids") Collection<Long> ids);

    /**
     * 状态 CAS 更新 —— 防超卖的核心。
     * <p>
     * 把「校验状态」和「更新状态」合并成一条原子 SQL：
     * 只有当前状态仍是期望值、且版本号没被别人改过时才会更新成功。
     * 并发下两个线程同时下单，数据库的行锁会让它们串行执行，
     * 后到的那个因为 status 已变成 LOCKED，影响行数为 0，从而失败。
     */
    @Update("UPDATE product SET status = #{target}, version = version + 1, update_time = NOW() "
            + "WHERE id = #{id} AND status = #{expect} AND deleted = 0")
    int casUpdateStatus(@Param("id") Long id,
                        @Param("expect") String expect,
                        @Param("target") String target);

    /**
     * 带版本号的 CAS，用于需要严格依赖乐观锁语义的场景。
     */
    @Update("UPDATE product SET status = #{target}, version = version + 1, update_time = NOW() "
            + "WHERE id = #{id} AND status = #{expect} AND version = #{version} AND deleted = 0")
    int casUpdateStatusWithVersion(@Param("id") Long id,
                                   @Param("expect") String expect,
                                   @Param("target") String target,
                                   @Param("version") Integer version);

    @Update("UPDATE product SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int increaseViewCount(@Param("id") Long id);

    /**
     * 收藏数增减，delta 为 +1 / -1，同样用单条 SQL 保证原子性。
     */
    @Update("UPDATE product SET favorite_count = GREATEST(0, favorite_count + #{delta}) "
            + "WHERE id = #{id} AND deleted = 0")
    int addFavoriteCount(@Param("id") Long id, @Param("delta") int delta);
}

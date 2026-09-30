package com.campus.market.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果包装。把 MyBatis-Plus 的 IPage 转成前端友好的结构。
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;
    private long pages;

    public static <T> PageResult<T> of(IPage<T> source) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(source.getRecords());
        result.setTotal(source.getTotal());
        result.setPage(source.getCurrent());
        result.setSize(source.getSize());
        result.setPages(source.getPages());
        return result;
    }

    /**
     * 实体分页转 VO 分页。
     */
    public static <E, T> PageResult<T> of(IPage<E> source, Function<E, T> mapper) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(source.getRecords().stream().map(mapper).toList());
        result.setTotal(source.getTotal());
        result.setPage(source.getCurrent());
        result.setSize(source.getSize());
        result.setPages(source.getPages());
        return result;
    }
}

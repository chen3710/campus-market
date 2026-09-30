package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {

    /**
     * 信用分增减。用 GREATEST(0, ...) 保证不会出现负数，
     * 且整个操作在一条 SQL 里完成，天然原子，不会出现读-改-写的并发覆盖问题。
     */
    @Update("UPDATE `user` SET credit_score = GREATEST(0, credit_score + #{delta}), update_time = NOW() "
            + "WHERE id = #{userId} AND deleted = 0")
    int addCreditScore(@Param("userId") Long userId, @Param("delta") int delta);
}

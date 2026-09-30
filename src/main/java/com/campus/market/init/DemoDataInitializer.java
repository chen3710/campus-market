package com.campus.market.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.auth.LoginUser;
import com.campus.market.config.AppProperties;
import com.campus.market.entity.Category;
import com.campus.market.entity.Product;
import com.campus.market.entity.User;
import com.campus.market.enums.ProductStatus;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 演示数据初始化。
 * <p>
 * 只在 user 表为空时执行，避免每次重启都重复插入。
 * 用代码而不是 SQL 脚本插数据，是因为密码需要经过 BCrypt 加密 ——
 * 每次加密都会生成随机盐，哈希值无法预先写死在 init.sql 里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "123456";

    private final AppProperties properties;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (!properties.demoData().enabled()) {
            return;
        }

        Long userCount = userMapper.selectCount(null);
        if (userCount != null && userCount > 0) {
            log.info("检测到已有用户数据，跳过演示数据初始化");
            return;
        }

        Map<String, Category> categoryMap = categoryMapper.selectList(
                        new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortNo))
                .stream().collect(Collectors.toMap(Category::getName, Function.identity(), (a, b) -> a));

        if (categoryMap.isEmpty()) {
            log.warn("分类表为空，请先执行 sql/init.sql 再启动应用");
            return;
        }

        User admin = createUser("admin", "系统管理员", "主校区", LoginUser.ROLE_ADMIN, 100);
        User alice = createUser("alice", "林小雨", "东校区", LoginUser.ROLE_USER, 108);
        User bob = createUser("bob", "陈子航", "西校区", LoginUser.ROLE_USER, 96);
        User carol = createUser("carol", "苏念", "东校区", LoginUser.ROLE_USER, 102);

        List<Product> products = new ArrayList<>();
        products.add(buildProduct("高等数学 同济第七版 上下册", "大一用过的教材，书页干净无笔记，附赠配套习题册。",
                "48.00", "89.00", "教材书籍", alice, "九成新", categoryMap));
        products.add(buildProduct("iPad Air 5 64G 深空灰", "去年买的，一直贴膜带壳，无磕碰无划痕，电池健康 92%，带原装充电头。",
                "2680.00", "4399.00", "数码电子", alice, "九五新", categoryMap));
        products.add(buildProduct("罗技 MX Master 3S 无线鼠标", "买重了，全新未拆封，包装完好。",
                "520.00", "699.00", "数码电子", bob, "全新", categoryMap));
        products.add(buildProduct("山地自行车 26 寸 21 速", "毕业了带不走，车况良好，刚换过刹车皮，送车锁和打气筒。",
                "420.00", "1280.00", "代步工具", bob, "八成新", categoryMap));
        products.add(buildProduct("雅马哈 F310 民谣吉他", "大一入学买的，弹了一年多，音准很好，带琴包和变调夹。",
                "380.00", "899.00", "乐器文娱", carol, "九成新", categoryMap));
        products.add(buildProduct("宿舍小冰箱 双门 108L", "毕业清仓，制冷正常，外观有轻微使用痕迹。",
                "260.00", "799.00", "生活用品", carol, "八成新", categoryMap));
        products.add(buildProduct("耐克 Air Zoom 跑鞋 42 码", "跑了不到 50 公里，鞋底磨损很轻，尺码不合适转手。",
                "320.00", "799.00", "服饰鞋包", alice, "九成新", categoryMap));
        products.add(buildProduct("考研英语一 黄皮书真题套装", "2024 版全套，只做了前 5 套，其余全新。",
                "35.00", "128.00", "教材书籍", carol, "九成新", categoryMap));
        products.add(buildProduct("哑铃一对 可调节 20kg", "自用一年，配重片齐全，无锈迹。",
                "150.00", "399.00", "运动器材", bob, "八成新", categoryMap));
        products.add(buildProduct("索尼 WH-1000XM4 头戴耳机", "降噪效果依然很好，耳罩无脱皮，带原装收纳盒。",
                "980.00", "2299.00", "数码电子", alice, "九成新", categoryMap));

        products.forEach(productMapper::insert);

        log.info("演示数据初始化完成：{} 个用户（管理员 admin / 学生 alice、bob、carol），{} 件商品，统一密码 {}",
                4, products.size(), DEMO_PASSWORD);
        log.info("管理员账号: {} / {}", admin.getUsername(), DEMO_PASSWORD);
    }

    private User createUser(String username, String nickname, String campus, String role, int creditScore) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        user.setNickname(nickname);
        user.setCampus(campus);
        user.setPhone(null);
        user.setCreditScore(creditScore);
        user.setRole(role);
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);
        return user;
    }

    private Product buildProduct(String title, String description, String price, String originalPrice,
                                 String categoryName, User seller, String condition, Map<String, Category> categoryMap) {
        Category category = categoryMap.get(categoryName);
        Product product = new Product();
        product.setTitle(title);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setOriginalPrice(new BigDecimal(originalPrice));
        product.setCategoryId(category == null ? categoryMap.values().iterator().next().getId() : category.getId());
        product.setSellerId(seller.getId());
        product.setConditionLevel(condition);
        product.setStatus(ProductStatus.ON_SALE.name());
        product.setViewCount(0);
        product.setFavoriteCount(0);
        product.setVersion(0);
        product.setDeleted(0);
        return product;
    }
}

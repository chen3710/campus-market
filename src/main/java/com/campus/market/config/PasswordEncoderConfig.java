package com.campus.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 只引入 spring-security-crypto 这一个 jar 拿到 BCrypt，
 * 不引入 spring-boot-starter-security，避免它默认给所有接口套上登录页和过滤器链。
 * <p>
 * 为什么用 BCrypt 而不是 MD5：BCrypt 每次加密都会生成随机盐，同一个密码两次加密结果不同，
 * 攻击者无法通过彩虹表反查；且它的计算成本可调（strength），能有效抵抗暴力破解。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}

package com.campus.market.auth;

import com.campus.market.common.BusinessException;
import com.campus.market.common.ResultCode;
import com.campus.market.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验。
 * <p>
 * 采用 HS256 对称签名：密钥只在服务端持有，Token 本身不落库，
 * 因此服务端是无状态的，多实例部署时无需共享 Session。
 */
@Component
public class JwtUtil {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final long expireMillis;

    public JwtUtil(AppProperties properties) {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret 长度不足 32 字节，无法满足 HS256 的密钥强度要求");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret);
        this.expireMillis = properties.jwt().expireMinutes() * 60_000L;
    }

    /**
     * 签发 Token。只把用户 ID 作为 subject，用户名与角色放在自定义 claim 里，
     * 前端拿到的 Token 无法被篡改（篡改会导致签名校验失败）。
     */
    public String generate(LoginUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim(CLAIM_USERNAME, user.username())
                .claim(CLAIM_ROLE, user.role())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验 Token，失败统一抛 401。
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class),
                    claims.get(CLAIM_ROLE, String.class));
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录凭证无效，请重新登录");
        }
    }

    public long getExpireMillis() {
        return expireMillis;
    }
}

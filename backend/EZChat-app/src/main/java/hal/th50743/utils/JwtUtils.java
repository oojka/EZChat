package hal.th50743.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类
 * <p>
 * 负责 JWT Token 的生成与解析。
 */
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret; // JWT密钥

    @Value("${jwt.expiration}")
    private long expiration; // 默认过期时间（分钟）

    private Key key; // 签名密钥

    /**
     * 初始化方法，在Bean属性设置之后执行
     * 用于生成JWT签名所需的Key实例
     */
    @PostConstruct //bean初始化
    public void init() {
        // 检查密钥长度，HMAC-SHA256 至少需要 32 字节
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret key is too short. It must be at least 32 bytes (256 bits) long.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 根据给定的claims生成JWT，使用默认过期时间
     * @param claims 包含在JWT中的数据
     * @return 生成的JWT字符串
     */
    public String generateJwt(Map<String, Object> claims) {
        return generateJwt(claims, expiration);
    }

    /**
     * 根据给定的claims和自定义的过期时间生成JWT
     * @param claims 包含在JWT中的数据
     * @param expireTimeMinute 过期时间（分钟）
     * @return 生成的JWT字符串
     */
    public String generateJwt(Map<String, Object> claims, long expireTimeMinute) {
        Date now = new Date();
        if (expireTimeMinute < 1) {
            throw new IllegalArgumentException("Expiration time must be greater than 1 minute.");
        }
        Date expireDate = new Date(now.getTime() + expireTimeMinute * 60 * 1000);
        return Jwts.builder()
                .setClaims(claims) // 设置自定义声明
                .setIssuedAt(now)  // 设置签发时间 (iat)
                .setExpiration(expireDate) // 设置过期时间 (exp)
                .signWith(key, SignatureAlgorithm.HS256) // 设置签名算法和密钥
                .compact(); // 构建JWT
    }


    /**
     * 解析JWT，返回Claims
     * @param token 要解析的JWT字符串
     * @return 解析出的Claims对象
     * @throws JwtException 如果token无效或已过期
     */
    public Claims parseJwt(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(key) // 设置签名密钥
                .build()
                .parseClaimsJws(token) // 解析JWT
                .getBody(); // 获取Claims
    }
}

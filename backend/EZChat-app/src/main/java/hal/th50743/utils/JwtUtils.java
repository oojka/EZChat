package hal.th50743.utils;

import hal.th50743.service.CacheService;
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
 * JWT 工具类
 *
 * <p>提供 JWT Token 的生成和解析功能。
 *
 * <h3>Token 结构</h3>
 * <ul>
 *   <li><b>Header</b>：算法信息（alg: HS256, typ: JWT）</li>
 *   <li><b>Payload</b>：自定义声明（uid, username, tokenType, iat, exp）</li>
 *   <li><b>Signature</b>：HMAC-SHA256 签名</li>
 * </ul>
 *
 * <h3>Payload 声明（Claims）</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>uid</td><td>String</td><td>用户唯一标识符</td></tr>
 *   <tr><td>username</td><td>String</td><td>用户名（可选）</td></tr>
 *   <tr><td>tokenType</td><td>String</td><td>"access" 或 "refresh"</td></tr>
 *   <tr><td>iat</td><td>Date</td><td>签发时间（自动设置）</td></tr>
 *   <tr><td>exp</td><td>Date</td><td>过期时间（自动计算）</td></tr>
 * </table>
 *
 * <h3>安全要求</h3>
 * <ul>
 *   <li>密钥长度：至少 32 字节（256 位），满足 HMAC-SHA256 要求</li>
 *   <li>密钥来源：从环境变量 {@code JWT_SECRET} 读取</li>
 *   <li>算法：HMAC-SHA256（对称加密）</li>
 * </ul>
 *
 * @see hal.th50743.interceptor.TokenInterceptor Token 验证拦截器
 * @see CacheService Token 缓存服务
 */
@Component
public class JwtUtils {

    /**
     * JWT 密钥（从配置文件读取）
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * 签名密钥（初始化后不可变）
     */
    private Key key;

    /**
     * 初始化签名密钥
     *
     * <p>在 Bean 属性注入完成后执行，验证密钥长度并生成 Key 实例。
     *
     * @throws IllegalArgumentException 如果密钥长度不足 32 字节
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret key is too short. It must be at least 32 bytes (256 bits) long.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT Token
     *
     * <p>根据给定的声明和过期时间生成 JWT。
     * 自动设置签发时间（iat）和过期时间（exp）。
     *
     * @param claims           自定义声明（如 uid, username, tokenType）
     * @param expireTimeMinute 过期时间（分钟），必须大于 0
     * @return 生成的 JWT 字符串
     * @throws IllegalArgumentException 如果过期时间小于 1 分钟
     */
    public String generateJwt(Map<String, Object> claims, long expireTimeMinute) {
        Date now = new Date();
        if (expireTimeMinute < 1) {
            throw new IllegalArgumentException("Expiration time must be greater than 1 minute.");
        }
        Date expireDate = new Date(now.getTime() + expireTimeMinute * 60 * 1000);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 JWT Token
     *
     * <p>验证签名并提取 Claims。如果 Token 无效或已过期，将抛出异常。
     *
     * @param token 要解析的 JWT 字符串
     * @return 解析出的 Claims 对象
     * @throws JwtException 如果 Token 无效、签名不匹配或已过期
     */
    public Claims parseJwt(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

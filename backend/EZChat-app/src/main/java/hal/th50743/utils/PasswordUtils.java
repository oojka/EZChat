package hal.th50743.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码工具类
 * <p>
 * 提供统一的密码哈希加密和验证功能，使用 BCrypt 算法。
 * 所有涉及密码的操作必须通过此类进行，确保密码安全性。
 * <p>
 * <b>BCrypt 工作原理：</b>
 * <ul>
 *     <li>使用 BCrypt 算法进行密码哈希，默认强度为 10</li>
 *     <li>自动生成随机盐值，并将盐值嵌入到哈希字符串中</li>
 *     <li>线程安全，可全局复用</li>
 * </ul>
 * <p>
 * <b>BCrypt 哈希值格式：</b>
 * <pre>
 * $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 * │││││││││││││││││││││││││││││││││││││││││││││││││││││││││││││
 * │││││││││││││││││└─ 哈希值部分（31字符）
 * ││││││││││└─────────── 盐值部分（22字符，已嵌入在字符串中）
 * ││││└──────────────── Cost factor（强度参数，这里是10）
 * ││└────────────────── 算法版本标识（$2a$、$2b$ 或 $2y$）
 * </pre>
 * <p>
 * <b>数据库存储说明：</b>
 * <ul>
 *     <li>BCrypt 哈希值固定长度为 60 字符</li>
 *     <li>盐值已经嵌入在哈希字符串的前 29 个字符中，无需单独存储</li>
 *     <li>数据库表只需要一个 {@code password_hash varchar(255)} 字段即可</li>
 *     <li>不需要额外的 {@code salt} 字段，因为盐值已包含在哈希值中</li>
 *     <li>每次加密相同密码会生成不同的哈希值（因为盐值随机），但都存储在同一个字段中</li>
 * </ul>
 * <p>
 * <b>验证流程：</b>
 * <ol>
 *     <li>用户输入明文密码</li>
 *     <li>从数据库读取 {@code password_hash} 字段（包含盐值的完整哈希字符串）</li>
 *     <li>调用 {@link #matches(String, String)} 方法</li>
 *     <li>BCrypt 内部自动从哈希值中提取盐值（前29字符）</li>
 *     <li>使用提取的盐值对输入密码进行哈希</li>
 *     <li>比较结果是否匹配</li>
 * </ol>
 * <p>
 * <b>示例：</b>
 * <pre>
 * // 注册时加密密码
 * String passwordHash = PasswordUtils.encode("userPassword123");
 * // 结果：$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 * // 直接存储到数据库的 password_hash 字段
 *
 * // 登录时验证密码
 * String dbHash = "从数据库读取的 password_hash";
 * boolean isValid = PasswordUtils.matches("userPassword123", dbHash);
 * // BCrypt 会自动提取盐值并验证
 * </pre>
 */
public final class PasswordUtils {

    /**
     * BCrypt 密码编码器实例
     * <p>
     * BCryptPasswordEncoder 是线程安全的，可以作为静态成员变量复用。
     * 默认强度为 10，提供了安全性和性能的良好平衡。
     */
    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 私有构造函数，防止工具类被实例化。
     */
    private PasswordUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 对明文密码进行 BCrypt 哈希加密
     * <p>
     * 每次调用都会生成不同的哈希值（包含随机盐），即使密码相同。
     * 生成的哈希值长度为 60 字符，格式为：{@code $2a$10$盐值+哈希值}
     * <p>
     * <b>存储说明：</b>
     * <ul>
     *     <li>返回的哈希值已经包含了盐值，直接存储到数据库的 {@code password_hash} 字段即可</li>
     *     <li>不需要单独存储盐值字段</li>
     *     <li>数据库字段类型建议：{@code varchar(255)} 或 {@code char(60)}</li>
     * </ul>
     * <p>
     * <b>示例：</b>
     * <pre>
     * String hash = PasswordUtils.encode("mypassword");
     * // 结果：$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
     * // 直接存储到数据库：INSERT INTO users (password_hash) VALUES (hash)
     * </pre>
     *
     * @param rawPassword 明文密码
     * @return 哈希后的密码字符串（60字符，包含盐值，可直接存储到数据库的 password_hash 字段）
     * @throws IllegalArgumentException 如果 rawPassword 为 null
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Raw password cannot be null");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 验证明文密码是否与哈希值匹配
     * <p>
     * BCrypt 会自动从哈希值中提取盐值进行验证。
     * 验证过程：
     * <ol>
     *     <li>从 {@code encodedPassword} 中提取盐值（前29个字符）</li>
     *     <li>使用提取的盐值对 {@code rawPassword} 进行哈希</li>
     *     <li>比较生成的哈希值与 {@code encodedPassword} 是否匹配</li>
     * </ol>
     * <p>
     * <b>注意：</b>
     * <ul>
     *     <li>{@code encodedPassword} 必须是从数据库 {@code password_hash} 字段读取的完整哈希值</li>
     *     <li>哈希值必须包含盐值（即完整的60字符 BCrypt 哈希字符串）</li>
     *     <li>如果数据库中存储的是旧格式的明文或简单哈希，此方法将无法正确验证</li>
     * </ul>
     * <p>
     * <b>示例：</b>
     * <pre>
     * // 从数据库读取
     * String dbHash = user.getPasswordHash(); // $2a$10$N9qo8uLOickgx2ZMRZoMye...
     * // 用户输入的密码
     * String userInput = "mypassword";
     * // 验证
     * boolean isValid = PasswordUtils.matches(userInput, dbHash);
     * </pre>
     *
     * @param rawPassword     用户输入的明文密码
     * @param encodedPassword 数据库中存储的完整 BCrypt 哈希值（60字符，包含盐值）
     * @return 如果密码匹配返回 true，否则返回 false
     * @throws IllegalArgumentException 如果任一参数为 null
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            throw new IllegalArgumentException("Raw password and encoded password cannot be null");
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 检查给定的字符串是否为有效的 BCrypt 哈希值
     * <p>
     * BCrypt 哈希值格式要求：
     * <ul>
     *     <li>必须以 "{@code $2a$}", "{@code $2b$}" 或 "{@code $2y$}" 开头</li>
     *     <li>固定长度为 60 字符</li>
     *     <li>前29个字符包含算法标识、强度参数和盐值</li>
     *     <li>后31个字符为哈希值</li>
     * </ul>
     * <p>
     * <b>用途：</b>
     * <ul>
     *     <li>数据迁移时检查密码是否已加密</li>
     *     <li>验证数据库中的密码格式是否正确</li>
     *     <li>防止对已加密的密码重复加密</li>
     * </ul>
     * <p>
     * <b>示例：</b>
     * <pre>
     * String hash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
     * boolean isValid = PasswordUtils.isEncoded(hash); // true
     *
     * String plain = "mypassword";
     * boolean isPlain = PasswordUtils.isEncoded(plain); // false
     * </pre>
     *
     * @param encodedPassword 待检查的字符串（通常是从数据库 password_hash 字段读取的值）
     * @return 如果是有效的 BCrypt 哈希值（60字符，以 $2a$/$2b$/$2y$ 开头）返回 true，否则返回 false
     */
    public static boolean isEncoded(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.length() != 60) {
            return false;
        }
        return encodedPassword.startsWith("$2a$") ||
               encodedPassword.startsWith("$2b$") ||
               encodedPassword.startsWith("$2y$");
    }
}

package hal.th50743.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 邀请码工具
 *
 * 业务目的：
 * - 生成短邀请码（用于短链接）
 * - 计算 SHA-256 哈希（服务端入库保存 hash，避免 DB 泄露导致邀请码直接可用）
 */
public class InviteCodeUtils {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private InviteCodeUtils() {}

    /**
     * 生成 URL-safe 的短邀请码（Base62）
     *
     * @param length 长度（建议 16~24）
     */
    public static String generateInviteCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62[RANDOM.nextInt(BASE62.length)]);
        }
        return sb.toString();
    }

    /**
     * SHA-256 hex
     */
    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            // 业务上属于“不可恢复的系统错误”
            throw new IllegalStateException("Failed to hash invite code", e);
        }
    }
}



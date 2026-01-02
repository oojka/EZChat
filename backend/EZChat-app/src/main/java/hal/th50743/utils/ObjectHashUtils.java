package hal.th50743.utils;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 对象哈希计算工具类
 * <p>
 * 业务目的：统一对象哈希计算逻辑，确保前后端哈希算法一致。
 */
public final class ObjectHashUtils {

    private ObjectHashUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 计算字节数组的 SHA-256 哈希值（hex 字符串）
     * <p>
     * 业务说明：
     * - 使用 SHA-256 算法计算哈希
     * - 返回 64 字符的十六进制字符串（小写）
     * - 与前端 Web Crypto API 的计算结果一致
     *
     * @param bytes 字节数组
     * @return SHA-256 hex 字符串（64 字符，小写）
     * @throws IllegalStateException 如果哈希计算失败
     */
    public static String calculateSHA256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate object hash", e);
        }
    }
}


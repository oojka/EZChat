package hal.th50743.utils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 文件名格式化工具类
 * <p>
 * 负责文件名的安全验证、格式化与合规化处理，防止路径穿越和非法字符。
 */
public class FileNameFormater {

    // 预编译正则：仅允许字母、数字、点、下划线、中划线
    private static final Pattern WHITE_LIST_PATTERN = Pattern.compile("^[a-zA-Z0-9.\\-_]+$");
    private static final Pattern ILLEGAL_CHAR_PATTERN = Pattern.compile("[^a-zA-Z0-9.\\-_]");
    private static final Pattern CONSECUTIVE_UNDERSCORES = Pattern.compile("_+");
    private static final Pattern TRIM_UNDERSCORES = Pattern.compile("^_+|_+$");

    private static final int MAX_BYTE_LENGTH = 200;
    private static final String DEFAULT_NAME = "file";

    /**
     * 【复合方法】获取安全文件名
     * 逻辑：验证通过且长度合规，返回原名；否则强制执行清洗格式化。
     *
     * @param originalName 原始文件名
     * @return 安全的文件名
     */
    public static String getSafeName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "unnamed_file";
        }

        // 提取纯文件名（防御路径穿越）
        String baseName = new java.io.File(originalName).getName();

        // 如果符合白名单且字节长度合规，直接返回
        if (isValidFormat(baseName) && isLengthValid(baseName)) {
            return baseName;
        }

        // 否则执行格式化修正
        return format(baseName);
    }

    /**
     * 格式化逻辑：强制清洗非法字符并压缩
     *
     * @param originalName 原始文件名
     * @return 格式化后的文件名
     */
    public static String format(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "unnamed_file";
        }

        String baseName = new java.io.File(originalName).getName();
        String extension = "";
        String name = baseName;

        // 分离后缀
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            name = baseName.substring(0, lastDot);
            extension = baseName.substring(lastDot).toLowerCase();
        }

        // 清洗：非法字符转下划线 -> 合并连续下划线 -> 去除首尾符号
        String sanitized = ILLEGAL_CHAR_PATTERN.matcher(name).replaceAll("_");
        sanitized = CONSECUTIVE_UNDERSCORES.matcher(sanitized).replaceAll("_");
        sanitized = TRIM_UNDERSCORES.matcher(sanitized).replaceAll("");

        if (sanitized.isEmpty()) {
            sanitized = DEFAULT_NAME;
        }

        // 字节级截断处理
        int maxNameBytes = MAX_BYTE_LENGTH - extension.getBytes(StandardCharsets.UTF_8).length;
        sanitized = truncateByBytes(sanitized, maxNameBytes);

        return sanitized + extension;
    }

    /**
     * 基础验证：是否仅包含白名单字符且无路径风险
     *
     * @param originalName 原始文件名
     * @return 合法返回 true，否则返回 false
     */
    public static boolean isValid(String originalName) {
        if (originalName == null || originalName.isBlank()) return false;
        // 路径风险检查
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            return false;
        }
        return isValidFormat(originalName);
    }

    private static boolean isValidFormat(String name) {
        return WHITE_LIST_PATTERN.matcher(name).matches();
    }

    private static boolean isLengthValid(String name) {
        return name.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTE_LENGTH;
    }

    /**
     * 截断逻辑：由于sanitized已限定为ASCII，length()即为字节数
     */
    private static String truncateByBytes(String s, int maxBytes) {
        if (s.length() <= maxBytes) return s;
        return s.substring(0, Math.max(0, maxBytes));
    }
}

package hal.th50743.utils;

import java.security.SecureRandom;

/**
 * UID 生成器
 * <p>
 * 负责生成指定长度的随机数字字符串。
 */
public class UidGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成指定长度的随机数字字符串
     *
     * @param length 长度
     * @return 随机数字字符串
     */
    public static String generateUid(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10)); // 0~9
        }
        return sb.toString();
    }

}

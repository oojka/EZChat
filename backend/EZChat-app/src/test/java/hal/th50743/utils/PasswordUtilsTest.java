package hal.th50743.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码工具类单元测试
 *
 * <p>覆盖以下场景:</p>
 * <ul>
 *     <li>BCrypt编码和匹配</li>
 *     <li>空参数校验</li>
 * </ul>
 *
 * @see PasswordUtils
 */
class PasswordUtilsTest {

    /**
     * 编码与匹配 - BCrypt编码后可正确匹配原密码
     */
    @Test
    void encodeAndMatchesWork() {
        String hash = PasswordUtils.encode("pw");

        assertTrue(PasswordUtils.isEncoded(hash));
        assertTrue(PasswordUtils.matches("pw", hash));
        assertFalse(PasswordUtils.matches("other", hash));
    }

    /**
     * 编码 - 传入null时抛出IllegalArgumentException异常
     */
    @Test
    void encodeRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtils.encode(null));
    }

    /**
     * 匹配 - 传入null参数时抛出IllegalArgumentException异常
     */
    @Test
    void matchesRejectsNulls() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtils.matches(null, "hash"));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtils.matches("pw", null));
    }
}

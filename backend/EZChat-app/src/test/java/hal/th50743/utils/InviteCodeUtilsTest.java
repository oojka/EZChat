package hal.th50743.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邀请码工具类单元测试
 *
 * <p>覆盖以下场景:</p>
 * <ul>
 *     <li>Base62邀请码生成</li>
 *     <li>SHA-256哈希计算</li>
 * </ul>
 *
 * @see InviteCodeUtils
 */
class InviteCodeUtilsTest {

    /**
     * 生成邀请码 - 返回指定长度的Base62字符串
     */
    @Test
    void generateInviteCodeProducesBase62() {
        String code = InviteCodeUtils.generateInviteCode(16);

        assertEquals(16, code.length());
        assertTrue(code.matches("[0-9A-Za-z]+"));
    }

    /**
     * SHA-256哈希 - 计算结果与已知值匹配
     */
    @Test
    void sha256HexMatchesKnownValue() {
        String hash = InviteCodeUtils.sha256Hex("invite");

        assertEquals("5014f9af3a684fdd64a775c1c4c532ee66b1b96cb56b1d02170d2249b6764f75", hash);
    }
}

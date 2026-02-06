package hal.th50743.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UID生成器单元测试
 *
 * @see UidGenerator
 */
class UidGeneratorTest {

    /**
     * 生成UID - 返回指定长度的纯数字字符串
     */
    @Test
    void generateUidReturnsDigits() {
        String uid = UidGenerator.generateUid(8);

        assertEquals(8, uid.length());
        assertTrue(uid.matches("\\d{8}"));
    }
}

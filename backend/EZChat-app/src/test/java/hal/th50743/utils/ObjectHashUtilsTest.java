package hal.th50743.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 对象哈希工具单元测试
 *
 * <p>验证 SHA-256 哈希计算的正确性</p>
 *
 * @see ObjectHashUtils
 */
class ObjectHashUtilsTest {

    /**
     * SHA-256 计算 - 验证结果与已知值一致
     */
    @Test
    void calculateSHA256MatchesKnownValue() {
        String hash = ObjectHashUtils.calculateSHA256("hello".getBytes(StandardCharsets.UTF_8));

        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash);
    }
}

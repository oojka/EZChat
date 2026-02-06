package hal.th50743.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件名格式化工具单元测试
 *
 * <p>覆盖以下场景:</p>
 * <ul>
 *     <li>安全文件名生成 (getSafeName): 有效名称保留、无效字符替换、空白名称处理</li>
 *     <li>文件名校验 (isValid): 路径遍历攻击防护、白名单字符验证</li>
 * </ul>
 *
 * @see FileNameFormater
 */
class FileNameFormaterTest {

    /**
     * 安全文件名 - 有效名称原样保留
     */
    @Test
    void getSafeNameKeepsValidName() {
        assertEquals("photo.png", FileNameFormater.getSafeName("photo.png"));
    }

    /**
     * 安全文件名 - 无效字符替换为下划线
     */
    @Test
    void getSafeNameFormatsInvalidCharacters() {
        assertEquals("my_file.png", FileNameFormater.getSafeName("my file.png"));
        assertEquals("my_file.txt", FileNameFormater.getSafeName("my#file.txt"));
    }

    /**
     * 安全文件名 - 空白名称使用默认值 "unnamed_file"
     */
    @Test
    void getSafeNameHandlesBlank() {
        assertEquals("unnamed_file", FileNameFormater.getSafeName(" "));
    }

    /**
     * 文件名校验 - 拒绝包含路径遍历字符的文件名 (安全相关)
     */
    @Test
    void isValidRejectsPathTraversal() {
        assertFalse(FileNameFormater.isValid("../evil.png"));
        assertFalse(FileNameFormater.isValid("dir/evil.png"));
        assertFalse(FileNameFormater.isValid("dir\\evil.png"));
    }

    /**
     * 文件名校验 - 接受符合白名单规则的文件名
     */
    @Test
    void isValidAcceptsWhitelist() {
        assertTrue(FileNameFormater.isValid("safe_name-1.0.png"));
    }
}

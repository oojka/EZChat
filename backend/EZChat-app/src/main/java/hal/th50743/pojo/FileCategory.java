package hal.th50743.pojo;

/**
 * 文件分类枚举
 * <p>
 * 用于标识对象的业务用途，对应 objects 表的 category 字段。
 * 类型安全，避免字符串拼写错误。
 */
public enum FileCategory {
    /**
     * 用户头像
     */
    USER_AVATAR("USER_AVATAR"),
    
    /**
     * 聊天室封面/群头像
     */
    CHAT_COVER("CHAT_COVER"),
    
    /**
     * 消息图片
     */
    MESSAGE_IMG("MESSAGE_IMG"),
    
    /**
     * 通用文件（未分类）
     */
    GENERAL("GENERAL");

    private final String value;

    FileCategory(String value) {
        this.value = value;
    }

    /**
     * 获取数据库存储值
     *
     * @return 字符串值
     */
    public String getValue() {
        return value;
    }

    /**
     * 从字符串值转换为枚举
     *
     * @param value 字符串值
     * @return FileCategory 枚举，如果不存在则返回 GENERAL
     */
    public static FileCategory fromValue(String value) {
        if (value == null) {
            return GENERAL;
        }
        for (FileCategory category : FileCategory.values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        return GENERAL;
    }
}



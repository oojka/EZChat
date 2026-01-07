package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息视图对象
 * <p>
 * 用于前端展示消息内容，包含发送者、消息类型、内容及图片等信息。
 * 与数据库中的 Message 实体对应，但包含更多前端展示所需的信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageVO {

    /**
     * 发送者用户对外ID（users.uid）
     */
    private String sender;

    /**
     * 聊天室代码（chats.chat_code）
     */
    private String chatCode;

    /**
     * 消息类型
     * <p>
     * 业务规则：
     * - 0: 纯文本消息
     * - 1: 纯图片消息
     * - 2: 混合消息（文本+图片）
     */
    private Integer type;

    /**
     * 消息文本内容
     * <p>
     * 业务规则：
     * - 纯文本消息：包含完整文本内容
     * - 纯图片消息：通常为空或包含图片描述
     * - 混合消息：包含文本内容
     */
    private String text;

    /**
     * 图片对象ID列表的JSON字符串（临时字段，用于数据转换）
     * <p>
     * 业务说明：
     * - 格式：JSON数组，如 "[1,2,3]"
     * - 用于从数据库查询结果转换为前端需要的格式
     * - 最终会被转换为 images 字段
     */
    private String assetIds;

    /**
     * 消息图片列表
     * <p>
     * 业务规则：
     * - 纯文本消息：空列表
     * - 纯图片消息：包含所有图片
     * - 混合消息：包含所有图片
     */
    private List<Image> images;

    /**
     * 消息创建时间
     */
    private LocalDateTime createTime;

}
package hal.th50743.service;

import hal.th50743.pojo.Image;
import hal.th50743.pojo.MessageReq;
import hal.th50743.pojo.MessageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 消息服务接口
 * <p>
 * 定义消息的发送、存储、查询及文件上传业务逻辑。
 */
public interface MessageService {

    /**
     * 添加消息到数据库
     *
     * @param userId 发送用户ID
     * @param chatId 聊天ID
     * @param text   文本内容
     * @param images 图片列表
     * @return 消息的 Sequence ID
     */
    Long addMessage(Integer userId, Integer chatId, String text, List<Image> images);

    /**
     * 处理 WebSocket 传入的新消息
     *
     * @param userId 发送用户ID
     * @param msg    消息请求对象
     * @return 处理结果（包含接收用户列表和 seqId）
     */
    hal.th50743.pojo.WSMessageResult handleWSMessage(Integer userId, MessageReq msg);

    /**
     * 根据聊天代码获取消息列表
     *
     * @param userID    当前用户ID
     * @param chatCode  聊天室代码
     * @param timeStamp 时间戳（可选）
     * @return 包含消息列表和聊天室信息的 DTO
     */
    hal.th50743.pojo.MessageListVO getMessagesByChatCode(Integer userID, String chatCode, Long cursorSeqId);

    /**
     * 同步消息（拉取指定序列号之后的消息）
     *
     * @param userId    当前用户ID
     * @param chatCode  聊天室代码
     * @param lastSeqId 上次同步的最后序列号
     * @return 消息列表
     */
    List<MessageVO> syncMessages(Integer userId, String chatCode, Long lastSeqId);

    /**
     * 上传文件
     *
     * @param file 文件对象
     * @return 图片对象
     */
    Image upload(MultipartFile file);
}

package hal.th50743.pojo;

/**
 * 创建聊天室返回对象
 *
 * 业务目的：
 * - 创建成功后，前端需要展示房间 chatCode 与短邀请码（用于生成邀请链接）
 *
 * @param chatCode   8 位数字房间号
 * @param inviteCode 邀请短码（明文，仅用于分发；服务端落库保存其哈希）
 */
public record CreateChatVO(String chatCode, String inviteCode) {
}



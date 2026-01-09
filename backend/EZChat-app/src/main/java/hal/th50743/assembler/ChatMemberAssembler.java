package hal.th50743.assembler;

import hal.th50743.pojo.*;
import hal.th50743.service.AssetService;
import hal.th50743.utils.ImageUtils;
import io.minio.MinioOSSOperator;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 聊天成员/用户相关数据组装器
 * <p>
 * 负责 ChatMember, User 到 ChatMemberVO 的转换，以及 JoinBroadcastVO 的构建。
 * 剥离了原本位于 ChatAssembler 和 ChatServiceImpl 中的成员处理逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMemberAssembler {

    private final MinioOSSOperator minioOSSOperator;
    private final AssetService assetService;

    /**
     * 将 ChatMember 列表转换为 ChatMemberVO 列表
     *
     * @param members     成员列表
     * @param onlineUsers 在线用户 Map
     * @return ChatMemberVO 列表
     */
    public List<ChatMemberVO> toChatMemberVOList(List<ChatMember> members, Map<Integer, Session> onlineUsers) {
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMemberVO> memberVOList = new ArrayList<>();
        for (ChatMember m : members) {
            boolean isOnline = isUserOnline(m.getUserId(), onlineUsers);
            memberVOList.add(toChatMemberVO(m, isOnline));
        }
        return memberVOList;
    }

    /**
     * 将单个 ChatMember 转换为 ChatMemberVO
     *
     * @param member   成员实体
     * @param isOnline 是否在线
     * @return ChatMemberVO
     */
    public ChatMemberVO toChatMemberVO(ChatMember member, boolean isOnline) {
        ChatMemberVO vo = new ChatMemberVO();
        vo.setUid(member.getUid());
        vo.setChatCode(member.getChatCode());
        vo.setNickname(member.getNickname());
        vo.setOnline(isOnline);
        vo.setLastSeenAt(member.getLastSeenAt());

        // 优先使用 JOIN 查询出的 assetName，如果为空则尝试使用 assetId 回退
        String assetName = member.getAvatarAssetName();
        if (assetName == null && member.getAssetId() != null) {
            // Fallback: JOIN failed (e.g. Asset cleaned up or timing issue), verify with
            // AssetService
            Asset asset = assetService.findById(member.getAssetId());
            if (asset != null) {
                assetName = asset.getAssetName();
            }
        }

        if (assetName != null || member.getAssetId() != null) {
            vo.setAvatar(buildAvatar(assetName, member.getAssetId()));
        }

        return vo;
    }

    /**
     * 构建聊天成员视图对象（包含头像信息）- 用于新成员加入时
     * <p>
     * 逻辑抽取自原 ChatServiceImpl.getChatMemberVO
     *
     * @param user     用户信息对象
     * @param chatCode 聊天室代码
     * @param joinTime 加入时间
     * @return ChatMemberVO
     */
    public ChatMemberVO toChatMemberVO(User user, String chatCode, LocalDateTime joinTime) {
        // 1. 创建基础成员信息对象
        ChatMemberVO memberVO = new ChatMemberVO(
                user.getUid(), // 用户唯一标识
                chatCode, // 聊天室代码
                user.getNickname(), // 用户昵称
                null, // 头像暂设为null，后续设置
                true, // 在线状态（新加入的用户默认在线）
                joinTime); // 最后活跃时间（即加入时间）

        // 2. 处理用户头像
        // 2. 处理用户头像
        if (user.getAssetId() != null) {
            Asset asset = assetService.findById(user.getAssetId());
            String assetName = (asset != null) ? asset.getAssetName() : null;
            memberVO.setAvatar(buildAvatar(assetName, user.getAssetId()));

            if (asset == null) {
                log.debug("Asset not found for user avatar: assetId={}, userId={}", user.getAssetId(), user.getId());
            }
        }

        return memberVO;
    }

    /**
     * 构建加入聊天室的广播消息对象
     *
     * @param user        用户信息
     * @param chatCode    聊天室代码
     * @param displayName 显示名称
     * @param now         当前时间
     * @return JoinBroadcastVO
     */
    public JoinBroadcastVO toJoinBroadcastVO(User user, String chatCode, String displayName, LocalDateTime now) {
        // 1. 构造成员信息对象（利用复用逻辑）
        ChatMemberVO memberVO = toChatMemberVO(user, chatCode, now);

        // 2. 构造广播数据对象
        JoinBroadcastVO broadcastVO = new JoinBroadcastVO();
        broadcastVO.setSender(user.getUid()); // 发送者UID
        broadcastVO.setChatCode(chatCode); // 聊天室代码
        broadcastVO.setType(11); // 消息类型：成员加入
        broadcastVO.setText(displayName); // 显示名称
        broadcastVO.setCreateTime(now); // 创建时间
        broadcastVO.setMember(memberVO); // 成员详细信息

        return broadcastVO;
    }

    /**
     * 判断用户是否在线
     *
     * @param userId      用户 ID
     * @param onlineUsers 在线用户 Map
     * @return 是否在线
     */
    public boolean isUserOnline(Integer userId, Map<Integer, Session> onlineUsers) {
        return userId != null && onlineUsers != null && onlineUsers.containsKey(userId);
    }

    private Image buildAvatar(String assetName, Integer assetId) {
        Image image = ImageUtils.buildImage(assetName, minioOSSOperator);
        if (image != null) {
            image.setAssetId(assetId);
        } else if (assetId != null) {
            // Fallback: 只有ID没有文件的情况（例如文件被删或未找到）
            image = new Image();
            image.setAssetId(assetId);
        }
        return image;
    }
}

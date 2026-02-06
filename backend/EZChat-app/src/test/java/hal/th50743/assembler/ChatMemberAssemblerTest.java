package hal.th50743.assembler;

import hal.th50743.pojo.Asset;
import hal.th50743.pojo.ChatMember;
import hal.th50743.pojo.ChatMemberVO;
import hal.th50743.pojo.JoinBroadcastVO;
import hal.th50743.pojo.User;
import hal.th50743.service.AssetService;
import io.minio.MinioOSSOperator;
import io.minio.MinioOSSResult;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 聊天成员组装器单元测试
 *
 * <p>覆盖以下场景:</p>
 * <ul>
 *     <li>成员列表转换为VO列表</li>
 *     <li>头像资源名称解析</li>
 *     <li>AssetService回退逻辑</li>
 *     <li>在线状态判断</li>
 *     <li>加入广播VO构建</li>
 * </ul>
 *
 * @see ChatMemberAssembler
 */
@ExtendWith(MockitoExtension.class)
class ChatMemberAssemblerTest {

    @Mock
    private MinioOSSOperator minioOSSOperator;

    @Mock
    private AssetService assetService;

    /**
     * 转换成员列表 - 空列表返回空结果
     */
    @Test
    void toChatMemberVOListEmptyReturnsEmpty() {
        ChatMemberAssembler assembler = new ChatMemberAssembler(minioOSSOperator, assetService);

        List<ChatMemberVO> result = assembler.toChatMemberVOList(List.of(), Map.<Integer, Session>of());

        assertTrue(result.isEmpty());
    }

    /**
     * 转换成员VO - 使用成员自带的头像资源名称
     */
    @Test
    void toChatMemberVOUsesAssetName() {
        ChatMemberAssembler assembler = new ChatMemberAssembler(minioOSSOperator, assetService);

        ChatMember member = new ChatMember();
        member.setUid("u1");
        member.setChatCode("12345678");
        member.setNickname("nick");
        member.setAssetId(4);
        member.setAvatarAssetName("public/a.png");

        when(minioOSSOperator.getImageUrls(eq("public/a.png"), eq(30), eq(java.util.concurrent.TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/a.png", "url", "thumb"));

        ChatMemberVO vo = assembler.toChatMemberVO(member, true);

        assertNotNull(vo.getAvatar());
        assertEquals(Integer.valueOf(4), vo.getAvatar().getAssetId());
        assertEquals("public/a.png", vo.getAvatar().getImageName());
    }

    /**
     * 转换成员VO - 回退到AssetService查询头像资源
     */
    @Test
    void toChatMemberVOFallbacksToAssetService() {
        ChatMemberAssembler assembler = new ChatMemberAssembler(minioOSSOperator, assetService);

        ChatMember member = new ChatMember();
        member.setUid("u1");
        member.setChatCode("12345678");
        member.setNickname("nick");
        member.setAssetId(7);

        Asset asset = new Asset();
        asset.setId(7);
        asset.setAssetName("public/b.png");
        when(assetService.findById(7)).thenReturn(asset);
        when(minioOSSOperator.getImageUrls(eq("public/b.png"), eq(30), eq(java.util.concurrent.TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/b.png", "url", "thumb"));

        ChatMemberVO vo = assembler.toChatMemberVO(member, false);

        assertNotNull(vo.getAvatar());
        assertEquals("public/b.png", vo.getAvatar().getImageName());
        assertEquals(Integer.valueOf(7), vo.getAvatar().getAssetId());
    }

    /**
     * 转换成员VO - AssetService未找到时仅返回资源ID
     */
    @Test
    void toChatMemberVOFallbacksToAssetIdOnly() {
        ChatMemberAssembler assembler = new ChatMemberAssembler(minioOSSOperator, assetService);

        ChatMember member = new ChatMember();
        member.setUid("u1");
        member.setChatCode("12345678");
        member.setNickname("nick");
        member.setAssetId(9);

        when(assetService.findById(9)).thenReturn(null);

        ChatMemberVO vo = assembler.toChatMemberVO(member, false);

        assertNotNull(vo.getAvatar());
        assertEquals(Integer.valueOf(9), vo.getAvatar().getAssetId());
        assertNull(vo.getAvatar().getImageName());
    }

    /**
     * 从User转换成员VO - 构建头像并设置在线状态
     */
    @Test
    void toChatMemberVOFromUserBuildsAvatar() {
        ChatMemberAssembler assembler = new ChatMemberAssembler(minioOSSOperator, assetService);
        User user = new User();
        user.setId(1);
        user.setUid("u1");
        user.setNickname("nick");
        user.setAssetId(5);

        Asset asset = new Asset();
        asset.setId(5);
        asset.setAssetName("public/c.png");
        when(assetService.findById(5)).thenReturn(asset);
        when(minioOSSOperator.getImageUrls(eq("public/c.png"), eq(30), eq(java.util.concurrent.TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/c.png", "url", "thumb"));

        LocalDateTime joinTime = LocalDateTime.now();
        ChatMemberVO vo = assembler.toChatMemberVO(user, "12345678", joinTime);

        assertEquals("u1", vo.getUid());
        assertTrue(vo.isOnline());
        assertEquals(joinTime, vo.getLastSeenAt());
        assertEquals(Integer.valueOf(5), vo.getAvatar().getAssetId());
    }

    /**
     * 构建加入广播VO - 包含成员信息和系统消息类型
     */
    @Test
    void toJoinBroadcastVOUsesMember() {
        ChatMemberAssembler assembler = new ChatMemberAssembler(minioOSSOperator, assetService);
        User user = new User();
        user.setUid("u1");
        user.setNickname("nick");

        LocalDateTime now = LocalDateTime.now();
        JoinBroadcastVO vo = assembler.toJoinBroadcastVO(user, "12345678", "nick", now);

        assertEquals("u1", vo.getSender());
        assertEquals("12345678", vo.getChatCode());
        assertEquals(Integer.valueOf(11), vo.getType());
        assertEquals("nick", vo.getText());
        assertEquals(now, vo.getCreateTime());
        assertNotNull(vo.getMember());
    }

    /**
     * 判断用户在线状态 - 根据在线用户Map判断
     */
    @Test
    void isUserOnlineChecksMap() {
        ChatMemberAssembler assembler = new ChatMemberAssembler(minioOSSOperator, assetService);
        Map<Integer, Session> online = new HashMap<>();
        online.put(1, mock(Session.class));
        assertTrue(assembler.isUserOnline(1, online));
        assertFalse(assembler.isUserOnline(2, online));
    }
}

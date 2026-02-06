package hal.th50743.assembler;

import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.pojo.Asset;
import hal.th50743.pojo.ChatMember;
import hal.th50743.pojo.ChatMemberVO;
import hal.th50743.pojo.ChatVO;
import hal.th50743.pojo.MessageVO;
import hal.th50743.service.AssetService;
import io.minio.MinioOSSOperator;
import io.minio.MinioOSSResult;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 聊天组装器单元测试
 *
 * <p>覆盖以下场景:</p>
 * <ul>
 *     <li>完整组装(成员、头像、未读数)</li>
 *     <li>精简组装(默认值处理)</li>
 *     <li>无效JSON处理</li>
 *     <li>公开化字段清理</li>
 * </ul>
 *
 * @see ChatAssembler
 */
@ExtendWith(MockitoExtension.class)
class ChatAssemblerTest {

    @Mock
    private MinioOSSOperator minioOSSOperator;

    @Mock
    private AssetService assetService;

    @Mock
    private ChatMemberAssembler chatMemberAssembler;

    /**
     * 完整组装 - 填充成员列表、头像URL、未读数和在线人数
     */
    @Test
    void assemblePopulatesMembersAndImages() {
        ChatAssembler assembler = new ChatAssembler(minioOSSOperator, assetService, new ObjectMapper(),
                chatMemberAssembler);

        ChatVO chatVO = new ChatVO();
        chatVO.setAvatarAssetName("public/avatar.png");
        chatVO.setCreateTime(LocalDateTime.now().minusMinutes(5));

        MessageVO lastMsg = new MessageVO();
        lastMsg.setAssetIds("[1]");
        lastMsg.setCreateTime(LocalDateTime.now());

        Asset asset = new Asset();
        asset.setId(1);
        asset.setAssetName("public/msg.png");
        when(assetService.findById(1)).thenReturn(asset);
        when(minioOSSOperator.getImageUrls(eq("public/avatar.png"), eq(30), eq(java.util.concurrent.TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/avatar.png", "avatar-url", "avatar-thumb"));
        when(minioOSSOperator.getImageUrls(eq("public/msg.png"), eq(30), eq(java.util.concurrent.TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/msg.png", "msg-url", "msg-thumb"));

        ChatMember member1 = new ChatMember("u1", 1, "12345678", 1, "nick1", null,
                LocalDateTime.now(), null, null, null);
        ChatMember member2 = new ChatMember("u2", 1, "12345678", 2, "nick2", null,
                LocalDateTime.now(), null, null, null);
        when(chatMemberAssembler.isUserOnline(eq(1), any())).thenReturn(true);
        when(chatMemberAssembler.isUserOnline(eq(2), any())).thenReturn(false);
        when(chatMemberAssembler.toChatMemberVO(any(ChatMember.class), anyBoolean())).thenAnswer(invocation -> {
            ChatMember m = invocation.getArgument(0);
            boolean online = invocation.getArgument(1);
            ChatMemberVO vo = new ChatMemberVO();
            vo.setUid(m.getUid());
            vo.setOnline(online);
            return vo;
        });

        assembler.assemble(chatVO, Map.<Integer, Session>of(), lastMsg, 3, List.of(member1, member2));

        assertNotNull(chatVO.getAvatar());
        assertNull(chatVO.getAvatarAssetName());
        assertEquals(3, chatVO.getUnreadCount());
        assertEquals(1, chatVO.getOnLineMemberCount());
        assertEquals(2, chatVO.getChatMembers().size());

        assertNotNull(lastMsg.getImages());
        assertEquals(1, lastMsg.getImages().size());
        assertEquals(Integer.valueOf(1), lastMsg.getImages().get(0).getAssetId());
        assertNull(lastMsg.getAssetIds());
        assertEquals(lastMsg, chatVO.getLastMessage());
        assertEquals(lastMsg.getCreateTime(), chatVO.getLastActiveAt());
    }

    /**
     * 精简组装 - 无消息时使用默认值
     */
    @Test
    void assembleLiteUsesDefaults() {
        ChatAssembler assembler = new ChatAssembler(minioOSSOperator, assetService, new ObjectMapper(),
                chatMemberAssembler);

        ChatVO chatVO = new ChatVO();
        chatVO.setCreateTime(LocalDateTime.now());

        assembler.assembleLite(chatVO, null, null, null);

        assertEquals(0, chatVO.getUnreadCount());
        assertEquals(0, chatVO.getOnLineMemberCount());
        assertNull(chatVO.getChatMembers());
        assertEquals(chatVO.getCreateTime(), chatVO.getLastActiveAt());
    }

    /**
     * 精简组装 - 无效JSON资源ID返回空图片列表
     */
    @Test
    void assembleHandlesInvalidJson() {
        ChatAssembler assembler = new ChatAssembler(minioOSSOperator, assetService, new ObjectMapper(),
                chatMemberAssembler);

        ChatVO chatVO = new ChatVO();
        MessageVO lastMsg = new MessageVO();
        lastMsg.setAssetIds("bad-json");
        lastMsg.setCreateTime(LocalDateTime.now());

        assembler.assembleLite(chatVO, lastMsg, 0, 0);

        assertNotNull(lastMsg.getImages());
        assertTrue(lastMsg.getImages().isEmpty());
        assertNull(lastMsg.getAssetIds());
    }

    /**
     * 公开化处理 - 清除敏感字段以供未加入用户查看
     */
    @Test
    void sanitizeForPublicClearsFields() {
        ChatAssembler assembler = new ChatAssembler(minioOSSOperator, assetService, new ObjectMapper(),
                chatMemberAssembler);

        ChatVO chatVO = new ChatVO();
        chatVO.setOwnerUid("u1");
        chatVO.setJoinEnabled(1);
        chatVO.setPasswordEnabled(1);
        chatVO.setLastActiveAt(LocalDateTime.now());
        chatVO.setCreateTime(LocalDateTime.now());
        chatVO.setUpdateTime(LocalDateTime.now());
        chatVO.setUnreadCount(1);
        chatVO.setLastMessage(new MessageVO());
        chatVO.setOnLineMemberCount(2);
        chatVO.setChatMembers(List.of(new ChatMemberVO()));

        assembler.sanitizeForPublic(chatVO);

        assertNull(chatVO.getOwnerUid());
        assertNull(chatVO.getJoinEnabled());
        assertNull(chatVO.getPasswordEnabled());
        assertNull(chatVO.getLastActiveAt());
        assertNull(chatVO.getCreateTime());
        assertNull(chatVO.getUpdateTime());
        assertNull(chatVO.getUnreadCount());
        assertNull(chatVO.getLastMessage());
        assertNull(chatVO.getOnLineMemberCount());
        assertNull(chatVO.getChatMembers());
    }
}

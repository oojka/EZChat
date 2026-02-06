package hal.th50743.service.impl;

import hal.th50743.assembler.ChatAssembler;
import hal.th50743.assembler.ChatMemberAssembler;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatInviteMapper;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.mapper.ChatMemberMapper;
import hal.th50743.mapper.MessageMapper;
import hal.th50743.mapper.UserMapper;
import hal.th50743.pojo.*;
import hal.th50743.service.AssetService;
import hal.th50743.service.ChatInviteService;
import hal.th50743.utils.PasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 聊天服务单元测试
 *
 * <p>覆盖以下场景:</p>
 * <ul>
 *     <li>聊天列表获取 (getChatVOListAndMemberStatusListLite, getChat, getChatMemberVOList)</li>
 *     <li>加入验证 (validateChatJoin): 邀请码校验、密码校验、各种异常情况</li>
 *     <li>加入聊天 (join): 邀请码模式、密码模式、系统消息生成</li>
 *     <li>创建聊天 (createChat): 密码设置、邀请码生成、重复键重试</li>
 *     <li>退出/解散 (leaveChat, disbandChat): 房主权限、成员清理</li>
 *     <li>聊天管理 (updateChatPassword, updateChatBasicInfo, kickMembers, transferOwner)</li>
 *     <li>私聊创建 (createPrivateChat): 已存在复用、新建逻辑</li>
 * </ul>
 *
 * @see ChatServiceImpl
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatMapper chatMapper;

    @Mock
    private ChatMemberMapper chatMemberMapper;

    @Mock
    private ChatInviteMapper chatInviteMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AssetService assetService;

    @Mock
    private ChatInviteService chatInviteService;

    @Mock
    private ChatAssembler chatAssembler;

    @Mock
    private ChatMemberAssembler chatMemberAssembler;

    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(
                chatMapper,
                chatMemberMapper,
                chatInviteMapper,
                messageMapper,
                userMapper,
                assetService,
                chatInviteService,
                chatAssembler,
                chatMemberAssembler
        );
    }

    // ================================
    // 聊天列表获取测试
    // ================================

    /**
     * 获取聊天列表(精简版) - 空列表返回空结果
     */
    @Test
    void getChatVOListAndMemberStatusListLiteEmptyReturnsEmpty() {
        when(chatMapper.selectChatVOListByUserId(1)).thenReturn(List.of());

        AppInitVO result = chatService.getChatVOListAndMemberStatusListLite(1);

        assertEquals(0, result.getChatList().size());
        assertEquals(0, result.getUserStatusList().size());
    }

    /**
     * 获取聊天列表(精简版) - 构建用户状态并组装精简视图
     */
    @Test
    void getChatVOListAndMemberStatusListLiteBuildsStatusAndAssemblesLite() {
        ChatVO chatVO = new ChatVO();
        chatVO.setChatCode("12345678");
        when(chatMapper.selectChatVOListByUserId(1)).thenReturn(List.of(chatVO));

        ChatMemberLite member1 = new ChatMemberLite("12345678", 10, "u10", LocalDateTime.now());
        ChatMemberLite member2 = new ChatMemberLite("12345678", 11, "u11", LocalDateTime.now());
        when(chatMemberMapper.selectChatMemberLiteListByUserId(1)).thenReturn(List.of(member1, member2));
        when(chatMemberAssembler.isUserOnline(eq(10), any())).thenReturn(true);
        when(chatMemberAssembler.isUserOnline(eq(11), any())).thenReturn(false);

        Map<String, Object> unreadRow = new HashMap<>();
        unreadRow.put("chatCode", "12345678");
        unreadRow.put("unreadCount", 3);
        when(messageMapper.selectUnreadCountMapByUserId(1)).thenReturn(List.of(unreadRow));

        MessageVO lastMsg = new MessageVO();
        Map<String, MessageVO> lastMap = new HashMap<>();
        lastMap.put("12345678", lastMsg);
        when(messageMapper.selectLastMessageListByUserId(1)).thenReturn(lastMap);

        AppInitVO result = chatService.getChatVOListAndMemberStatusListLite(1);

        assertEquals(1, result.getChatList().size());
        assertEquals(2, result.getUserStatusList().size());
        verify(chatAssembler).assembleLite(eq(chatVO), eq(lastMsg), eq(3), eq(1));
    }

    /**
     * 获取单个聊天 - 私聊时使用对方昵称和头像作为聊天名称
     */
    @Test
    void getChatSetsPrivateChatNameAndAvatar() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(1, 1)).thenReturn(true);

        ChatVO chatVO = new ChatVO();
        chatVO.setType(1);
        chatVO.setChatName("old");
        ChatMemberVO other = new ChatMemberVO();
        other.setUid("u2");
        other.setNickname("other");
        other.setAvatar(new Image("asset.png", "url", "thumb", 5));
        chatVO.setChatMembers(List.of(other));
        when(chatMapper.selectChatVOByChatId(1)).thenReturn(chatVO);

        when(chatMemberMapper.selectChatMemberListByChatId(1)).thenReturn(List.of());
        when(messageMapper.selectLastMessageByChatId(1)).thenReturn(null);
        when(messageMapper.selectUnreadCountMapByUserIdAndChatId(1, 1)).thenReturn(0);

        User currentUser = new User();
        currentUser.setUid("u1");
        when(userMapper.selectUserById(1)).thenReturn(currentUser);

        ChatVO result = chatService.getChat(1, "12345678");

        assertEquals("other", result.getChatName());
        assertNotNull(result.getAvatar());
    }

    /**
     * 获取聊天成员列表 - 返回组装器结果
     */
    @Test
    void getChatMemberVOListReturnsAssemblerResult() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(1, 1)).thenReturn(true);
        ChatMember member = new ChatMember();
        when(chatMemberMapper.selectChatMemberListByChatId(1)).thenReturn(List.of(member));
        List<ChatMemberVO> resultList = List.of(new ChatMemberVO());
        when(chatMemberAssembler.toChatMemberVOList(eq(List.of(member)), any())).thenReturn(resultList);

        List<ChatMemberVO> result = chatService.getChatMemberVOList(1, "12345678");

        assertEquals(resultList, result);
    }

    /**
     * 获取聊天ID - 聊天不存在时抛出 CHAT_NOT_FOUND 异常
     */
    @Test
    void getChatIdNotFoundThrows() {
        when(chatMapper.selectChatIdByChatCode("missing")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.getChatId(1, "missing"));

        assertEquals(ErrorCode.CHAT_NOT_FOUND.getCode(), ex.getCode());
    }

    /**
     * 获取聊天ID - 用户非成员时抛出 NOT_A_MEMBER 异常
     */
    @Test
    void getChatIdNotMemberThrows() {
        when(chatMapper.selectChatIdByChatCode("123")).thenReturn(1);
        when(chatMapper.isValidChatId(1, 1)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.getChatId(1, "123"));

        assertEquals(ErrorCode.NOT_A_MEMBER.getCode(), ex.getCode());
    }

    // ================================
    // 加入验证测试 (validateChatJoin)
    // ================================

    /**
     * 加入验证 - 邀请码无效时抛出 INVITE_CODE_INVALID 异常
     */
    @Test
    void validateChatJoinInviteInvalidThrows() {
        ValidateChatJoinReq req = new ValidateChatJoinReq(null, null, "invite");
        when(chatInviteMapper.selectByCodeHash(anyString())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.validateChatJoin(req));

        assertEquals(ErrorCode.INVITE_CODE_INVALID.getCode(), ex.getCode());
    }

    /**
     * 加入验证 - 邀请码已过期时抛出 INVITE_CODE_EXPIRED 异常
     */
    @Test
    void validateChatJoinInviteExpiredThrows() {
        ValidateChatJoinReq req = new ValidateChatJoinReq(null, null, "invite");
        ChatInvite invite = new ChatInvite();
        invite.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(chatInviteMapper.selectByCodeHash(anyString())).thenReturn(invite);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.validateChatJoin(req));

        assertEquals(ErrorCode.INVITE_CODE_EXPIRED.getCode(), ex.getCode());
    }

    /**
     * 加入验证 - 邀请码已撤销时抛出 INVITE_CODE_REVOKED 异常
     */
    @Test
    void validateChatJoinInviteRevokedThrows() {
        ValidateChatJoinReq req = new ValidateChatJoinReq(null, null, "invite");
        ChatInvite invite = new ChatInvite();
        invite.setRevoked(1);
        when(chatInviteMapper.selectByCodeHash(anyString())).thenReturn(invite);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.validateChatJoin(req));

        assertEquals(ErrorCode.INVITE_CODE_REVOKED.getCode(), ex.getCode());
    }

    /**
     * 加入验证 - 邀请码使用次数达上限时抛出 INVITE_CODE_USAGE_LIMIT_REACHED 异常
     */
    @Test
    void validateChatJoinInviteUsageLimitThrows() {
        ValidateChatJoinReq req = new ValidateChatJoinReq(null, null, "invite");
        ChatInvite invite = new ChatInvite();
        invite.setMaxUses(1);
        invite.setUsedCount(1);
        when(chatInviteMapper.selectByCodeHash(anyString())).thenReturn(invite);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.validateChatJoin(req));

        assertEquals(ErrorCode.INVITE_CODE_USAGE_LIMIT_REACHED.getCode(), ex.getCode());
    }

    /**
     * 加入验证 - 密码模式下未提供密码时抛出 PASSWORD_REQUIRED 异常
     */
    @Test
    void validateChatJoinPasswordMissingThrows() {
        ValidateChatJoinReq req = new ValidateChatJoinReq("12345678", null, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.validateChatJoin(req));

        assertEquals(ErrorCode.PASSWORD_REQUIRED.getCode(), ex.getCode());
    }

    /**
     * 加入验证 - 请求参数无效时抛出 BAD_REQUEST 异常
     */
    @Test
    void validateChatJoinBadRequestThrows() {
        ValidateChatJoinReq req = new ValidateChatJoinReq();

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.validateChatJoin(req));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /**
     * 加入验证 - 聊天室不存在时抛出 CHAT_NOT_FOUND 异常
     */
    @Test
    void validateChatJoinChatNotFoundThrows() {
        ValidateChatJoinReq req = new ValidateChatJoinReq("12345678", "pw", null);
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.validateChatJoin(req));

        assertEquals(ErrorCode.CHAT_NOT_FOUND.getCode(), ex.getCode());
    }

    /**
     * 加入验证 - 聊天室禁止加入时抛出 FORBIDDEN 异常
     */
    @Test
    void validateChatJoinDisabledThrows() {
        ValidateChatJoinReq req = new ValidateChatJoinReq("12345678", "pw", null);
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        ChatJoinInfo info = new ChatJoinInfo();
        info.setChatId(1);
        info.setJoinEnabled(0);
        when(chatMapper.selectJoinInfoByChatId(1)).thenReturn(info);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.validateChatJoin(req));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    /**
     * 加入验证 - 聊天室未设置密码时抛出 FORBIDDEN 异常
     */
    @Test
    void validateChatJoinPasswordDisabledThrows() {
        ValidateChatJoinReq req = new ValidateChatJoinReq("12345678", "pw", null);
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        ChatJoinInfo info = new ChatJoinInfo();
        info.setChatId(1);
        info.setJoinEnabled(1);
        info.setChatPasswordHash(null);
        when(chatMapper.selectJoinInfoByChatId(1)).thenReturn(info);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.validateChatJoin(req));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    /**
     * 加入验证 - 密码不匹配时抛出 BAD_REQUEST 异常
     */
    @Test
    void validateChatJoinPasswordMismatchThrows() {
        ValidateChatJoinReq req = new ValidateChatJoinReq("12345678", "pw2", null);
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        ChatJoinInfo info = new ChatJoinInfo();
        info.setChatId(1);
        info.setJoinEnabled(1);
        info.setChatPasswordHash(PasswordUtils.encode("pw1"));
        when(chatMapper.selectJoinInfoByChatId(1)).thenReturn(info);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.validateChatJoin(req));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /**
     * 加入验证成功 - 返回聊天室信息并进行公开化处理
     */
    @Test
    void validateChatJoinSuccessReturnsChatVO() {
        ValidateChatJoinReq req = new ValidateChatJoinReq("12345678", "pw", null);
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        ChatJoinInfo info = new ChatJoinInfo();
        info.setChatId(1);
        info.setJoinEnabled(1);
        info.setChatPasswordHash(PasswordUtils.encode("pw"));
        when(chatMapper.selectJoinInfoByChatId(1)).thenReturn(info);
        ChatVO chatVO = new ChatVO();
        when(chatMapper.selectChatVOByChatId(1)).thenReturn(chatVO);

        ChatVO result = chatService.validateChatJoin(req);

        assertEquals(chatVO, result);
        verify(chatAssembler).assembleLite(eq(chatVO), eq(null), eq(null), eq(null));
        verify(chatAssembler).sanitizeForPublic(chatVO);
    }

    // ================================
    // 加入聊天测试 (join)
    // ================================

    /**
     * 加入聊天 - 请求为空时抛出 BAD_REQUEST 异常
     */
    @Test
    void joinNullRequestThrows() {
        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.join(null));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /**
     * 加入聊天(邀请码模式) - 聊天室禁止加入时抛出 FORBIDDEN 异常
     */
    @Test
    void joinInviteJoinDisabledThrows() {
        JoinChatReq req = new JoinChatReq();
        req.setInviteCode("invite");
        req.setUserId(1);

        ChatInvite invite = new ChatInvite();
        invite.setChatId(2);
        invite.setCodeHash("hash");
        when(chatInviteMapper.selectByCodeHash(anyString())).thenReturn(invite);
        ChatJoinInfo info = new ChatJoinInfo();
        info.setChatId(2);
        info.setChatCode("12345678");
        info.setJoinEnabled(0);
        when(chatMapper.selectJoinInfoByChatId(2)).thenReturn(info);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.join(req));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    /**
     * 加入聊天(邀请码模式) - 成功加入后创建系统消息并消费邀请码
     */
    @Test
    void joinInviteSuccessCreatesSystemMessage() {
        JoinChatReq req = new JoinChatReq();
        req.setInviteCode("invite");
        req.setUserId(1);

        ChatInvite invite = new ChatInvite();
        invite.setChatId(2);
        invite.setCodeHash("hash");
        when(chatInviteMapper.selectByCodeHash(anyString())).thenReturn(invite);
        ChatJoinInfo info = new ChatJoinInfo();
        info.setChatId(2);
        info.setChatCode("12345678");
        info.setJoinEnabled(1);
        when(chatMapper.selectJoinInfoByChatId(2)).thenReturn(info);

        User user = new User();
        user.setId(1);
        user.setUid("u1");
        user.setNickname("nick");
        user.setUsername(null);
        when(userMapper.selectUserById(1)).thenReturn(user);

        when(messageMapper.selectCurrentSequence(2)).thenReturn(5L);
        when(chatMemberMapper.selectChatMemberListByChatId(2)).thenReturn(List.of(new ChatMember("u1", 2, "12345678",
                1, "nick", null, LocalDateTime.now(), null, null, null)));

        JoinBroadcastVO broadcastVO = new JoinBroadcastVO();
        when(chatMemberAssembler.toJoinBroadcastVO(any(User.class), eq("12345678"), anyString(), any()))
                .thenReturn(broadcastVO);

        Chat result = chatService.join(req);

        assertEquals(Integer.valueOf(2), result.getId());
        assertEquals("12345678", result.getChatCode());
        verify(chatInviteService).consumeInvite(2, "hash");
        verify(messageMapper).insertMessage(any(Message.class));
    }

    /**
     * 加入聊天(密码模式) - 聊天室不存在时抛出 CHAT_NOT_FOUND 异常
     */
    @Test
    void joinPasswordChatNotFoundThrows() {
        JoinChatReq req = new JoinChatReq();
        req.setChatCode("12345678");
        req.setPassword("pw");
        req.setUserId(1);
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.join(req));

        assertEquals(ErrorCode.CHAT_NOT_FOUND.getCode(), ex.getCode());
    }

    /**
     * 加入聊天(密码模式) - 聊天室未设置密码时抛出 FORBIDDEN 异常
     */
    @Test
    void joinPasswordMissingHashThrows() {
        JoinChatReq req = new JoinChatReq();
        req.setChatCode("12345678");
        req.setPassword("pw");
        req.setUserId(1);
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(2);
        ChatJoinInfo info = new ChatJoinInfo();
        info.setChatId(2);
        info.setChatPasswordHash(null);
        when(chatMapper.selectJoinInfoByChatId(2)).thenReturn(info);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.join(req));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    /**
     * 加入聊天(密码模式) - 密码不匹配时抛出 BAD_REQUEST 异常
     */
    @Test
    void joinPasswordMismatchThrows() {
        JoinChatReq req = new JoinChatReq();
        req.setChatCode("12345678");
        req.setPassword("pw2");
        req.setUserId(1);
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(2);
        ChatJoinInfo info = new ChatJoinInfo();
        info.setChatId(2);
        info.setChatPasswordHash(PasswordUtils.encode("pw1"));
        when(chatMapper.selectJoinInfoByChatId(2)).thenReturn(info);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.join(req));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ================================
    // 创建聊天测试 (createChat)
    // ================================

    /**
     * 创建聊天 - 成功创建后返回邀请码并设置密码哈希
     */
    @Test
    void createChatSuccessReturnsInvite() {
        ChatReq req = new ChatReq();
        req.setChatName("room");
        req.setJoinEnable(1);
        req.setPassword("pw");
        ChatInviteVO invite = new ChatInviteVO(1, "invite", null, 0, 0, LocalDateTime.now());
        when(chatInviteService.createInviteForChatId(eq(1), eq(10), any(), any())).thenReturn(invite);
        doAnswer(invocation -> {
            ChatCreate chatCreate = invocation.getArgument(0);
            chatCreate.setId(10);
            return null;
        }).when(chatMapper).insertChat(any(ChatCreate.class));

        CreateChatVO result = chatService.createChat(1, req);

        assertNotNull(result.chatCode());
        assertEquals("invite", result.inviteCode());
        verify(chatMemberMapper).insertChatMember(eq(10), eq(1), any(LocalDateTime.class));
        ArgumentCaptor<ChatCreate> captor = ArgumentCaptor.forClass(ChatCreate.class);
        verify(chatMapper).insertChat(captor.capture());
        assertNotNull(captor.getValue().getChatPasswordHash());
    }

    /**
     * 创建聊天 - 重复键冲突达到重试上限时抛出 DATABASE_ERROR 异常
     */
    @Test
    void createChatDuplicateKeyThrowsAfterRetries() {
        ChatReq req = new ChatReq();
        req.setChatName("room");
        doThrow(new DuplicateKeyException("dup")).when(chatMapper).insertChat(any(ChatCreate.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.createChat(1, req));

        assertEquals(ErrorCode.DATABASE_ERROR.getCode(), ex.getCode());
    }

    // ================================
    // 退出聊天测试 (leaveChat)
    // ================================

    /**
     * 退出聊天 - 房主在有其他成员时退出抛出 BAD_REQUEST 异常
     */
    @Test
    void leaveChatOwnerWithMembersThrows() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(1, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);
        when(chatMemberMapper.countMembersByChatId(1)).thenReturn(2);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.leaveChat(1, "12345678"));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /**
     * 退出聊天 - 房主独自一人时退出自动解散聊天室
     */
    @Test
    void leaveChatOwnerSingleDisbands() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(1, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);
        when(chatMemberMapper.countMembersByChatId(1)).thenReturn(1);

        chatService.leaveChat(1, "12345678");

        verify(chatMemberMapper).deleteChatMembersByChatId(1);
        verify(chatMapper).deleteChatById(1);
    }

    /**
     * 退出聊天 - 非房主退出仅删除自己的成员记录
     */
    @Test
    void leaveChatNonOwnerDeletesMember() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(2, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);

        chatService.leaveChat(2, "12345678");

        verify(chatMemberMapper).deleteChatMember(1, 2);
    }

    // ================================
    // 解散聊天测试 (disbandChat)
    // ================================

    /**
     * 解散聊天 - 非房主尝试解散时抛出 FORBIDDEN 异常
     */
    @Test
    void disbandChatForbiddenForNonOwner() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(2, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.disbandChat(2, "12345678"));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    /**
     * 解散聊天 - 房主成功解散聊天室并删除所有成员
     */
    @Test
    void disbandChatDeletes() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(1, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);

        chatService.disbandChat(1, "12345678");

        verify(chatMemberMapper).deleteChatMembersByChatId(1);
        verify(chatMapper).deleteChatById(1);
    }

    // ================================
    // 更新聊天密码测试 (updateChatPassword)
    // ================================

    /**
     * 更新聊天密码 - 非房主尝试更新时抛出 FORBIDDEN 异常
     */
    @Test
    void updateChatPasswordForbidden() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(2, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);

        ChatPasswordUpdateReq req = new ChatPasswordUpdateReq();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.updateChatPassword(2, "12345678", req));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    /**
     * 更新聊天密码 - 传入空密码时清除密码哈希
     */
    @Test
    void updateChatPasswordClearsHash() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(1, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);

        ChatPasswordUpdateReq req = new ChatPasswordUpdateReq();
        req.setPassword("");

        chatService.updateChatPassword(1, "12345678", req);

        verify(chatMapper).updateChatPassword(1, null);
    }

    // ================================
    // 更新聊天基本信息测试 (updateChatBasicInfo)
    // ================================

    /**
     * 更新聊天基本信息 - 非房主尝试更新时抛出 FORBIDDEN 异常
     */
    @Test
    void updateChatBasicInfoForbidden() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(2, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);

        ChatBasicUpdateReq req = new ChatBasicUpdateReq();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.updateChatBasicInfo(2, "12345678", req));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    /**
     * 更新聊天基本信息 - 成功更新名称、人数上限、公告和头像
     */
    @Test
    void updateChatBasicInfoUpdatesAndReturnsChat() {
        ChatServiceImpl spyService = spy(chatService);
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(1, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);
        ChatVO chatVO = new ChatVO();
        doAnswer(invocation -> chatVO).when(spyService).getChat(1, "12345678");

        ChatBasicUpdateReq req = new ChatBasicUpdateReq();
        req.setChatName("room");
        req.setMaxMembers(100);
        req.setAnnouncement("hi");
        req.setAvatar(new Image("a.png", null, null, 9));

        ChatVO result = spyService.updateChatBasicInfo(1, "12345678", req);

        assertEquals(chatVO, result);
        verify(chatMapper).updateChatBasicInfo(1, "room", 100, "hi", 9);
    }

    // ================================
    // 踢出成员测试 (kickMembers)
    // ================================

    /**
     * 踢出成员 - 非房主尝试踢人时抛出 FORBIDDEN 异常
     */
    @Test
    void kickMembersForbidden() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(2, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);

        ChatKickReq req = new ChatKickReq(List.of("u1"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.kickMembers(2, "12345678", req));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    /**
     * 踢出成员 - 房主成功踢出指定用户列表
     */
    @Test
    void kickMembersRemovesUsers() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(1, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);
        when(userMapper.selectIdByUid("u2")).thenReturn(2);
        when(userMapper.selectIdByUid("u3")).thenReturn(3);

        ChatKickReq req = new ChatKickReq(List.of("u2", "u3"));
        chatService.kickMembers(1, "12345678", req);

        verify(chatMemberMapper).deleteChatMembersByChatIdAndUserIds(1, List.of(2, 3));
    }

    // ================================
    // 转让房主测试 (transferOwner)
    // ================================

    /**
     * 转让房主 - 非房主尝试转让时抛出 FORBIDDEN 异常
     */
    @Test
    void transferOwnerForbidden() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(2, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);

        ChatOwnerTransferReq req = new ChatOwnerTransferReq("u2");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.transferOwner(2, "12345678", req));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    /**
     * 转让房主 - 房主成功将所有权转让给指定用户
     */
    @Test
    void transferOwnerUpdatesOwner() {
        when(chatMapper.selectChatIdByChatCode("12345678")).thenReturn(1);
        when(chatMapper.isValidChatId(1, 1)).thenReturn(true);
        when(chatMapper.selectOwnerIdByChatId(1)).thenReturn(1);
        when(userMapper.selectIdByUid("u2")).thenReturn(2);

        ChatOwnerTransferReq req = new ChatOwnerTransferReq("u2");
        chatService.transferOwner(1, "12345678", req);

        verify(chatMapper).updateChatOwner(1, 2);
    }

    // ================================
    // 创建私聊测试 (createPrivateChat)
    // ================================

    /**
     * 创建私聊 - 已存在私聊时直接返回现有聊天码
     */
    @Test
    void createPrivateChatReturnsExisting() {
        when(chatMapper.selectPrivateChatCodeBetweenUsers(1, 2)).thenReturn("12345678");

        String result = chatService.createPrivateChat(1, 2);

        assertEquals("12345678", result);
        verify(chatMapper, never()).insertChat(any(ChatCreate.class));
    }

    /**
     * 创建私聊 - 成功创建新私聊并添加双方为成员
     */
    @Test
    void createPrivateChatSuccess() {
        when(chatMapper.selectPrivateChatCodeBetweenUsers(1, 2)).thenReturn(null);
        doAnswer(invocation -> {
            ChatCreate chatCreate = invocation.getArgument(0);
            chatCreate.setId(10);
            return null;
        }).when(chatMapper).insertChat(any(ChatCreate.class));

        String result = chatService.createPrivateChat(1, 2);

        assertNotNull(result);
        assertEquals(8, result.length());
        assertTrue(result.matches("\\d{8}"));
        verify(chatMemberMapper).insertChatMember(eq(10), eq(1), any(LocalDateTime.class));
        verify(chatMemberMapper).insertChatMember(eq(10), eq(2), any(LocalDateTime.class));
    }

    /**
     * 创建私聊 - 重复键冲突达到重试上限时抛出 DATABASE_ERROR 异常
     */
    @Test
    void createPrivateChatDuplicateKeyThrowsAfterRetries() {
        when(chatMapper.selectPrivateChatCodeBetweenUsers(1, 2)).thenReturn(null);
        doThrow(new DuplicateKeyException("dup")).when(chatMapper).insertChat(any(ChatCreate.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> chatService.createPrivateChat(1, 2));

        assertEquals(ErrorCode.DATABASE_ERROR.getCode(), ex.getCode());
    }
}

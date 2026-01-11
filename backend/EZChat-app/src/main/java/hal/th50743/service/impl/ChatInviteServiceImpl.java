package hal.th50743.service.impl;

import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatInviteMapper;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.pojo.ChatInvite;
import hal.th50743.pojo.ChatInviteCreateReq;
import hal.th50743.pojo.ChatInviteVO;
import hal.th50743.service.ChatInviteService;
import hal.th50743.utils.InviteCodeUtils;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邀请链接管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInviteServiceImpl implements ChatInviteService {

    private static final int DEFAULT_EXPIRY_MINUTES = 10080;
    private static final int MAX_ACTIVE_INVITES = 5;

    private final ChatInviteMapper chatInviteMapper;
    private final ChatMapper chatMapper;

    /**
     * 查询聊天室有效邀请链接列表
     *
     * @param userId 当前用户ID
     * @param chatCode 聊天室代码
     * @return 有效邀请链接列表
     */
    @Override
    public List<ChatInviteVO> listActiveInvites(Integer userId, String chatCode) {
        Integer chatId = resolveChatId(chatCode);
        assertOwner(userId, chatId, chatCode);

        List<ChatInvite> invites = chatInviteMapper.selectActiveInvitesByChatId(chatId);
        if (invites == null || invites.isEmpty()) {
            return Collections.emptyList();
        }
        return invites.stream().map(this::toInviteVO).collect(Collectors.toList());
    }

    /**
     * 创建新的邀请链接
     *
     * @param userId 当前用户ID
     * @param chatCode 聊天室代码
     * @param req 创建请求
     * @return 新创建的邀请链接信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatInviteVO createInvite(Integer userId, String chatCode, ChatInviteCreateReq req) {
        Integer chatId = resolveChatId(chatCode);
        assertOwner(userId, chatId, chatCode);

        int activeCount = chatInviteMapper.countActiveInvitesByChatId(chatId);
        if (activeCount >= MAX_ACTIVE_INVITES) {
            log.warn("[Invite Create] Active invite limit reached: chatId={}, count={}", chatId, activeCount);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite limit reached");
        }

        Integer expiryMinutes = req == null ? null : req.getJoinLinkExpiryMinutes();
        Integer maxUses = req == null ? null : req.getMaxUses();
        ChatInvite invite = buildInvite(userId, chatId, expiryMinutes, maxUses);

        int rows = chatInviteMapper.insertChatInvite(invite);
        if (rows <= 0 || invite.getId() == null) {
            log.error("[Invite Create] Insert failed: chatId={}, userId={}", chatId, userId);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "Create invite failed");
        }

        log.info("[Invite Create] Success: chatId={}, inviteId={}, userId={}", chatId, invite.getId(), userId);
        return toInviteVO(invite);
    }

    /**
     * 撤销邀请链接
     *
     * @param userId 当前用户ID
     * @param chatCode 聊天室代码
     * @param inviteId 邀请码ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeInvite(Integer userId, String chatCode, Integer inviteId) {
        if (inviteId == null) {
            log.warn("[Invite Revoke] InviteId missing: userId={}, chatCode={}", userId, chatCode);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "inviteId is required");
        }
        Integer chatId = resolveChatId(chatCode);
        assertOwner(userId, chatId, chatCode);

        int rows = chatInviteMapper.revokeById(chatId, inviteId);
        if (rows <= 0) {
            log.warn("[Invite Revoke] Not found or already revoked: chatId={}, inviteId={}", chatId, inviteId);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite not found or already revoked");
        }

        log.info("[Invite Revoke] Success: chatId={}, inviteId={}, userId={}", chatId, inviteId, userId);
    }

    /**
     * 创建邀请链接（内部复用）
     *
     * @param userId 创建者用户ID
     * @param chatId 聊天室ID
     * @param expiryMinutes 过期分钟数
     * @param maxUses 最大使用次数
     * @return 新创建的邀请链接信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatInviteVO createInviteForChatId(Integer userId, Integer chatId, Integer expiryMinutes, Integer maxUses) {
        if (userId == null) {
            log.warn("[Invite Create] UserId missing: chatId={}", chatId);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User is required");
        }
        if (chatId == null) {
            log.error("[Invite Create] ChatId missing: userId={}", userId);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chatId is required");
        }
        int activeCount = chatInviteMapper.countActiveInvitesByChatId(chatId);
        if (activeCount >= MAX_ACTIVE_INVITES) {
            log.warn("[Invite Create] Active invite limit reached: chatId={}, count={}", chatId, activeCount);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite limit reached");
        }

        ChatInvite invite = buildInvite(userId, chatId, expiryMinutes, maxUses);
        int rows = chatInviteMapper.insertChatInvite(invite);
        if (rows <= 0 || invite.getId() == null) {
            log.error("[Invite Create] Insert failed: chatId={}, userId={}", chatId, userId);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "Create invite failed");
        }

        log.info("[Invite Create] Success: chatId={}, inviteId={}, userId={}", chatId, invite.getId(), userId);
        return toInviteVO(invite);
    }

    /**
     * 校验群主权限
     */
    private void assertOwner(Integer userId, Integer chatId, String chatCode) {
        if (userId == null) {
            log.warn("[Invite Auth] UserId missing: chatCode={}", chatCode);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User is required");
        }
        Integer ownerId = chatMapper.selectOwnerIdByChatId(chatId);
        if (ownerId == null) {
            log.error("[Invite Auth] Chat owner not found: chatId={}", chatId);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Chat owner not found");
        }
        if (!Objects.equals(ownerId, userId)) {
            log.warn("[Invite Auth] Permission denied: chatId={}, userId={}", chatId, userId);
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only owner can manage invites");
        }
    }

    /**
     * 解析聊天室ID
     */
    private Integer resolveChatId(String chatCode) {
        if (chatCode == null || chatCode.isBlank()) {
            log.warn("[Invite Resolve] Chat code missing: chatCode={}", chatCode);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Chat code is required");
        }
        Integer chatId = chatMapper.selectChatIdByChatCode(chatCode);
        if (chatId == null) {
            log.warn("[Invite Resolve] Chat not found: chatCode={}", chatCode);
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND, "Chat not found");
        }
        return chatId;
    }

    /**
     * 构建邀请码记录
     */
    private ChatInvite buildInvite(Integer userId, Integer chatId, Integer expiryMinutes, Integer maxUses) {
        int normalizedExpiry = normalizeExpiryMinutes(expiryMinutes);
        int normalizedMaxUses = normalizeMaxUses(maxUses);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(Math.max(1, normalizedExpiry));
        String inviteCode = InviteCodeUtils.generateInviteCode(18);
        String codeHash = InviteCodeUtils.sha256Hex(inviteCode);

        return new ChatInvite(
                null,
                chatId,
                inviteCode,
                codeHash,
                expiresAt,
                normalizedMaxUses,
                0,
                0,
                userId,
                now,
                now
        );
    }

    /**
     * 过期时间归一化
     */
    private int normalizeExpiryMinutes(Integer expiryMinutes) {
        int value = expiryMinutes == null ? DEFAULT_EXPIRY_MINUTES : expiryMinutes;
        if (value <= 0) {
            log.warn("[Invite Validate] Invalid expiryMinutes: {}", value);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "expiryMinutes must be positive");
        }
        return value;
    }

    /**
     * 使用次数归一化
     */
    private int normalizeMaxUses(Integer maxUses) {
        int value = maxUses == null ? 0 : maxUses;
        if (value != 0 && value != 1) {
            log.warn("[Invite Validate] Invalid maxUses: {}", value);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "maxUses must be 0 or 1");
        }
        return value;
    }

    /**
     * 映射为 VO
     */
    private ChatInviteVO toInviteVO(ChatInvite invite) {
        return new ChatInviteVO(
                invite.getId(),
                invite.getInviteCode(),
                invite.getExpiresAt(),
                invite.getMaxUses(),
                invite.getUsedCount(),
                invite.getCreateTime()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumeInvite(Integer chatId, String codeHash) {
        int rows = chatInviteMapper.consume(chatId, codeHash);
        if (rows <= 0) {
            log.warn("[Invite Consume] Failed: chatId={}, hash={}", chatId, codeHash);
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID, "Invite code invalid or expired");
        }
    }
}

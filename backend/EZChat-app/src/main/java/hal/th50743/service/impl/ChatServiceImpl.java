package hal.th50743.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.mapper.ChatMemberMapper;
import hal.th50743.mapper.ChatInviteMapper;
import hal.th50743.mapper.MessageMapper;
import hal.th50743.pojo.*;
import hal.th50743.pojo.FileEntity;
import hal.th50743.service.ChatService;
import hal.th50743.service.FileService;
import hal.th50743.service.UserService;
import hal.th50743.utils.ImageUtils;
import hal.th50743.utils.InviteCodeUtils;
import hal.th50743.utils.PasswordUtils;
import hal.th50743.utils.UidGenerator;
import hal.th50743.ws.WebSocketServer;
import io.minio.MinioOSSOperator;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天服务实现类
 * <p>
 * 负责处理聊天室初始化、成员管理及详情获取逻辑。
 * 实现了 ChatService 接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMapper chatMapper;
    private final ChatMemberMapper chatMemberMapper;
    private final ChatInviteMapper chatInviteMapper;
    private final MessageMapper messageMapper;
    private final MinioOSSOperator minioOSSOperator;
    private final UserService userService;
    private final FileService fileService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============================================================
    // 1. 核心组装逻辑 (VO Assembly)
    // ============================================================

    /**
     * 核心复用方法：组装单个 ChatVO 的公共数据
     * <p>
     * 涵盖：头像 URL 转换、最后消息填充、未读数挂载、在线成员统计及脱敏。
     *
     * @param c           聊天室视图对象
     * @param onlineUsers 在线用户 Session 映射
     * @param lastMsg     最后一条消息
     * @param unreadCount 未读消息数
     * @param members     聊天室成员列表
     */
    private void assembleChatVO(ChatVO c,
                                Map<Integer, Session> onlineUsers,
                                MessageVO lastMsg,
                                Integer unreadCount,
                                List<ChatMember> members) {
        // A. 头像处理
        if (c.getAvatarObjectName() != null) {
            c.setAvatar(ImageUtils.buildImage(c.getAvatarObjectName(), minioOSSOperator));
            c.setAvatarObjectName(null);
        }

        // B. 最后消息处理
        if (lastMsg != null) {
            // 根据 objectIds 构建 Image 对象列表
            String objectIdsJson = lastMsg.getObjectIds();
            if (objectIdsJson != null && !objectIdsJson.isEmpty()) {
                try {
                    List<Integer> objectIds = objectMapper.readValue(objectIdsJson, new TypeReference<List<Integer>>() {});
                    List<Image> images = new ArrayList<>();
                    for (Integer objectId : objectIds) {
                        FileEntity objectEntity = fileService.findById(objectId);
                        if (objectEntity != null) {
                            Image image = ImageUtils.buildImage(objectEntity.getObjectName(), minioOSSOperator);
                            if (image != null) {
                                image.setObjectId(objectId);
                                images.add(image);
                            }
                        }
                    }
                    lastMsg.setImages(images);
                } catch (JsonProcessingException e) {
                    log.error("反序列化最后消息的图片对象ID列表失败: {}", objectIdsJson, e);
                    lastMsg.setImages(Collections.emptyList());
                }
            }
            lastMsg.setObjectIds(null);
            c.setLastMessage(lastMsg);
            c.setLastActiveAt(lastMsg.getCreateTime());
        } else {
            c.setLastActiveAt(c.getCreateTime());
        }

        // C. 未读数挂载
        c.setUnreadCount(unreadCount != null ? unreadCount : 0);

        // D. 成员处理与在线人数统计
        if (members != null) {
            List<ChatMemberVO> memberVOList = new ArrayList<>();
            int onlineCount = 0;

            for (ChatMember m : members) {
                boolean isOnline = m.getUserId() != null && onlineUsers.containsKey(m.getUserId());
                if (isOnline) onlineCount++;

                ChatMemberVO vo = new ChatMemberVO();
                vo.setUid(m.getUid());
                vo.setNickname(m.getNickname());
                vo.setOnline(isOnline);
                vo.setLastSeenAt(m.getLastSeenAt());
                
                if (m.getAvatarObjectName() != null) {
                    vo.setAvatar(ImageUtils.buildImage(m.getAvatarObjectName(), minioOSSOperator));
                }
                
                memberVOList.add(vo);
            }

            c.setOnLineMemberCount(onlineCount);
            c.setChatMembers(memberVOList);
        }
    }

    /**
     * 将 ChatMember 列表转换为 ChatMemberVO 列表（包含头像 URL）
     *
     * 业务目的：
     * - 统一成员列表的输出格式（右侧成员栏/聊天室详情复用）
     * - 避免 Controller 层做对象拼装，保持 Controller → Service → Mapper 的职责清晰
     */
    private List<ChatMemberVO> toChatMemberVOList(List<ChatMember> members, Map<Integer, Session> onlineUsers) {
        if (members == null || members.isEmpty()) return Collections.emptyList();
        List<ChatMemberVO> memberVOList = new ArrayList<>();
        for (ChatMember m : members) {
            boolean isOnline = m.getUserId() != null && onlineUsers.containsKey(m.getUserId());
            ChatMemberVO vo = new ChatMemberVO();
            vo.setUid(m.getUid());
            vo.setNickname(m.getNickname());
            vo.setOnline(isOnline);
            vo.setLastSeenAt(m.getLastSeenAt());
            if (m.getAvatarObjectName() != null) {
                vo.setAvatar(ImageUtils.buildImage(m.getAvatarObjectName(), minioOSSOperator));
            }
            memberVOList.add(vo);
        }
        return memberVOList;
    }

    // ============================================================
    // 2. 业务初始化逻辑 (App Initialization)
    // ============================================================

    /**
     * 获取用户的聊天列表及成员在线状态
     *
     * @param userId 用户ID
     * @return AppInitVO 包含聊天列表和用户状态列表
     */
    @Override
    public AppInitVO getChatVOListAndMemberStatusList(Integer userId) {
        // 获取用户加入的所有房间列表
        List<ChatVO> chatVOList = chatMapper.getChatVOListByUserId(userId);
        if (chatVOList == null || chatVOList.isEmpty()) return null;

        // 预拉取全局数据：在线用户快照、全量成员记录、未读数 Map、最后消息 Map
        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
        List<ChatMember> allMembers = chatMemberMapper.getChatMemberListByUserId(userId);

        // 按 ChatCode 对成员进行分组，优化 O(1) 查找性能
        Map<String, List<ChatMember>> chatMemberMap = allMembers.stream()
                .collect(Collectors.groupingBy(ChatMember::getChatCode));

        // 聚合未读消息数
        Map<String, Integer> unreadCountMap = messageMapper.getUnreadCountMapByUserId(userId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("chatCode"),
                        row -> ((Number) row.get("unreadCount")).intValue(),
                        (v1, v2) -> v1));

        // 聚合各房间最后一条消息
        Map<String, MessageVO> lastMessageMap = messageMapper.getLastMessageListByUserId(userId);

        // 处理全局唯一用户状态列表 (用于前端头像在线状态点展示)
        Map<String, UserStatus> uniqueUserStatusMap = new HashMap<>();
        for (ChatMember m : allMembers) {
            m.setChatId(null);
            uniqueUserStatusMap.putIfAbsent(m.getUid(), new UserStatus(
                    m.getUid(), onlineUsers.containsKey(m.getUserId()), m.getLastSeenAt()));
        }

        // 遍历并调用组装逻辑
        for (ChatVO c : chatVOList) {
            assembleChatVO(c,
                    onlineUsers,
                    lastMessageMap.get(c.getChatCode()),
                    unreadCountMap.get(c.getChatCode()),
                    chatMemberMap.get(c.getChatCode()));
        }

        return new AppInitVO(chatVOList, new ArrayList<>(uniqueUserStatusMap.values()));
    }

    /**
     * 获取用户的聊天列表及成员在线状态（轻量版）
     *
     * 业务目的：
     * - refresh 初始化只优先渲染 chatList（AsideList）
     * - 不返回每个房间的 chatMembers，减少数据量与头像预取压力
     *
     * @param userId 用户ID
     * @return AppInitVO（chatList + userStatusList）
     */
    @Override
    public AppInitVO getChatVOListAndMemberStatusListLite(Integer userId) {
        // 1) chatList 基础字段（不含成员列表）
        List<ChatVO> chatVOList = chatMapper.getChatVOListByUserId(userId);
        if (chatVOList == null || chatVOList.isEmpty()) {
            return new AppInitVO(Collections.emptyList(), Collections.emptyList());
        }

        // 2) 轻量成员列表：用于聚合在线人数与 userStatusList（不含 nickname/avatar）
        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
        List<ChatMemberLite> liteMembers = chatMemberMapper.getChatMemberLiteListByUserId(userId);

        // 2.1) per-room 在线人数统计（chatCode -> onlineCount）
        Map<String, Integer> onlineCountMap = new HashMap<>();
        // 2.2) 全局唯一用户状态表（uid 去重）
        Map<String, UserStatus> uniqueUserStatusMap = new HashMap<>();

        for (ChatMemberLite m : liteMembers) {
            boolean isOnline = m.getUserId() != null && onlineUsers.containsKey(m.getUserId());
            onlineCountMap.merge(m.getChatCode(), isOnline ? 1 : 0, Integer::sum);
            uniqueUserStatusMap.putIfAbsent(
                    m.getUid(),
                    new UserStatus(m.getUid(), isOnline, m.getLastSeenAt())
            );
        }

        // 3) 遍历 chatList：补齐头像、最后消息、未读数与在线人数（但不填 chatMembers）
        Map<String, Integer> unreadCountMap = messageMapper.getUnreadCountMapByUserId(userId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("chatCode"),
                        row -> ((Number) row.get("unreadCount")).intValue(),
                        (v1, v2) -> v1));
        Map<String, MessageVO> lastMessageMap = messageMapper.getLastMessageListByUserId(userId);

        for (ChatVO c : chatVOList) {
            // 头像处理
            if (c.getAvatarObjectName() != null) {
                c.setAvatar(ImageUtils.buildImage(c.getAvatarObjectName(), minioOSSOperator));
                c.setAvatarObjectName(null);
            }

            // 最后消息处理
            MessageVO lastMsg = lastMessageMap.get(c.getChatCode());
            if (lastMsg != null) {
                // 根据 objectIds 构建 Image 对象列表
                String objectIdsJson = lastMsg.getObjectIds();
                if (objectIdsJson != null && !objectIdsJson.isEmpty()) {
                    try {
                        List<Integer> objectIds = objectMapper.readValue(objectIdsJson, new TypeReference<List<Integer>>() {});
                        List<Image> images = new ArrayList<>();
                        for (Integer objectId : objectIds) {
                            FileEntity objectEntity = fileService.findById(objectId);
                            if (objectEntity != null) {
                                Image image = ImageUtils.buildImage(objectEntity.getObjectName(), minioOSSOperator);
                                if (image != null) {
                                    image.setObjectId(objectId);
                                    images.add(image);
                                }
                            }
                        }
                        lastMsg.setImages(images);
                    } catch (JsonProcessingException e) {
                        log.error("反序列化最后消息的图片对象ID列表失败: {}", objectIdsJson, e);
                        lastMsg.setImages(Collections.emptyList());
                    }
                }
                lastMsg.setObjectIds(null);
                c.setLastMessage(lastMsg);
                c.setLastActiveAt(lastMsg.getCreateTime());
            } else {
                c.setLastActiveAt(c.getCreateTime());
            }

            // 未读数
            c.setUnreadCount(unreadCountMap.getOrDefault(c.getChatCode(), 0));
            // 在线人数（初始化阶段只提供计数，不提供 chatMembers）
            c.setOnLineMemberCount(onlineCountMap.getOrDefault(c.getChatCode(), 0));
            c.setChatMembers(null);
        }

        return new AppInitVO(chatVOList, new ArrayList<>(uniqueUserStatusMap.values()));
    }

    // ============================================================
    // 3. 房间详情与权限逻辑 (Chat Detail & Security)
    // ============================================================

    /**
     * 获取聊天室详情
     *
     * @param userId   用户ID
     * @param chatCode 聊天室代码
     * @return ChatVO 聊天室详情视图对象
     */
    @Override
    public ChatVO getChat(Integer userId, String chatCode) {
        // 1. 获取 ID 并执行权限校验
        Integer chatId = getChatId(userId, chatCode);

        // 2. 获取房间基础信息
        ChatVO chatVO = chatMapper.getChatVOByChatId(chatId);
        if (chatVO != null) {
            // 获取该房间所需的实时状态数据
            Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
            List<ChatMember> members = chatMemberMapper.getChatMemberListByChatId(chatId);
            MessageVO lastMsg = messageMapper.getLastMessageByChatId(chatId);
            Integer unreadCount = messageMapper.getUnreadCountMapByUserIdAndChatId(userId, chatId);

            // 调用核心组装逻辑
            assembleChatVO(chatVO, onlineUsers, lastMsg, unreadCount, members);
        }
        return chatVO;
    }

    /**
     * 获取聊天室成员列表（按 chatCode 懒加载）
     *
     * 业务目的：
     * - 右侧成员栏按需拉取成员列表，避免 refresh 初始化阶段全量加载所有群成员
     *
     * @param userId   当前用户内部 ID
     * @param chatCode 聊天室对外 ID
     * @return 成员列表（VO）
     */
    @Override
    public List<ChatMemberVO> getChatMemberVOList(Integer userId, String chatCode) {
        Integer chatId = getChatId(userId, chatCode);
        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
        List<ChatMember> members = chatMemberMapper.getChatMemberListByChatId(chatId);
        return toChatMemberVOList(members, onlineUsers);
    }

    /**
     * 根据 ChatCode 获取 ChatId，并校验用户权限
     *
     * @param userId   用户ID
     * @param chatCode 聊天室代码
     * @return ChatId
     * @throws RuntimeException 如果聊天室不存在或用户无权访问
     */
    @Override
    public Integer getChatId(Integer userId, String chatCode) {
        // 查找 ChatID
        Integer chatId = chatMapper.getChatIdByChatCode(chatCode);
        if (chatId == null) {
            log.warn("[非法请求] 用户 {} 尝试访问不存在的 chatCode: {}", userId, chatCode);
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        }

        // 校验成员关系 (防止越权访问)
        if (!chatMapper.isValidChatId(userId, chatId)) {
            log.warn("[权限拒绝] 用户 {} 尝试访问未加入的聊天室: {}", userId, chatCode);
            throw new BusinessException(ErrorCode.NOT_A_MEMBER);
        }
        return chatId;
    }

    /**
     * 获取聊天室加入校验信息（不做成员权限校验）
     *
     * @param chatCode 聊天室对外 ID
     * @return ChatJoinInfo
     */
    @Override
    public ChatJoinInfo getJoinInfo(String chatCode) {
        return chatMapper.getJoinInfoByChatCode(chatCode);
    }

    /**
     * 验证聊天室加入请求
     * <p>
     * 业务目的：
     * - 轻量级验证接口，仅验证房间是否存在、密码是否正确、是否允许加入
     * - 不执行实际的加入操作（不创建用户、不添加成员）
     * - 用于前端在用户提交加入表单前进行预验证
     * <p>
     * 支持两种验证模式：
     * <ul>
     *   <li><b>模式1：chatCode + password</b> - 通过房间ID和密码验证（两者必须同时提供）</li>
     *   <li><b>模式2：inviteCode</b> - 通过邀请码验证（可单独使用，当前未实现）</li>
     * </ul>
     * <p>
     * 验证逻辑：
     * <ol>
     *   <li>判断验证模式（inviteCode 或 chatCode）</li>
     *   <li>模式1：检查房间是否存在（42001）</li>
     *   <li>模式1：检查是否允许加入（joinEnabled == 1，否则返回 40300）</li>
     *   <li>模式1：检查密码登录是否启用（password_hash 是否为 null，为 null 则返回 40300）</li>
     *   <li>模式1：验证密码是否正确（42004）</li>
     *   <li>返回简化的 ChatVO（仅包含 chatCode, chatName, avatar, memberCount）</li>
     * </ol>
     *
     * @param req 验证请求对象（包含 chatCode + password 或 inviteCode）
     * @return 简化的 ChatVO（仅包含 chatCode, chatName, avatar, memberCount，其他字段为 null）
     * @throws BusinessException 如果验证失败（房间不存在、禁止加入、密码错误等）
     */
    @Override
    public ChatVO validateChatJoin(ValidateChatJoinReq req) {
        // 判断验证模式
        if (req.getInviteCode() != null && !req.getInviteCode().isBlank()) {
            // 模式2：邀请码验证（当前未实现）
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite code validation not implemented yet");
        } else if (req.getChatCode() != null && !req.getChatCode().isBlank()) {
            // 模式1：chatCode + password 验证
            // 检查 password 是否提供（如果提供了 chatCode，password 必填）
            if (req.getPassword() == null || req.getPassword().isBlank()) {
                throw new BusinessException(ErrorCode.PASSWORD_REQUIRED, "Password is required when chatCode is provided");
            }

            // 获取房间加入信息
            ChatJoinInfo info = chatMapper.getJoinInfoByChatCode(req.getChatCode());
            if (info == null || info.getChatId() == null) {
                throw new BusinessException(ErrorCode.CHAT_NOT_FOUND, "Chat room not found");
            }

            // 检查是否允许加入（joinEnabled == 1 才允许）
            if (info.getJoinEnabled() == null || info.getJoinEnabled() == 0) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Join is disabled for this chat room");
            }

            // 检查密码登录是否启用（password_hash 为 null 表示禁止通过 roomID 密码方式加入）
            if (info.getChatPasswordHash() == null || info.getChatPasswordHash().isBlank()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Password login is not enabled for this chat room");
            }

            // 验证密码是否正确
            if (!PasswordUtils.matches(req.getPassword(), info.getChatPasswordHash())) {
                throw new BusinessException(ErrorCode.PASSWORD_INCORRECT);
            }

            // 获取简化的 ChatVO
            ChatVO chatVO = chatMapper.getChatVOByChatId(info.getChatId());
            if (chatVO == null) {
                throw new BusinessException(ErrorCode.CHAT_NOT_FOUND, "Chat room not found");
            }

            // 构建简化的 ChatVO（只保留必要字段，其他设为 null）
            // 头像转换：从 avatarObjectName 转换为 Image 对象
            if (chatVO.getAvatarObjectName() != null) {
                chatVO.setAvatar(ImageUtils.buildImage(chatVO.getAvatarObjectName(), minioOSSOperator));
            }
            chatVO.setAvatarObjectName(null);

            // 设置其他字段为 null
            chatVO.setOwnerUid(null);
            chatVO.setJoinEnabled(null);
            chatVO.setLastActiveAt(null);
            chatVO.setCreateTime(null);
            chatVO.setUpdateTime(null);
            chatVO.setUnreadCount(null);
            chatVO.setLastMessage(null);
            chatVO.setOnLineMemberCount(null);
            chatVO.setChatMembers(null);

            return chatVO;
        } else {
            // 既没有提供 chatCode 也没有提供 inviteCode
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Either chatCode+password or inviteCode must be provided");
        }
    }

    // ============================================================
    // 4. 其他逻辑
    // ============================================================

    /**
     * 获取用户的聊天成员ID列表
     *
     * @param userId 用户ID
     * @return 成员ID列表
     */
    @Override
    public List<Integer> getChatMembers(Integer userId) {
        return chatMemberMapper.getChatMembersById(userId);
    }

    /**
     * 加入聊天室
     *
     * @param joinChatReq 加入请求
     * @return Chat 聊天室对象
     */
    @Override
    public Chat join(JoinChatReq joinChatReq) {
        // 业务校验：chatCode 必填
        if (joinChatReq == null || joinChatReq.getChatCode() == null || joinChatReq.getChatCode().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chatCode is required");
        }
        if (joinChatReq.getUid() == null || joinChatReq.getUid().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "uid is required");
        }

        ChatJoinInfo info = chatMapper.getJoinInfoByChatCode(joinChatReq.getChatCode());
        if (info == null || info.getChatId() == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        }

        // 业务规则：join_enabled=0 时，密码/邀请都不可加入（统一拒绝）
        if (info.getJoinEnabled() != null && info.getJoinEnabled() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Join is disabled for this chat");
        }

        // 业务规则：chat_password_hash != null 则必须校验密码；为 null 则免密加入
        if (info.getChatPasswordHash() != null && !info.getChatPasswordHash().isBlank()) {
            if (joinChatReq.getPassword() == null || joinChatReq.getPassword().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Password is required");
            }
            if (!PasswordUtils.matches(joinChatReq.getPassword(), info.getChatPasswordHash())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Incorrect password");
            }
        }

        Integer userId = userService.getIdByUid(joinChatReq.getUid());
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        chatMemberMapper.insertIgnore(info.getChatId(), userId, now);

        // 返回最小 Chat 对象用于上层逻辑（当前工程对 join 返回不做强依赖）
        // 注意：Lombok @AllArgsConstructor 不包含 transient 字段，所以构造函数参数不包含 avatarObjectName
        // 参数顺序：id, chatCode, chatName, ownerId, objectId, joinEnabled, createTime, updateTime
        Chat chat = new Chat();
        chat.setId(info.getChatId());
        chat.setChatCode(info.getChatCode());
        chat.setJoinEnabled(info.getJoinEnabled());
        chat.setCreateTime(now);
        chat.setUpdateTime(now);
        return chat;
    }

    /**
     * 创建聊天室
     *
     * @param userId 当前用户内部 ID
     * @param chatReq 创建参数
     * @return CreateChatVO（chatCode + inviteCode）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public CreateChatVO createChat(Integer userId, ChatReq chatReq) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "User is required");
        if (chatReq == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body is required");
        if (chatReq.getChatName() == null || chatReq.getChatName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chatName is required");
        }

        // 1) joinEnabled：默认允许加入（1）
        int joinEnabled = chatReq.getJoinEnable() == null ? 1 : chatReq.getJoinEnable();

        // 2) 密码：可选。若提供则写入 BCrypt hash；不提供则 NULL（表示免密）
        String passwordHash = null;
        String password = chatReq.getPassword();
        String confirm = chatReq.getPasswordConfirm();
        if (password != null && !password.isBlank()) {
            if (!password.equals(confirm)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Password confirm mismatch");
            }
            passwordHash = PasswordUtils.encode(password);
        }

        // 3) 头像对象 ID：优先使用前端传来的 objectId（性能优化，无需查表）
        Integer objectId = null;
        if (chatReq.getAvatar() != null) {
            // 优先使用 objectId（如果前端已提供）
            if (chatReq.getAvatar().getObjectId() != null) {
                objectId = chatReq.getAvatar().getObjectId();
            } else if (chatReq.getAvatar().getObjectName() != null) {
                // 降级方案：如果前端未提供 objectId，根据 objectName 查询（向后兼容）
                FileEntity existingObject = fileService.findByObjectName(chatReq.getAvatar().getObjectName());
                if (existingObject != null) {
                    objectId = existingObject.getId();
                } else {
                    log.warn("Chat avatar object not found in objects table: {}", chatReq.getAvatar().getObjectName());
                }
            }
        }

        // 4) chatCode：8 位数字，冲突重试（复用 UID 生成思路）
        String chatCode = null;
        ChatCreate chatCreate = new ChatCreate();
        chatCreate.setChatName(chatReq.getChatName());
        chatCreate.setOwnerId(userId);
        chatCreate.setJoinEnabled(joinEnabled);
        chatCreate.setChatPasswordHash(passwordHash);
        chatCreate.setObjectId(objectId);

        for (int i = 1; i <= 5; i++) {
            chatCode = UidGenerator.generateUid(8);
            chatCreate.setChatCode(chatCode);
            try {
                chatMapper.insertChat(chatCreate);
                break;
            } catch (DuplicateKeyException e) {
                if (i == 5) throw new BusinessException(ErrorCode.DATABASE_ERROR, "Failed to generate unique chatCode");
            }
        }

        Integer chatId = chatCreate.getId();
        if (chatId == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Create chat failed (no chatId)");
        }

        // 5) 创建者自动入群
        LocalDateTime now = LocalDateTime.now();
        chatMemberMapper.insertIgnore(chatId, userId, now);

        // 6) 生成短邀请码（默认 7 天 TTL）：入库保存 hash，返回明文给前端展示
        int expiryMinutes = chatReq.getJoinLinkExpiryMinutes() == null ? 10080 : chatReq.getJoinLinkExpiryMinutes();
        LocalDateTime expiresAt = now.plusMinutes(Math.max(1, expiryMinutes));

        // 读取 maxUses：默认为 0（无限使用）
        int maxUses = chatReq.getMaxUses() == null ? 0 : chatReq.getMaxUses();
        // 业务校验：目前只支持 0（无限）或 1（一次性）
        if (maxUses != 0 && maxUses != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "maxUses must be 0 or 1");
        }

        // 生成邀请码并入库
        String inviteCode = InviteCodeUtils.generateInviteCode(18);
        String codeHash = InviteCodeUtils.sha256Hex(inviteCode);
        ChatInvite invite = new ChatInvite(
                null,
                chatCode,
                codeHash,
                expiresAt,
                maxUses,
                0,
                0,
                userId,
                null,
                null
        );
        chatInviteMapper.insert(invite);

        return new CreateChatVO(chatCode, inviteCode);
    }
}

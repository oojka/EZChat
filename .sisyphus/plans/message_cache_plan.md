# 消息缓存改造计划：分表缓存与服务层组装

## 1. 核心目标
解决消息查询中的“连表查询无法高效缓存”问题。采用**归一化（Normalization）**策略：
- **Redis 不存连表结果**：只存消息本体（无用户信息）、用户资料、聊天室 ID 列表。
- **Java Service 组装**：在内存中并行获取各部分数据并组装，利用 Redis Pipeline 提升性能。

## 2. Redis Key 设计

| 用途 | Key 格式 | 数据结构 | 说明 | TTL |
| :--- | :--- | :--- | :--- | :--- |
| **消息时间轴** | `chat:timeline:{chatId}` | ZSet | Score: `seqId`, Member: `msgId` | 永久 (或保留最新 1000 条) |
| **消息本体** | `msg:data:{msgId}` | String (JSON) | 仅存 Message 字段 (含 senderId, assetIds)，**不含**昵称头像 | 7 天 (热数据) |
| **用户资料** | `user:profile:{uid}` | String (JSON) | `{uid, nickname, avatarUrl}` | 1 天 (写操作触发失效) |

## 3. 改造步骤

### Phase 1: 基础设施准备
- [ ] **RedisTemplate 配置**：确保 RedisTemplate 支持 Jackson 序列化。
- [ ] **CacheService 扩展**：
    - 新增 `batchGetMessages(List<Integer> msgIds)`
    - 新增 `batchGetUserProfiles(List<String> uids)`
    - 新增 `zAddMessage(Integer chatId, Long seqId, Integer msgId)`

### Phase 2: 写入改造 (Write-Through)
修改 `MessageServiceImpl.addMessage`：
1. **DB 插入**：保持现有逻辑，插入 MySQL。
2. **缓存同步**：
    - 将 `Message` 实体转为 JSON，写入 `msg:data:{id}`。
    - 将 `seqId -> id` 写入 `chat:timeline:{chatId}`。
3. **用户更新**：修改 `UserServiceImpl.updateProfile`，更新资料时删除 `user:profile:{uid}`。

### Phase 3: 读取改造 (Look-Aside + Assembly)
重构 `MessageServiceImpl.getMessagesByChatCode`：
1. **获取 IDs**：从 Redis ZSet `chat:timeline:{chatId}` 获取范围内的 `msgId` 列表。
    - *Cache Miss*: 如果 ZSet 为空，回源查 DB 填充 ZSet。
2. **批量取消息**：Pipeline `MGET msg:data:{id...}`。
    - *Cache Miss*: 缺失的消息回源查 DB 并回填 Redis。
3. **批量取用户**：收集消息中的 `senderId`，去重后 Pipeline `MGET user:profile:{uid...}`。
    - *Cache Miss*: 缺失的用户回源查 DB 并回填 Redis。
4. **内存组装**：
    - 遍历消息列表，将对应的 User Profile 填入 `MessageVO`。
    - 调用 `MessageAssembler` 填充图片链接（这一步也可以考虑缓存，但目前先保持实时生成）。

## 4. 性能预期
- **复杂度**：O(1) 数据库查询（理想情况下 0 SQL，全 Redis）。
- **网络消耗**：3 次 Redis RTT (Timeline -> Msg -> User)，极快。
- **一致性**：用户修改头像后，刷新聊天列表即可立刻看到新头像（因为是读取时实时组装）。

## 5. 风险与回退
- **数据不一致**：Redis 写入失败可能导致缓存与 DB 不一致。
    - *对策*：设置合理的 TTL，读取时发现缺失自动回源。
- **大 Key 问题**：如果群消息过多，ZSet 可能过大。
    - *对策*：限制 ZSet 长度（如只缓存最新 2000 条），旧消息走 DB。


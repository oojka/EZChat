# Mapper 层 (MyBatis)

## OVERVIEW

MyBatis 数据访问层，接口在 `mapper/`，SQL 在 `resources/.../mapper/*.xml`。

## FILES

| 接口 | XML | 核心方法 |
|------|-----|----------|
| `UserMapper` | ✅ | `selectByUid`, `insertUser`, `updateProfile` |
| `MessageMapper` | ✅ | `insertMessage`, `selectMessageListByChatIdAndCursor` |
| `ChatMapper` | ✅ | `selectByChatCode`, `insertChat` |
| `ChatMemberMapper` | ✅ | `selectMembers`, `updateLastSeenAt` |
| `AssetMapper` | ✅ | `findByHash`, `batchActivate` |
| `FriendshipMapper` | ✅ | `selectFriendList` |
| `FriendRequestMapper` | ✅ | `selectPending`, `updateStatus` |
| `ChatInviteMapper` | ✅ | `selectByCode`, `decrementUses` |
| `OperationLogMapper` | ✅ | `insertLog` |

## CONVENTIONS

### 命名规则

| 操作 | 前缀 | 示例 |
|------|------|------|
| 查询单条 | `selectXxx` | `selectByUid` |
| 查询列表 | `selectXxxList` | `selectMemberList` |
| 插入 | `insert` | `insertMessage` |
| 更新 | `update` | `updateProfile` |
| 删除 | `delete` | `deleteByUserId` |
| 统计 | `count` | `countUnread` |

### XML 位置

```
src/main/resources/hal/th50743/mapper/XxxMapper.xml
```

### 驼峰映射

`application.yml` 已开启 `map-underscore-to-camel-case: true`，无需手写 ResultMap。

## ANTI-PATTERNS

- ❌ 在接口上用 `@Select` 注解 (应写在 XML)
- ❌ Service 层直接拼 SQL
- ❌ N+1 查询 (应用 JOIN 或批量查询)

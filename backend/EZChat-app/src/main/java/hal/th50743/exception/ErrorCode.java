package hal.th50743.exception;

import lombok.Getter;

/**
 * 错误码枚举
 * <p>
 * 业务目的：
 * - 统一管理所有业务错误码，避免硬编码错误码分散在代码各处
 * - 提供标准化的错误码和错误消息，便于前端进行错误处理和国际化
 * - 通过错误码范围区分不同类型的错误（客户端错误、业务错误、服务器错误）
 * <p>
 * 错误码设计规范：
 * <ul>
 *   <li><b>200</b>：成功（HTTP 状态码兼容）</li>
 *   <li><b>4xxxx</b>：客户端错误（请求参数错误、权限问题等）</li>
 *   <li><b>41xxx</b>：用户相关业务错误（用户不存在、用户已存在、凭证无效等）</li>
 *   <li><b>42xxx</b>：聊天/消息相关业务错误（房间不存在、非成员、密码相关等）</li>
 *   <li><b>43xxx</b>：文件相关业务错误（文件为空、文件过大等）</li>
 *   <li><b>5xxxx</b>：服务器错误（系统内部错误、数据库错误等）</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>{@code
 * // 在 Service 层抛出业务异常
 * throw new BusinessException(ErrorCode.USER_NOT_FOUND);
 * 
 * // 使用标准错误码 + 自定义消息
 * throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限访问此资源");
 * }</pre>
 * <p>
 * 前端处理：
 * - 前端通过 {@link hal.th50743.pojo.Result} 的 code 字段获取错误码
 * - 前端通过 i18n 键 `api.errors.${code}` 获取国际化错误消息
 * - 前端根据错误码进行相应的路由跳转和用户提示
 *
 * @author EZ Chat Team
 * @see BusinessException
 * @see GlobalExceptionHandler
 */
@Getter
public enum ErrorCode {
    // =========================================
    // 成功状态码
    // =========================================
    /**
     * 操作成功
     * <p>
     * 使用场景：API 调用成功时返回此状态码
     * <p>
     * 注意：此状态码与 HTTP 200 状态码兼容，但业务层通常使用 status=1 表示成功
     */
    SUCCESS(200, "success"),

    // =========================================
    // 客户端错误 (4xxxx)
    // =========================================
    /**
     * 请求参数错误
     * <p>
     * 使用场景：
     * - 请求参数格式不正确（如：类型不匹配、格式错误）
     * - 请求参数缺失必填字段
     * - 请求参数值不符合业务规则（如：长度超限、范围越界）
     * <p>
     * 示例：
     * - 房间名称超过 20 个字符
     * - 密码长度不符合要求
     * - 日期格式错误
     */
    BAD_REQUEST(40000, "Bad request"),

    /**
     * 未授权
     * <p>
     * 使用场景：
     * - Token 缺失或无效
     * - Token 已过期
     * - 用户未登录或登录状态已失效
     * <p>
     * 注意：与 HTTP 401 状态码语义相同，但这是业务层的错误码
     * <p>
     * 前端处理：通常需要清理登录态并跳转到登录页
     */
    UNAUTHORIZED(40100, "Unauthorized"),

    /**
     * 禁止访问
     * <p>
     * 使用场景：
     * - 用户没有权限执行该操作
     * - 资源访问被限制（如：房间禁止加入、密码登录未启用）
     * - 操作被管理员禁用
     * <p>
     * 注意：
     * - 与 HTTP 403 状态码语义相同，但这是业务层的错误码
     * - 可以根据错误消息区分不同的禁止场景（如：join_disabled vs password_login_disabled）
     * <p>
     * 前端处理：根据错误消息内容进行不同的路由跳转
     */
    FORBIDDEN(40300, "Forbidden"),

    /**
     * 资源未找到
     * <p>
     * 使用场景：
     * - 请求的资源不存在（如：用户不存在、房间不存在）
     * - 资源已被删除
     * - 资源 ID 无效
     * <p>
     * 注意：与 HTTP 404 状态码语义相同，但这是业务层的错误码
     */
    NOT_FOUND(40400, "Resource not found"),

    // =========================================
    // 业务错误：用户相关 (41xxx)
    // =========================================
    /**
     * 用户不存在
     * <p>
     * 使用场景：
     * - 根据用户 ID/UID 查询用户时，用户不存在
     * - 用户已被删除
     * - 用户 ID 无效
     * <p>
     * 示例：
     * <pre>{@code
     * User user = userMapper.findByUid(uid);
     * if (user == null) {
     *     throw new BusinessException(ErrorCode.USER_NOT_FOUND);
     * }
     * }</pre>
     */
    USER_NOT_FOUND(41001, "User not found"),

    /**
     * 用户已存在
     * <p>
     * 使用场景：
     * - 注册用户时，用户名/UID 已被占用
     * - 创建用户时，唯一标识冲突
     * <p>
     * 示例：
     * <pre>{@code
     * if (userMapper.existsByUsername(username)) {
     *     throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
     * }
     * }</pre>
     */
    USER_ALREADY_EXISTS(41002, "User already exists"),

    /**
     * 凭证无效
     * <p>
     * 使用场景：
     * - 登录时用户名或密码错误
     * - 密码验证失败
     * - 认证信息不匹配
     * <p>
     * 注意：出于安全考虑，不区分是用户名错误还是密码错误
     */
    INVALID_CREDENTIALS(41003, "Invalid username or password"),

    // =========================================
    // 业务错误：聊天/消息相关 (42xxx)
    // =========================================
    /**
     * 聊天室不存在
     * <p>
     * 使用场景：
     * - 根据聊天室代码（chatCode）查询聊天室时，聊天室不存在
     * - 聊天室已被删除
     * - 聊天室代码无效
     * <p>
     * 前端处理：
     * - 如果当前在聊天页面，跳转到聊天欢迎页
     * - 否则显示错误消息，停留在当前页面
     */
    CHAT_NOT_FOUND(42001, "Chat room not found"),

    /**
     * 用户不是聊天室成员
     * <p>
     * 使用场景：
     * - 用户尝试访问未加入的聊天室
     * - 用户尝试在非成员的聊天室中发送消息
     * - 用户被移出聊天室后仍尝试访问
     * <p>
     * 前端处理：
     * - 如果当前在聊天页面，跳转到聊天欢迎页
     * - 刷新房间列表（因为成员状态可能已过期）
     */
    NOT_A_MEMBER(42002, "User is not a member of this chat room"),

    /**
     * 密码必填
     * <p>
     * 使用场景：
     * - 通过房间 ID 加入聊天室时，密码字段为空
     * - 聊天室设置了密码，但请求中未提供密码
     * <p>
     * 注意：此错误码用于加入验证流程，表示密码字段缺失
     * <p>
     * 前端处理：跳转到 `/join/error?reason=password_required`
     */
    PASSWORD_REQUIRED(42003, "Password is required"),

    /**
     * 密码错误
     * <p>
     * 使用场景：
     * - 通过房间 ID 加入聊天室时，提供的密码不正确
     * - 密码验证失败
     * <p>
     * 注意：此错误码用于加入验证流程，表示密码验证失败
     * <p>
     * 前端处理：跳转到 `/join/error?reason=password_incorrect`
     */
    PASSWORD_INCORRECT(42004, "Incorrect password"),

    // =========================================
    // 业务错误：文件相关 (43xxx)
    // =========================================
    /**
     * 文件为空
     * <p>
     * 使用场景：
     * - 上传的文件大小为 0
     * - 文件内容为空
     * - 未选择文件
     * <p>
     * 注意：通常在文件上传前进行校验
     */
    FILE_EMPTY(43001, "File is empty"),

    /**
     * 文件大小超限
     * <p>
     * 使用场景：
     * - 上传的文件大小超过系统限制（如：图片超过 10MB）
     * - 文件大小不符合业务规则
     * <p>
     * 注意：通常在文件上传前进行校验
     */
    FILE_SIZE_EXCEED(43002, "File size exceeds limit"),

    /**
     * 文件上传失败
     * <p>
     * 使用场景：
     * - 文件上传到 OSS 时发生错误
     * - 文件处理过程中发生异常
     * - 存储服务不可用
     * <p>
     * 注意：此错误码属于服务器错误（5xxxx），但归类在文件相关错误中
     */
    FILE_UPLOAD_ERROR(53001, "File upload failed"),

    // =========================================
    // 服务器错误 (5xxxx)
    // =========================================
    /**
     * 系统内部错误
     * <p>
     * 使用场景：
     * - 未预期的系统异常（如：空指针异常、类型转换异常等）
     * - 业务逻辑中的未知错误
     * - 作为 {@link BusinessException#BusinessException(String)} 构造方法的默认错误码
     * <p>
     * 注意：
     * - 此错误码表示系统内部问题，不应暴露给用户的详细错误信息
     * - 生产环境应记录详细日志，但返回给前端的消息应保持简洁
     */
    SYSTEM_ERROR(50000, "System internal error"),

    /**
     * 数据库错误
     * <p>
     * 使用场景：
     * - 数据库操作失败（如：连接失败、查询超时、事务回滚失败）
     * - 数据库约束违反（如：唯一键冲突、外键约束违反）
     * - 数据完整性错误（如：数据截断、类型不匹配）
     * <p>
     * 注意：
     * - 通常由 {@link GlobalExceptionHandler} 捕获数据库异常后转换为此错误码
     * - 不应直接在前端暴露数据库错误详情，应转换为用户友好的错误消息
     */
    DATABASE_ERROR(50001, "Database error");

    /**
     * 错误码数值
     * <p>
     * 取值范围：
     * - 200：成功
     * - 4xxxx：客户端错误
     * - 5xxxx：服务器错误
     */
    private final int code;

    /**
     * 错误消息（英文）
     * <p>
     * 注意：
     * - 此消息为默认的英文错误消息
     * - 前端应通过 i18n 键 `api.errors.${code}` 获取国际化错误消息
     * - 如果前端 i18n 文件中没有对应的翻译，可以使用此默认消息作为兜底
     */
    private final String msg;

    /**
     * 错误码枚举构造函数
     *
     * @param code 错误码数值
     * @param msg  错误消息（英文）
     */
    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
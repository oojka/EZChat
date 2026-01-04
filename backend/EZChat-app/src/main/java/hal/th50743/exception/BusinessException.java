package hal.th50743.exception;

import lombok.Getter;

/**
 * 业务异常类
 * <p>
 * 业务目的：
 * - 统一处理业务层面的错误，与系统异常（如数据库连接失败、空指针等）区分开
 * - 通过错误码（code）和错误消息（msg）向前端返回结构化的错误信息
 * - 由 {@link GlobalExceptionHandler} 统一捕获并转换为 {@link hal.th50743.pojo.Result} 返回给前端
 * <p>
 * 设计要点：
 * - 继承自 {@link RuntimeException}，属于非受检异常，无需在方法签名中声明 throws
 * - 包含错误码（code）和错误消息（msg）两个字段，便于前端进行错误处理和国际化
 * - 提供多种构造方法，支持不同的使用场景（标准错误码、自定义错误码、自定义消息等）
 * <p>
 * 使用场景：
 * - Service 层业务逻辑校验失败时抛出（如：用户不存在、房间不存在、密码错误等）
 * - 权限校验失败时抛出（如：无权限访问、未授权等）
 * - 数据校验失败时抛出（如：参数格式错误、必填字段缺失等）
 * <p>
 * 示例：
 * <pre>{@code
 * // 使用标准错误码（推荐）
 * throw new BusinessException(ErrorCode.USER_NOT_FOUND);
 * 
 * // 使用标准错误码 + 自定义消息（用于提供更详细的错误信息）
 * throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限访问此用户的个人信息");
 * 
 * // 使用自定义错误码和消息（不推荐，应优先使用 ErrorCode 枚举）
 * throw new BusinessException(40001, "自定义错误消息");
 * 
 * // 仅提供错误消息（不推荐，会使用默认的系统错误码）
 * throw new BusinessException("操作失败");
 * }</pre>
 *
 * @author EZ Chat Team
 * @see ErrorCode
 * @see GlobalExceptionHandler
 * @see hal.th50743.pojo.Result
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 业务错误码
     * <p>
     * 取值范围：
     * - 4xxxx：客户端错误（如 40000 请求参数错误、40100 未授权、40300 禁止访问等）
     * - 5xxxx：服务器错误（如 50000 系统内部错误、50001 数据库错误等）
     * <p>
     * 建议使用 {@link ErrorCode} 枚举中定义的错误码，保持错误码的统一性和可维护性
     */
    private final int code;

    /**
     * 错误消息
     * <p>
     * 用于描述具体的错误原因，可以：
     * - 使用 {@link ErrorCode} 中定义的默认消息
     * - 提供自定义的详细错误信息（如包含具体的字段名、约束条件等）
     * <p>
     * 注意：此消息会传递给父类 {@link RuntimeException#getMessage()}，便于日志记录
     */
    private final String msg;

    /**
     * 构造方法 1：使用标准错误码（推荐）
     * <p>
     * 业务目的：
     * - 使用 {@link ErrorCode} 枚举中预定义的错误码和消息
     * - 保持错误码的统一性，便于前端进行错误处理和国际化
     * <p>
     * 使用场景：
     * - 业务逻辑校验失败（如：用户不存在、房间不存在）
     * - 权限校验失败（如：未授权、禁止访问）
     * - 数据校验失败（如：密码错误、必填字段缺失）
     * <p>
     * 示例：
     * <pre>{@code
     * if (user == null) {
     *     throw new BusinessException(ErrorCode.USER_NOT_FOUND);
     * }
     * }</pre>
     *
     * @param errorCode 标准错误码枚举值（不能为 null）
     */
    public BusinessException(ErrorCode errorCode) {
        // 将错误消息传递给父类，方便日志打印时使用 e.getMessage() 获取
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
        this.msg = errorCode.getMsg();
    }

    /**
     * 构造方法 2：使用自定义错误码和消息
     * <p>
     * 业务目的：
     * - 支持使用自定义的错误码和消息
     * - 适用于需要返回特定错误码但不在 {@link ErrorCode} 枚举中的场景
     * <p>
     * 使用场景：
     * - 需要返回特定的业务错误码（如第三方接口返回的错误码）
     * - 临时错误码（后续应迁移到 {@link ErrorCode} 枚举）
     * <p>
     * 注意：
     * - 不推荐使用，应优先使用 {@link ErrorCode} 枚举
     * - 如果确实需要自定义错误码，建议先添加到 {@link ErrorCode} 枚举中
     * <p>
     * 示例：
     * <pre>{@code
     * throw new BusinessException(40001, "自定义错误消息");
     * }</pre>
     *
     * @param code 自定义错误码（建议使用 4xxxx 或 5xxxx 范围内的值）
     * @param msg  自定义错误消息（不能为 null）
     */
    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    /**
     * 构造方法 2.1：使用标准错误码 + 自定义消息（推荐用于需要详细错误信息的场景）
     * <p>
     * 业务目的：
     * - 使用标准错误码保持错误码的统一性
     * - 提供自定义的详细错误信息，增强错误消息的可读性和可操作性
     * <p>
     * 使用场景：
     * - 需要提供更详细的错误信息（如：包含具体的字段名、约束条件、操作建议等）
     * - 需要根据业务上下文动态生成错误消息（如：包含具体的资源 ID、操作类型等）
     * <p>
     * 示例：
     * <pre>{@code
     * // 提供详细的权限错误信息
     * throw new BusinessException(ErrorCode.FORBIDDEN, 
     *     "您没有权限访问用户 " + userId + " 的个人信息，仅限本人或共同群聊成员查看");
     * 
     * // 提供详细的参数错误信息
     * throw new BusinessException(ErrorCode.BAD_REQUEST, 
     *     "房间名称不能为空，且长度不能超过 20 个字符");
     * }</pre>
     *
     * @param errorCode 标准错误码枚举值（不能为 null）
     * @param msg       自定义错误消息（不能为 null，会覆盖 ErrorCode 中的默认消息）
     */
    public BusinessException(ErrorCode errorCode, String msg) {
        super(msg);
        this.code = errorCode.getCode();
        this.msg = msg;
    }

    /**
     * 构造方法 3：仅提供错误消息（不推荐）
     * <p>
     * 业务目的：
     * - 快速抛出异常，无需指定错误码
     * - 自动使用系统错误码（{@link ErrorCode#SYSTEM_ERROR}）作为默认错误码
     * <p>
     * 使用场景：
     * - 临时调试或快速开发阶段
     * - 确实无法确定具体错误码的场景（应尽量避免）
     * <p>
     * 注意：
     * - 不推荐使用，应优先使用构造方法 1 或 2.1
     * - 使用此构造方法会丢失错误码的语义信息，不利于前端进行精确的错误处理
     * - 如果确实需要使用，建议后续重构为使用标准错误码
     * <p>
     * 示例：
     * <pre>{@code
     * throw new BusinessException("操作失败");
     * }</pre>
     *
     * @param msg 错误消息（不能为 null）
     */
    public BusinessException(String msg) {
        super(msg);
        // 使用系统错误码作为默认值，表示这是一个未分类的系统错误
        this.code = ErrorCode.SYSTEM_ERROR.getCode();
        this.msg = msg;
    }
}
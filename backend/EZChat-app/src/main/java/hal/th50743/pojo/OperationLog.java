package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志实体类
 * <p>
 * 用于记录用户的敏感操作，如创建、更新、删除等。
 * 支持详细的审计追踪，包括请求信息、执行结果和耗时统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLog {

    /** 主键ID */
    private Long id;

    /** 操作用户ID（可能为空，如未登录尝试） */
    private Integer userId;

    /** 功能模块（如：Chat, User, Message） */
    private String module;

    /** 操作类型（如：CREATE, UPDATE, DELETE） */
    private String type;

    /** 详细描述（方法名 + 参数摘要） */
    private String content;

    /** 操作IP地址 */
    private String ipAddress;

    /** 执行状态：1=成功, 0=失败 */
    private Integer status;

    /** 方法执行耗时（毫秒） */
    private Long executionTime;

    /** 请求路径（如：POST /api/chat/create） */
    private String requestPath;

    /** 请求方法（GET/POST/PUT/DELETE） */
    private String requestMethod;

    /** User-Agent 浏览器标识 */
    private String userAgent;

    /** 目标资源ID（如聊天室ID、消息ID等，便于关联查询） */
    private String targetId;

    /** 错误信息（失败时记录） */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;
}

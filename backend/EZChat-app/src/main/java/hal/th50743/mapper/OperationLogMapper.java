package hal.th50743.mapper;

import hal.th50743.pojo.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志数据访问层
 *
 * <p>负责操作日志（operation_logs 表）的数据库存储。
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>操作日志插入（AOP 切面自动记录）</li>
 * </ul>
 *
 * <h3>记录内容</h3>
 * <ul>
 *   <li>用户操作（登录、注册、发送消息等）</li>
 *   <li>请求信息（路径、方法、IP、UserAgent）</li>
 *   <li>执行结果（状态、耗时、错误信息）</li>
 * </ul>
 *
 * <h3>表依赖</h3>
 * <ul>
 *   <li>{@code operation_logs} - 操作日志表</li>
 * </ul>
 *
 * <p>注意：本接口仅提供写入功能，查询功能由管理后台实现（本工程不包含）。
 *
 * @see OperationLog
 */
@Mapper
public interface OperationLogMapper {

    /**
     * 插入操作日志
     *
     * <p>由 AOP 切面（{@code @Audited}）自动调用。
     * 插入后通过 useGeneratedKeys 回填 id。
     *
     * @param log 操作日志对象（包含 userId、module、type、content、ipAddress 等）
     */
    void insertLog(OperationLog log);
}

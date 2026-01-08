package hal.th50743.mapper;

import hal.th50743.pojo.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper 接口
 * <p>
 * 负责操作日志的数据库存储。
 */
@Mapper
public interface OperationLogMapper {

    /**
     * 插入操作日志
     *
     * @param log 操作日志对象
     */
    void insertLog(OperationLog log);
}

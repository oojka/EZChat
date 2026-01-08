package hal.th50743.service.impl;

import hal.th50743.mapper.OperationLogMapper;
import hal.th50743.pojo.OperationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 异步日志服务
 * <p>
 * 专门用于异步执行日志入库操作，避免阻塞主业务线程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncLogService {

    private final OperationLogMapper operationLogMapper;

    /**
     * 异步添加操作日志
     *
     * @param log 操作日志对象
     */
    @Async
    public void addLog(OperationLog log) {
        try {
            operationLogMapper.insertLog(log);
        } catch (Exception e) {
            // 日志入库失败不应影响主业务，仅记录错误日志
            hal.th50743.service.impl.AsyncLogService.log.error("Failed to add operation log: {}", log, e);
        }
    }
}

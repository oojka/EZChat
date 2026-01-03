package hal.th50743.task;

import hal.th50743.mapper.FileMapper;
import hal.th50743.pojo.FileEntity;
import hal.th50743.service.FileService;
import hal.th50743.service.OssMediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件清理定时任务
 * <p>
 * 业务目的：定期清理 PENDING 状态的过期文件，防止 MinIO 存储空间泄露。
 * <p>
 * 执行策略：
 * - 每天凌晨 2 点执行
 * - 清理 24 小时前的 PENDING 文件（status=0 AND create_time < NOW - 24h）
 * - 分页查询 + do...while 循环，防止 OOM
 * - 每批处理 100 条，处理完休眠 100ms 释放数据库连接
 * - 异常隔离：单个文件删除失败不影响其他文件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupTask {

    private static final int BATCH_SIZE = 100; // 每批处理 100 条
    private static final int GC_HOURS_OLD = 24; // 清理 24 小时前的文件

    private final FileService fileService;
    private final OssMediaService ossMediaService;
    private final FileMapper fileMapper;

    /**
     * 清理待处理的过期文件
     * <p>
     * Cron 表达式：`0 0 2 * * ?` 表示每天凌晨 2 点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupPendingFiles() {
        log.info("=== 开始执行文件 GC 任务 ===");
        long startTime = System.currentTimeMillis();
        
        int totalDeleted = 0;
        int totalFailed = 0;
        int offset = 0;

        do {
            // 1. 分页查询 PENDING 文件（每次最多 100 条）
            List<FileEntity> pendingFiles = fileService.findPendingFilesForGC(
                    GC_HOURS_OLD, BATCH_SIZE, offset
            );

            if (pendingFiles.isEmpty()) {
                break; // 没有更多数据，退出循环
            }

            log.debug("GC batch: offset={}, found {} files", offset, pendingFiles.size());

            // 2. 批量删除 MinIO 对象和数据库记录
            for (FileEntity file : pendingFiles) {
                try {
                    // 先删除 MinIO 对象（会自动删除缩略图）
                    ossMediaService.deleteObject(file.getObjectName());
                    // 再删除数据库记录
                    fileMapper.deleteById(file.getId());
                    totalDeleted++;
                } catch (Exception e) {
                    log.error("Failed to delete file: objectName={}, id={}", 
                            file.getObjectName(), file.getId(), e);
                    totalFailed++;
                }
            }

            offset += BATCH_SIZE; // 移动到下一批

            // 3. 避免长时间占用连接，每批处理后短暂休眠
            try {
                Thread.sleep(100); // 100ms 休眠
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("GC task interrupted");
                break;
            }

        } while (true); // 直到查询结果为空

        // 4. 记录汇总日志
        long duration = System.currentTimeMillis() - startTime;
        log.info("=== 文件 GC 任务完成 ===");
        log.info("Total deleted: {}, Total failed: {}, Duration: {}ms", 
                totalDeleted, totalFailed, duration);
    }
}




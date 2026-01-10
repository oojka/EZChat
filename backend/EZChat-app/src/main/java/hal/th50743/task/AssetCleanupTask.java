package hal.th50743.task;

import hal.th50743.mapper.AssetMapper;
import hal.th50743.pojo.Asset;
import hal.th50743.service.AssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件清理定时任务（优化版）
 * <p>
 * 业务目的：定期清理 PENDING 状态的过期文件，防止 MinIO 存储空间泄露。
 * <p>
 * 执行策略：
 * - 每天凌晨 2 点执行
 * - 清理 N 小时前的 PENDING 文件（可配置）
 * - 游标分页 + 批量删除，避免 offset 跳过问题
 * - 分批处理防止 OOM，批量删除提升性能
 * - 异常隔离：单个文件删除失败不影响其他文件
 * - 支持最大执行时间和最大删除数限制
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetCleanupTask {

    /** 每批处理条数 */
    @Value("${app.gc.batch-size:100}")
    private int batchSize;

    /** 清理多少小时前的文件 */
    @Value("${app.gc.hours-old:24}")
    private int gcHoursOld;

    /** 单次任务最大删除数量（防止长时间锁表） */
    @Value("${app.gc.max-delete-count:10000}")
    private int maxDeleteCount;

    /** 单次任务最大执行时间（毫秒） */
    @Value("${app.gc.max-duration-ms:300000}")
    private long maxDurationMs;

    /** 批次间休眠时间（毫秒） */
    @Value("${app.gc.batch-sleep-ms:50}")
    private long batchSleepMs;

    private final AssetService assetService;
    private final AssetMapper assetMapper;

    /**
     * 清理待处理的过期文件
     * <p>
     * Cron 表达式：`0 0 2 * * ?` 表示每天凌晨 2 点执行
     */
    @Scheduled(cron = "${app.gc.cron:0 0 2 * * ?}")
    public void cleanupPendingFiles() {
        log.info("========== Asset GC Task Started ==========");
        log.info("Config: batchSize={}, gcHoursOld={}h, maxDeleteCount={}, maxDurationMs={}ms",
                batchSize, gcHoursOld, maxDeleteCount, maxDurationMs);

        long startTime = System.currentTimeMillis();
        AtomicInteger totalDeleted = new AtomicInteger(0);
        AtomicInteger totalFailed = new AtomicInteger(0);
        int batchCount = 0;

        try {
            while (shouldContinue(startTime, totalDeleted.get())) {
                // 1. 分页查询（使用 ID 游标，避免 offset 跳过问题）
                List<Asset> pendingFiles = assetService.findPendingFilesForGC(gcHoursOld, batchSize, 0);

                if (pendingFiles.isEmpty()) {
                    log.info("No more files to clean, exiting loop");
                    break;
                }

                batchCount++;
                log.debug("Processing batch {}: found {} pending files", batchCount, pendingFiles.size());

                // 2. 批量处理本批次
                processBatch(pendingFiles, totalDeleted, totalFailed);

                // 3. 批次间休眠，释放数据库连接
                sleepBetweenBatches();
            }
        } catch (Exception e) {
            log.error("Asset GC task failed", e);
        }

        // 4. 输出汇总日志
        logSummary(startTime, batchCount, totalDeleted.get(), totalFailed.get());
    }

    /**
     * 判断是否继续执行
     */
    private boolean shouldContinue(long startTime, int currentDeleted) {
        // 检查超时
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= maxDurationMs) {
            log.warn("Max duration limit reached ({}ms), stopping early", maxDurationMs);
            return false;
        }

        // 检查最大删除数
        if (currentDeleted >= maxDeleteCount) {
            log.warn("Max delete count limit reached ({}), stopping early", maxDeleteCount);
            return false;
        }

        // 检查线程中断
        if (Thread.currentThread().isInterrupted()) {
            log.warn("Task interrupted, stopping");
            return false;
        }

        return true;
    }

    /**
     * 处理单批文件
     */
    private void processBatch(List<Asset> files, AtomicInteger totalDeleted, AtomicInteger totalFailed) {
        List<Integer> successIds = new ArrayList<>();

        for (Asset file : files) {
            try {
                // 先删除 MinIO 对象（会自动删除缩略图）
                // 如果没有抛异常，视为删除成功
                assetService.deleteAsset(file.getAssetName());
                successIds.add(file.getId());
                totalDeleted.incrementAndGet();
            } catch (Exception e) {
                log.error("Failed to delete file: id={}, assetName={}, error={}",
                        file.getId(), file.getAssetName(), e.getMessage());
                totalFailed.incrementAndGet();
            }
        }

        // 批量删除数据库记录（性能优化）
        if (!successIds.isEmpty()) {
            try {
                int deletedRows = assetMapper.deleteByIds(successIds);
                log.debug("Batch deleted DB records: expected={}, actual={}", successIds.size(), deletedRows);
            } catch (Exception e) {
                log.error("Failed to batch delete DB records: ids={}", successIds, e);
                // 注意：这里 MinIO 已删除但 DB 未删除，下次 GC 会重新处理
                // 由于 MinIO 已删除，下次删除时会报对象不存在，可以安全忽略
            }
        }
    }

    /**
     * 批次间休眠
     */
    private void sleepBetweenBatches() {
        if (batchSleepMs > 0) {
            try {
                Thread.sleep(batchSleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("GC task sleep interrupted");
            }
        }
    }

    /**
     * 输出汇总日志
     */
    private void logSummary(long startTime, int batchCount, int totalDeleted, int totalFailed) {
        long duration = System.currentTimeMillis() - startTime;
        double avgSpeed = duration > 0 ? (totalDeleted * 1000.0 / duration) : 0;

        log.info("========== Asset GC Task Finished ==========");
        log.info("Stats: batches={}, deleted={}, failed={}", batchCount, totalDeleted, totalFailed);
        log.info("Duration: {}ms, Avg speed: {}/s", duration, String.format("%.1f", avgSpeed));

        if (totalFailed > 0) {
            log.warn("{} files failed to delete, check error logs", totalFailed);
        }
    }
}

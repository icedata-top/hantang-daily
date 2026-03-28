package utils;

import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 可动态调整线程数的线程池
 * 基于 ThreadPoolExecutor，使用 setCorePoolSize() 动态调整
 */
public class DynamicThreadPool {
    private static final Logger logger = LogManager.getLogger(DynamicThreadPool.class);

    private final ThreadPoolExecutor executor;
    private final String poolName;

    /**
     * 创建动态线程池
     * @param poolName 线程池名称（用于日志）
     * @param initialSize 初始线程数
     */
    public DynamicThreadPool(String poolName, int initialSize) {
        this.poolName = poolName;
        this.executor = new ThreadPoolExecutor(
            initialSize,                        // 核心线程数
            initialSize,                        // 最大线程数
            60L, TimeUnit.SECONDS,              // 空闲线程存活时间
            new LinkedBlockingQueue<>()         // 无界队列
        );
        logger.info("Created dynamic thread pool [{}] with size {}", poolName, initialSize);
    }

    /**
     * 动态调整线程池大小
     * @param newSize 新的线程数
     */
    public void resize(int newSize) {
        int currentSize = executor.getCorePoolSize();
        if (newSize == currentSize) {
            return; // 大小未变，无需调整
        }

        logger.info("Resizing thread pool [{}] from {} to {}", poolName, currentSize, newSize);

        executor.setCorePoolSize(newSize);
        executor.setMaximumPoolSize(newSize);

        logger.info("Thread pool [{}] resized successfully", poolName);
    }

    /**
     * 提交任务
     */
    public void submit(Runnable task) {
        executor.submit(task);
    }

    /**
     * 提交任务并返回 Future
     */
    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    /**
     * 获取底层 ExecutorService
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        executor.shutdown();
    }
}

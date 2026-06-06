package business;

import api.BilibiliApi;
import dao.PostgresDao;
import dos.UserDO;
import dos.VideoDynamicDO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 今日动态数据，多线程并发发起API请求，Future设计模式等全部都加载完，再返回。
 */
public class TodayDynamicDataJob {
    private final List<Long> allVideoIdList;
    private final List<VideoDynamicDO> allVideoDynamicDOList;
    private static final int GROUP_SIZE;
    private static final int EXECUTORS_SIZE_OFFICIAL;
    private static final int EXECUTORS_SIZE_PROXY;
    private static final Logger logger = LogManager.getLogger(TodayDynamicDataJob.class);

    public TodayDynamicDataJob(List<Long> allVideoIdList) {
        this.allVideoIdList = allVideoIdList;
        this.allVideoDynamicDOList = new ArrayList<>();
    }

    static {
        try {
            // 加载配置文件
            Properties properties = new Properties();
            FileInputStream input = new FileInputStream("config.properties");
            properties.load(input);

            // 读取配置
            GROUP_SIZE = getPositiveIntProperty(properties, "dynamic.group_size", 25);
            EXECUTORS_SIZE_OFFICIAL = getPositiveIntProperty(
                    properties,
                    "dynamic.executors_size.official",
                    "dynamic.executors_size",
                    10
            );
            EXECUTORS_SIZE_PROXY = getPositiveIntProperty(properties, "dynamic.executors_size.proxy", 30);
        } catch (IOException e) {
            e.fillInStackTrace();
            throw new RuntimeException("无法加载数据库配置文件", e);
        }
    }

    private static int getPositiveIntProperty(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
            throw new NumberFormatException("value must be positive");
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int getPositiveIntProperty(Properties properties, String key, String fallbackKey, int defaultValue) {
        if (properties.getProperty(key) != null) {
            return getPositiveIntProperty(properties, key, defaultValue);
        }
        return getPositiveIntProperty(properties, fallbackKey, defaultValue);
    }
    /**
     * 串行地调用API获取结果
     */
    public void getDataSerial() throws IOException {
        BilibiliApi bilibiliApi = new BilibiliApi();
        int groupCount = (allVideoIdList.size() + GROUP_SIZE - 1) / GROUP_SIZE;

        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            int start = groupIndex * GROUP_SIZE;
            int end = Math.min(start + GROUP_SIZE, allVideoIdList.size());
            List<VideoDynamicDO> videoDynamicDOList = bilibiliApi.getVideoInfo(allVideoIdList.subList(start, end));
            allVideoDynamicDOList.addAll(videoDynamicDOList);
        }
    }

    /**
     * 并发地调用API获取结果
     */
    public void getData() throws IOException {
        BilibiliApi bilibiliApi = new BilibiliApi();

        // 这个任务的线程池不能在运行中扩容，使用两种配置中的较大值。
        int executorsSize = Math.max(EXECUTORS_SIZE_PROXY, EXECUTORS_SIZE_OFFICIAL);

        ExecutorService executorService = Executors.newFixedThreadPool(executorsSize);

        List<Future<List<VideoDynamicDO>>> futures = new ArrayList<>();
        int groupCount = (allVideoIdList.size() + GROUP_SIZE - 1) / GROUP_SIZE;

        // 分组并发发起请求
        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            int start = groupIndex * GROUP_SIZE;
            int end = Math.min(start + GROUP_SIZE, allVideoIdList.size());
            List<Long> sublist = allVideoIdList.subList(start, end);
            logger.debug("Start process for getting dynamic data from {} to {}", start, end);
            // 提交任务到线程池
            futures.add(executorService.submit(() -> bilibiliApi.getVideoInfo(sublist)));
        }

        // 收集结果
        for (Future<List<VideoDynamicDO>> future : futures) {
            try {
                allVideoDynamicDOList.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                // 处理异常
                System.err.println("Error occurred while fetching video info: " + e.getMessage());
            }
        }

        logger.info("Successfully finish all processes for getting dynamic data. Count of processes: {}", futures.size());
        // 关闭线程池
        executorService.shutdown();
    }

    /**
     * 并发地调用API获取结果，并在每个批次完成后立即写入每日动态表。
     */
    public int getDataAndInsert(PostgresDao postgresDao) throws IOException {
        BilibiliApi bilibiliApi = new BilibiliApi();
        // 这个任务的线程池不能在运行中扩容，使用两种配置中的较大值。
        int executorsSize = Math.max(EXECUTORS_SIZE_PROXY, EXECUTORS_SIZE_OFFICIAL);
        ExecutorService executorService = Executors.newFixedThreadPool(executorsSize);
        CompletionService<List<VideoDynamicDO>> completionService = new ExecutorCompletionService<>(executorService);
        int groupCount = (allVideoIdList.size() + GROUP_SIZE - 1) / GROUP_SIZE;
        int submittedCount = 0;
        int failedInsertCount = 0;
        int insertBatchSize = PostgresDao.getInsertBatchSize();
        List<VideoDynamicDO> pendingInsertList = new ArrayList<>(insertBatchSize);
        String recordDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        try {
            // 分组并发发起请求
            for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
                int start = groupIndex * GROUP_SIZE;
                int end = Math.min(start + GROUP_SIZE, allVideoIdList.size());
                List<Long> sublist = allVideoIdList.subList(start, end);
                logger.debug("Start process for getting dynamic data from {} to {}", start, end);
                completionService.submit(() -> bilibiliApi.getVideoInfo(sublist));
                submittedCount++;
            }

            // 按完成顺序收集并写入结果，避免等待所有批次结束后再落库。
            for (int completedCount = 0; completedCount < submittedCount; completedCount++) {
                try {
                    List<VideoDynamicDO> videoDynamicDOList = completionService.take().get();
                    allVideoDynamicDOList.addAll(videoDynamicDOList);
                    pendingInsertList.addAll(videoDynamicDOList);
                    failedInsertCount += flushReadyDailyDynamicBatches(
                            postgresDao,
                            pendingInsertList,
                            recordDate,
                            insertBatchSize
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted while fetching video dynamic data.", e);
                    break;
                } catch (ExecutionException e) {
                    logger.error("Error occurred while fetching video info.", e);
                }
            }
            failedInsertCount += flushDailyDynamicBatch(postgresDao, pendingInsertList, recordDate);

            logger.info("Successfully finish all processes for getting and inserting dynamic data. Count of processes: {}", submittedCount);
            return failedInsertCount;
        } finally {
            executorService.shutdown();
        }
    }

    private int flushReadyDailyDynamicBatches(
            PostgresDao postgresDao,
            List<VideoDynamicDO> pendingInsertList,
            String recordDate,
            int insertBatchSize
    ) {
        int failedInsertCount = 0;
        while (pendingInsertList.size() >= insertBatchSize) {
            List<VideoDynamicDO> batch = new ArrayList<>(pendingInsertList.subList(0, insertBatchSize));
            pendingInsertList.subList(0, insertBatchSize).clear();
            failedInsertCount += flushDailyDynamicBatch(postgresDao, batch, recordDate);
        }
        return failedInsertCount;
    }

    private int flushDailyDynamicBatch(
            PostgresDao postgresDao,
            List<VideoDynamicDO> videoDynamicDOList,
            String recordDate
    ) {
        if (videoDynamicDOList.isEmpty()) {
            return 0;
        }
        try {
            postgresDao.insertDailyDynamic(videoDynamicDOList, recordDate);
            videoDynamicDOList.clear();
            return 0;
        } catch (SQLException e) {
            logger.error("Error occurred while inserting video dynamic batch.", e);
            videoDynamicDOList.clear();
            return 1;
        }
    }

    public List<VideoDynamicDO> getAllVideoDynamicDOList() {
        return allVideoDynamicDOList;
    }

    /**
     * 动态信息里面包含了用户信息，从这里提取，以便插入到用户的维度表。
     */
    public List<UserDO> getUserDOList() {
        return allVideoDynamicDOList.stream().map(VideoDynamicDO::userDO).distinct().toList();
    }
}

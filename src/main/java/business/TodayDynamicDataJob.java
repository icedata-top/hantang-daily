package business;

import api.BilibiliApi;
import dos.UserDO;
import dos.VideoDynamicDO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
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
    private static final int EXECUTORS_SIZE;
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
            GROUP_SIZE = Integer.parseInt(properties.getProperty("dynamic.group_size", "25"));
            EXECUTORS_SIZE = Integer.parseInt(properties.getProperty("dynamic.executors_size", "10"));
        } catch (IOException e) {
            e.fillInStackTrace();
            throw new RuntimeException("无法加载数据库配置文件", e);
        }
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
        ExecutorService executorService = Executors.newFixedThreadPool(EXECUTORS_SIZE);

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

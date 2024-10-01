package business;

import api.BilibiliApi;
import dos.VideoDynamicDO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    private static final int GROUP_SIZE = 10;

    public TodayDynamicDataJob(List<Long> allVideoIdList) {
        this.allVideoIdList = allVideoIdList;
        this.allVideoDynamicDOList = new ArrayList<>();
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
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        List<Future<List<VideoDynamicDO>>> futures = new ArrayList<>();
        int groupCount = (allVideoIdList.size() + GROUP_SIZE - 1) / GROUP_SIZE;

        // 分组并发发起请求
        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            int start = groupIndex * GROUP_SIZE;
            int end = Math.min(start + GROUP_SIZE, allVideoIdList.size());
            List<Long> sublist = allVideoIdList.subList(start, end);
            System.out.println("发起线程 " + start + "~" + end);
            // 提交任务到线程池
            futures.add(executorService.submit(() -> bilibiliApi.getVideoInfo(sublist)));
        }

        // 收集结果
        for (Future<List<VideoDynamicDO>> future : futures) {
            try {
                allVideoDynamicDOList.addAll(future.get());
                System.out.println("线程全部执行完毕");
            } catch (InterruptedException | ExecutionException e) {
                // 处理异常
                System.err.println("Error occurred while fetching video info: " + e.getMessage());
            }
        }

        // 关闭线程池
        executorService.shutdown();
    }

    public List<VideoDynamicDO> getAllVideoDynamicDOList() {
        return allVideoDynamicDOList;
    }
}

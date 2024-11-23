package thread;

import api.BilibiliApi;
import dos.VideoDynamicDO;
import dos.VideoWithPriorityDO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * 获取数据的线程。toGetDataQueue 的消费者，toInsertQueue 的生产者
 */
public class GetDataThread extends Thread {
    private static final Logger logger = LogManager.getLogger(GetDataThread.class);
    final PriorityBlockingQueue<VideoWithPriorityDO> toGetDataQueue;
    final ArrayBlockingQueue<VideoDynamicDO> toInsertQueue;

    public GetDataThread (PriorityBlockingQueue<VideoWithPriorityDO> toGetDataQueue, ArrayBlockingQueue<VideoDynamicDO> toInsertQueue) {
        this.toGetDataQueue = toGetDataQueue;
        this.toInsertQueue = toInsertQueue;
    }

    @Override
    public void run() {
        BilibiliApi bilibiliApi = new BilibiliApi();  // 任务处理类
        while (true) {
            try {
                List<VideoWithPriorityDO> taskBatch = new ArrayList<>();
                // 从队列中最多取40个任务（或队列中现有的所有任务）
                synchronized (toGetDataQueue) {
                    toGetDataQueue.drainTo(taskBatch, 40);
                }

                // 如果没有任务可以处理，休眠一段时间再试
                if (taskBatch.isEmpty()) {
                    Thread.sleep(1000); // 可调整的休眠时间
                    continue;
                }

                // 使用 Stream 提取 aid 列表
                List<Long> aidList = taskBatch.stream()
                        .map(VideoWithPriorityDO::aid)
                        .toList();
                // 调用API处理任务
                logger.info("GetDataThread thread ready to get video info from Bilibili API. size: {}", taskBatch.size());
                List<VideoDynamicDO> result = bilibiliApi.getVideoInfo(aidList);

                // 将结果加入保存队列
                toInsertQueue.addAll(result);
            } catch (InterruptedException | IOException e) {
                logger.error(e);
            }
        }
    }
}
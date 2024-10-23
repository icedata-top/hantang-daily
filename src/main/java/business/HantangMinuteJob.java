package business;

import api.BilibiliApi;
import dao.MysqlDao;
import dos.VideoDynamicDO;
import dos.VideoWithPriorityDO;
import enums.DynamicInsertTableEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.stream.Collectors;


public class HantangMinuteJob {
    private static final PriorityBlockingQueue<VideoWithPriorityDO> toGetDataQueue = new PriorityBlockingQueue<>(11, new PriorityComparator());
    private static final ArrayBlockingQueue<VideoDynamicDO> toInsertQueue = new ArrayBlockingQueue<>(5000);

    // 线程池 及 线程池大小
    private static final int NUM_GET_DATA_THREADS = 4;
    private static final ExecutorService getDataPool = Executors.newFixedThreadPool(NUM_GET_DATA_THREADS);
    private static final int NUM_INSERT_THREADS = 2;
    private static final ExecutorService insertPoll = Executors.newFixedThreadPool(NUM_INSERT_THREADS);


    public static void main(String[] args) throws IOException {
        toGetDataQueue.add(new VideoWithPriorityDO(6009789L, 1) );
        toGetDataQueue.add(new VideoWithPriorityDO(113345092984387L, 1) );

        // 创建多个 GetDataTask
        for (int i = 0; i < NUM_GET_DATA_THREADS; i++) {
            getDataPool.submit(new GetDataThread());
        }

        // 创建多个 InsertTask
        for (int i = 0; i < NUM_INSERT_THREADS; i++) {
            insertPoll.submit(new InsertThread());
        }
    }

    /**
     * 获取数据的线程。aidTaskToExcQueue 的消费者，recordToSaveQueue 的生产者
     */
    static class GetDataThread extends Thread {
        private static final Logger logger = LogManager.getLogger(GetDataThread.class);
        @Override
        public void run() {
            BilibiliApi bilibiliApi = new BilibiliApi();  // 任务处理类
            while (true) {
                try {
                    List<VideoWithPriorityDO> taskBatch = new ArrayList<>();
                    // 从队列中最多取40个任务（或队列中现有的所有任务）
                    toGetDataQueue.drainTo(taskBatch, 40);

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

    /**
     * 插入数据的线程。aidTaskToExcQueue 的消费者，recordToSaveQueue 的生产者
     */
    static class InsertThread extends Thread {
        private static final Logger logger = LogManager.getLogger(GetDataThread.class);
        @Override
        public void run() {
            try {
                MysqlDao mysqlDao = new MysqlDao();
                while (true) {
                    List<VideoDynamicDO> recordToInsert = new ArrayList<>();
                    toInsertQueue.drainTo(recordToInsert, 100);

                    // 如果没有任务可以处理，休眠一段时间再试
                    if (recordToInsert.isEmpty()) {
                        Thread.sleep(1000); // 可调整的休眠时间
                        continue;
                    }

                    logger.info("InsertThread thread ready to insert records to MySQL DB. size: {}", recordToInsert.size());
                    //mysqlDao.insertDynamic(recordToInsert, DynamicInsertTableEnum.MINUTE);
                }
            } catch (SQLException | ClassNotFoundException | InterruptedException e) {
                logger.error(e);
            }
        }
    }

    /**
     * 优先级比较器。优先级为0的排在最后，否则按数值从小到大排序。
     */
    static class PriorityComparator implements Comparator<VideoWithPriorityDO> {
        @Override
        public int compare(VideoWithPriorityDO o1, VideoWithPriorityDO o2) {
            int p1 = o1.priority();
            int p2 = o2.priority();

            if (p1 == 0 && p2 == 0) {
                return 0;
            }
            if (p1 == 0) {
                return 1;
            }
            if (p2 == 0) {
                return -1;
            }
            return Integer.compare(p1, p2);
        }
    }
}

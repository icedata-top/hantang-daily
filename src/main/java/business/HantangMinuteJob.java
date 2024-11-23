package business;

import api.BilibiliApi;
import dao.MysqlDao;
import dos.VideoDynamicDO;
import dos.VideoWithPriorityDO;
import thread.GetDataThread;
import thread.GetObservingVideoThread;
import thread.InsertThread;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.*;


public class HantangMinuteJob {
    private static final PriorityBlockingQueue<VideoWithPriorityDO> toGetDataQueue = new PriorityBlockingQueue<>(11, new PriorityComparator());
    private static final ArrayBlockingQueue<VideoDynamicDO> toInsertQueue = new ArrayBlockingQueue<>(5000);

    // 线程池 及 线程池大小
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final int NUM_GET_DATA_THREADS;
    private static final ExecutorService getDataPool;
    private static final int NUM_INSERT_THREADS;
    private static final ExecutorService insertPoll;

    static {
        try {
            // 加载配置文件
            Properties properties = new Properties();
            FileInputStream input = new FileInputStream("config.properties");
            properties.load(input);

            // 读取配置
            NUM_GET_DATA_THREADS = Integer.parseInt(properties.getProperty("minute.num_get_data_threads", "4"));
            NUM_INSERT_THREADS = Integer.parseInt(properties.getProperty("minute.num_insert_threads", "2"));

            // 初始化固定线程池
            getDataPool = Executors.newFixedThreadPool(NUM_GET_DATA_THREADS);
            insertPoll = Executors.newFixedThreadPool(NUM_INSERT_THREADS);
        } catch (IOException e) {
            e.fillInStackTrace();
            throw new RuntimeException("无法加载数据库配置文件", e);
        }
    }


    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException {
        // 创建每分钟的任务，从视频静态信息表里读取，哪些视频在本分钟需要被监测，目前只支持优先度=1的
        GetObservingVideoThread oThread = new GetObservingVideoThread(toGetDataQueue, new MysqlDao());
        scheduler.scheduleWithFixedDelay(oThread, 0, 60, TimeUnit.SECONDS);

        // 创建多个 GetDataTask
        for (int i = 0; i < NUM_GET_DATA_THREADS; i++) {
            getDataPool.submit(new GetDataThread(toGetDataQueue, toInsertQueue));
        }

        // 创建多个 InsertTask
        for (int i = 0; i < NUM_INSERT_THREADS; i++) {
            insertPoll.submit(new InsertThread(toInsertQueue));
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

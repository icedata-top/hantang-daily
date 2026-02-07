package business;

import api.ApiStateListener;
import api.BilibiliApi;
import dao.MysqlDao;
import dos.VideoDynamicDO;
import dos.VideoWithPriorityDO;
import thread.GetDataThread;
import thread.GetObservingVideoThread;
import thread.InsertThread;
import utils.DynamicThreadPool;

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

    // 线程池配置
    private static final int NUM_GET_DATA_THREADS_OFFICIAL;
    private static final int NUM_INSERT_THREADS_OFFICIAL;
    private static final int NUM_GET_DATA_THREADS_PROXY;
    private static final int NUM_INSERT_THREADS_PROXY;

    // 动态线程池
    private static final DynamicThreadPool getDataPool;
    private static final DynamicThreadPool insertPool;

    static {
        try {
            // 加载配置文件
            Properties properties = new Properties();
            FileInputStream input = new FileInputStream("config.properties");
            properties.load(input);

            // 读取官方 API 和代理 API 的不同配置
            NUM_GET_DATA_THREADS_OFFICIAL = Integer.parseInt(
                properties.getProperty("minute.num_get_data_threads.official", "4"));
            NUM_INSERT_THREADS_OFFICIAL = Integer.parseInt(
                properties.getProperty("minute.num_insert_threads.official", "2"));
            NUM_GET_DATA_THREADS_PROXY = Integer.parseInt(
                properties.getProperty("minute.num_get_data_threads.proxy", "12"));
            NUM_INSERT_THREADS_PROXY = Integer.parseInt(
                properties.getProperty("minute.num_insert_threads.proxy", "6"));

            // 初始化动态线程池（使用官方 API 配置）
            getDataPool = new DynamicThreadPool("MinuteJob-GetData", NUM_GET_DATA_THREADS_OFFICIAL);
            insertPool = new DynamicThreadPool("MinuteJob-Insert", NUM_INSERT_THREADS_OFFICIAL);

            // 注册 API 状态监听器
            BilibiliApi.addStateListener(new ApiStateListener() {
                @Override
                public void onApiStateChanged(boolean useProxy) {
                    int getDataThreads = useProxy ? NUM_GET_DATA_THREADS_PROXY : NUM_GET_DATA_THREADS_OFFICIAL;
                    int insertThreads = useProxy ? NUM_INSERT_THREADS_PROXY : NUM_INSERT_THREADS_OFFICIAL;

                    getDataPool.resize(getDataThreads);
                    insertPool.resize(insertThreads);
                }
            });
        } catch (IOException e) {
            e.fillInStackTrace();
            throw new RuntimeException("无法加载数据库配置文件", e);
        }
    }


    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException {
        // 创建每分钟的任务，从视频静态信息表里读取，哪些视频在本分钟需要被监测，目前只支持优先度=1的
        GetObservingVideoThread oThread = new GetObservingVideoThread(toGetDataQueue, new MysqlDao());
        scheduler.scheduleWithFixedDelay(oThread, 0, 60, TimeUnit.SECONDS);

        // 提交 GetDataThread 任务
        for (int i = 0; i < NUM_GET_DATA_THREADS_OFFICIAL; i++) {
            getDataPool.submit(new GetDataThread(toGetDataQueue, toInsertQueue));
        }

        // 提交 InsertThread 任务
        for (int i = 0; i < NUM_INSERT_THREADS_OFFICIAL; i++) {
            insertPool.submit(new InsertThread(toInsertQueue));
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

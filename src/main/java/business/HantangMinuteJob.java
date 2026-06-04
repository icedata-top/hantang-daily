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
            NUM_GET_DATA_THREADS_OFFICIAL = getPositiveIntProperty(
                    properties,
                    "minute.num_get_data_threads.official",
                    "minute.num_get_data_threads",
                    4
            );
            NUM_INSERT_THREADS_OFFICIAL = getPositiveIntProperty(
                    properties,
                    "minute.num_insert_threads.official",
                    "minute.num_insert_threads",
                    2
            );
            NUM_GET_DATA_THREADS_PROXY = getPositiveIntProperty(properties, "minute.num_get_data_threads.proxy", 12);
            NUM_INSERT_THREADS_PROXY = getPositiveIntProperty(properties, "minute.num_insert_threads.proxy", 6);

            // 初始化动态线程池（使用官方 API 配置）
            getDataPool = new DynamicThreadPool(
                    "MinuteJob-GetData",
                    NUM_GET_DATA_THREADS_OFFICIAL,
                    Math.max(NUM_GET_DATA_THREADS_OFFICIAL, NUM_GET_DATA_THREADS_PROXY)
            );
            insertPool = new DynamicThreadPool(
                    "MinuteJob-Insert",
                    NUM_INSERT_THREADS_OFFICIAL,
                    Math.max(NUM_INSERT_THREADS_OFFICIAL, NUM_INSERT_THREADS_PROXY)
            );

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
        int maxGetDataThreads = Math.max(NUM_GET_DATA_THREADS_OFFICIAL, NUM_GET_DATA_THREADS_PROXY);
        for (int i = 0; i < maxGetDataThreads; i++) {
            getDataPool.submit(new GetDataThread(toGetDataQueue, toInsertQueue, getDataPool, i));
        }

        // 提交 InsertThread 任务
        int maxInsertThreads = Math.max(NUM_INSERT_THREADS_OFFICIAL, NUM_INSERT_THREADS_PROXY);
        for (int i = 0; i < maxInsertThreads; i++) {
            insertPool.submit(new InsertThread(toInsertQueue, insertPool, i));
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
     * 优先级比较器。顺序为 1..720、0、-2。
     */
    static class PriorityComparator implements Comparator<VideoWithPriorityDO> {
        @Override
        public int compare(VideoWithPriorityDO o1, VideoWithPriorityDO o2) {
            int rank1 = getPriorityRank(o1.priority());
            int rank2 = getPriorityRank(o2.priority());

            if (rank1 != rank2) {
                return Integer.compare(rank1, rank2);
            }
            return Integer.compare(o1.priority(), o2.priority());
        }

        private int getPriorityRank(int priority) {
            if (priority >= 1 && priority <= 720) {
                return 0;
            }
            if (priority == 0) {
                return 1;
            }
            if (priority == -2) {
                return 2;
            }
            return 3;
        }
    }
}

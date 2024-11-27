package thread;

import dao.MysqlDao;
import dos.VideoWithPriorityDO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * 获取当前监测视频列表的线程。toGetDataQueue 的生产者
 */
public class GetObservingVideoThread extends Thread {
    private static final Logger logger = LogManager.getLogger(GetObservingVideoThread.class);
    final int PRIORITY = 1;
    final int[] PRIORITY_ARRAY = {1, 15, 60};
    final PriorityBlockingQueue<VideoWithPriorityDO> toGetDataQueue;
    final MysqlDao mysqlDao; // 这里的dao对象是入参传进来的，因为GetVideoListThread不是多线程的，是单线程的。

    public GetObservingVideoThread(PriorityBlockingQueue<VideoWithPriorityDO> toGetDataQueue, MysqlDao mysqlDao) {
        this.toGetDataQueue = toGetDataQueue;
        this.mysqlDao = mysqlDao;
    }

    @Override
    public void run() {
        LocalDateTime localDateTime = LocalDateTime.now();
        int minute = localDateTime.getMinute();
        try {
            // 在整15分钟的时候运行优先级为15的任务，以此类推。
            for (int priority : PRIORITY_ARRAY) {
                if (minute % priority != 0) {
                    continue;
                }
                List<Long> observingVideoList = mysqlDao.getObservingVideoList(priority);
                for (Long aid : observingVideoList) {
                    if (aid == null) {
                        continue;
                    }
                    toGetDataQueue.add(new VideoWithPriorityDO(aid, priority));
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
    }
}

package thread;

import dao.PostgresDao;
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
    final PriorityBlockingQueue<VideoWithPriorityDO> toGetDataQueue;
    final PostgresDao postgresDao; // 这里的dao对象是入参传进来的，因为GetVideoListThread不是多线程的，是单线程的。

    public GetObservingVideoThread(PriorityBlockingQueue<VideoWithPriorityDO> toGetDataQueue, PostgresDao postgresDao) {
        this.toGetDataQueue = toGetDataQueue;
        this.postgresDao = postgresDao;
    }

    @Override
    public void run() {
        LocalDateTime localDateTime = LocalDateTime.now();
        int minuteOfDay = localDateTime.getHour() * 60 + localDateTime.getMinute();
        try {
            List<VideoWithPriorityDO> observingVideoList = postgresDao.getDueMinuteCollectionVideoList(minuteOfDay);
            for (VideoWithPriorityDO videoWithPriorityDO : observingVideoList) {
                if (videoWithPriorityDO == null) {
                    continue;
                }
                toGetDataQueue.add(videoWithPriorityDO);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
    }
}

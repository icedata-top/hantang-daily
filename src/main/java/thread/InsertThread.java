package thread;

import dao.PostgresDao;
import dos.VideoDynamicDO;
import enums.DynamicInsertTableEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.DynamicThreadPool;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * 插入数据的线程。toInsertQueue 的消费者
 */
public class InsertThread extends Thread {
    private static final Logger logger = LogManager.getLogger(InsertThread.class);
    final ArrayBlockingQueue<VideoDynamicDO> toInsertQueue;
    final DynamicThreadPool dynamicThreadPool;
    final int workerIndex;

    public InsertThread(ArrayBlockingQueue<VideoDynamicDO> toInsertQueue) {
        this(toInsertQueue, null, 0);
    }

    public InsertThread(ArrayBlockingQueue<VideoDynamicDO> toInsertQueue, DynamicThreadPool dynamicThreadPool, int workerIndex) {
        this.toInsertQueue = toInsertQueue;
        this.dynamicThreadPool = dynamicThreadPool;
        this.workerIndex = workerIndex;
    }

    @Override
    public void run() {
        try {
            PostgresDao postgresDao = new PostgresDao();
            while (true) {
                if (!isWorkerActive()) {
                    Thread.sleep(1000);
                    continue;
                }
                List<VideoDynamicDO> recordToInsert = new ArrayList<>();
                synchronized (toInsertQueue) {
                    toInsertQueue.drainTo(recordToInsert, 100);
                }

                // 如果没有任务可以处理，休眠一段时间再试
                if (recordToInsert.isEmpty()) {
                    Thread.sleep(1000); // 可调整的休眠时间
                    continue;
                }

                logger.info("InsertThread thread ready to insert records to PostgreSQL DB. size: {}", recordToInsert.size());
                postgresDao.insertDynamic(recordToInsert, DynamicInsertTableEnum.MINUTE);
            }
        } catch (SQLException | ClassNotFoundException | InterruptedException e) {
            logger.error(e);
        }
    }

    private boolean isWorkerActive() {
        return dynamicThreadPool == null || dynamicThreadPool.isWorkerActive(workerIndex);
    }
}

import business.TodayDynamicDataJob;
import business.TodayStaticDataJob;
import dao.MysqlDao;
import dos.VideoDynamicDO;
import dos.VideoStaticDO;
import enums.DynamicInsertTableEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        String command = "full";
        if (args.length > 0) {
            command = args[0];
        }
        long startTime = System.currentTimeMillis();
        logger.info("Successfully START main process. command: {}", command);
        if ("full".equalsIgnoreCase(command)) {
            fullTask();
        } else if ("static".equalsIgnoreCase(command)) {
            normalStaticTasks();
        } else if ("dynamic".equalsIgnoreCase(command)) {
            normalDynamicTask();
        }
        long deltaTime = System.currentTimeMillis() - startTime;
        logger.info("Successfully FINISH main process. Time: {} ms. command: {}", deltaTime, command);
    }

    /**
     * 普通静态任务。搜索关键词，获取1日内的静态信息。
     */
    private static void normalStaticTasks() {
        try {
            // (1) 增量获取视频静态信息
            TodayStaticDataJob todayStaticDataJob = new TodayStaticDataJob();
            todayStaticDataJob.getData();
            List<VideoStaticDO> videoStaticDOList = todayStaticDataJob.getAllVideoStaticDOList();

            // (2) 视频静态信息落库
            MysqlDao mysqlDao = new MysqlDao();
            mysqlDao.insertStatic(videoStaticDOList);

            mysqlDao.insertTypeDim(todayStaticDataJob.getTypeDOList());
        } catch (SQLException e) {
            logger.error("SQLException (normalStaticTasks): {}", String.valueOf(e));
        } catch (Exception e) {
            logger.error(e);
        }
    }

    /**
     * 普通动态任务。遍历所有视频，获取动态信息。
     */
    private static void normalDynamicTask() {
        try {
            MysqlDao mysqlDao = new MysqlDao();
            // (3) 取出视频列表
            List<Long> allVideoIdList = mysqlDao.getAllVideoIdList();

            // (4) 全量获取动态数据
            TodayDynamicDataJob todayDynamicDataJob = new TodayDynamicDataJob(allVideoIdList);
            todayDynamicDataJob.getData();
            List<VideoDynamicDO> videoDynamicDOList = todayDynamicDataJob.getAllVideoDynamicDOList();

            // (5) 全量插入动态数据
            mysqlDao.insertDynamic(videoDynamicDOList, DynamicInsertTableEnum.DAILY);

            // (6) 分区信息
            mysqlDao.insertUserDim(todayDynamicDataJob.getUserDOList());
        } catch (SQLException e) {
            logger.error("SQLException (normalDynamicTask): {}", String.valueOf(e));
        } catch (Exception e) {
            logger.error(e);
        }
    }

    /**
     * 普通完整任务。先搜索关键词，获取静态信息；再遍历所有视频，获取动态信息。
     */
    private static void fullTask() {
        try {
            // (1) 增量获取视频静态信息
            TodayStaticDataJob todayStaticDataJob = new TodayStaticDataJob();
            todayStaticDataJob.getData();
            List<VideoStaticDO> videoStaticDOList = todayStaticDataJob.getAllVideoStaticDOList();

            // (2) 视频静态信息落库
            MysqlDao mysqlDao = new MysqlDao();
            mysqlDao.insertStatic(videoStaticDOList);

            // (3) 取出视频列表
            List<Long> allVideoIdList = mysqlDao.getAllVideoIdList();

            // (4) 全量获取动态数据
            TodayDynamicDataJob todayDynamicDataJob = new TodayDynamicDataJob(allVideoIdList);
            todayDynamicDataJob.getData();
            List<VideoDynamicDO> videoDynamicDOList = todayDynamicDataJob.getAllVideoDynamicDOList();

            // (5) 全量插入动态数据
            mysqlDao.insertDynamic(videoDynamicDOList, DynamicInsertTableEnum.DAILY);

            // (6) 插入用户和分区信息
            mysqlDao.insertTypeDim(todayStaticDataJob.getTypeDOList());
            mysqlDao.insertUserDim(todayDynamicDataJob.getUserDOList());
        } catch (SQLException e) {
            logger.error("SQLException: {}", String.valueOf(e));
        } catch (IOException e) {
            logger.error("IOException: {}", String.valueOf(e));
        } catch (ClassNotFoundException e) {
            logger.error("Cannot load class for JDBC. ClassNotFoundException.");
        } catch (Exception e) {
            logger.error(e);
        }
    }
}

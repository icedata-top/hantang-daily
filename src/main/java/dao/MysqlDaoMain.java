package dao;

import api.BilibiliApi;
import dos.VideoStaticDO;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class MysqlDaoMain {
    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException {
        System.out.println("Hello world");
        BilibiliApi bilibiliApi = new BilibiliApi();
        List<VideoStaticDO> videoStaticDOList = bilibiliApi.getSearchResult("洛天依", 1, 50);
        System.out.println("获取洛天依TOP50视频数据完成");
        for (VideoStaticDO videoStaticDO : videoStaticDOList) {
            System.out.println(videoStaticDO.aid() + "\t" + videoStaticDO.userDO().mid() + "\t" + videoStaticDO.title());
        }

        MysqlDao mysqlDao = new MysqlDao();
        mysqlDao.insertStatic(videoStaticDOList);

        System.out.println("插入数据完成");

        List<Long> allVideoIdList = mysqlDao.getAllVideoIdList();

        System.out.println("取出数据完成");

        System.out.println(allVideoIdList);

//        List<Long> allVideoIdList = Arrays.asList(
//                6009789L, 3905462L, 87739724L, 501314235L, 782181934L, 824799252L,
//                862819271L, 938641073L, 482844L, 2129461L, 2951993L, 3370007L,
//                9372087L, 43426592L, 48624233L, 78977256L, 5800806L, 795138286L
//        );
//        TodayDynamicDataJob todayDynamicDataJob = new TodayDynamicDataJob(allVideoIdList);
//        todayDynamicDataJob.getData();
//        List<VideoDynamicDO> videoDynamicDOList = todayDynamicDataJob.getAllVideoDynamicDOList();
//        System.out.println(videoDynamicDOList);


    }
}
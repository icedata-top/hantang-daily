import api.BilibiliApi;
import dao.MysqlDao;
import dos.VideoDynamicDO;
import dos.VideoStaticDO;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException {
        // (1) 增量获取视频静态信息
        BilibiliApi bilibiliApi = new BilibiliApi();
        List<VideoStaticDO> videoStaticDOList = bilibiliApi.getSearchResult("洛天依", 1, 50);

        // (2) 视频静态信息落库
        MysqlDao mysqlDao = new MysqlDao();
        mysqlDao.insertStatic(videoStaticDOList);

        // (3) 取出视频列表
        List<Long> allVideoIdList = mysqlDao.getAllVideoIdList();

        // (4) 全量获取动态数据
        List<VideoDynamicDO> videoDynamicDOList = bilibiliApi.getVideoInfo(allVideoIdList);

        // (5) 全量插入动态数据
        mysqlDao.insertDynamic(videoDynamicDOList);
    }
}

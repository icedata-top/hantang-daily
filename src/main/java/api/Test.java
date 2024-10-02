package api;

import dao.MysqlDao;
import dos.VideoStaticDO;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class Test {
    // 示例调用
    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException {
        BilibiliApi bilibiliApi = new BilibiliApi();
        List<VideoStaticDO> videoStaticDOList = bilibiliApi.getSearchResult("洛天依", 1, 42);
        for (VideoStaticDO videoStaticDO : videoStaticDOList) {
            System.out.println(videoStaticDO.aid() + "\t"+ videoStaticDO.title());
        }
        MysqlDao mysqlDao = new MysqlDao();
        mysqlDao.insertStatic(videoStaticDOList);
    }
}

package dao;

import api.BilibiliApi;
import dos.VideoDynamicDO;
import dos.VideoStaticDO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class MysqlDao {
    private static final String URL_LOCAL;
    private static final String USER_LOCAL;
    private static final String PASSWORD_LOCAL;
    private static final Logger logger = LogManager.getLogger(MysqlDao.class);

    static {
        try {
            // 加载配置文件
            Properties properties = new Properties();
            FileInputStream input = new FileInputStream("config.properties");
            properties.load(input);

            // 读取配置
            URL_LOCAL = properties.getProperty("db.url_local");
            USER_LOCAL = properties.getProperty("db.user_local");
            PASSWORD_LOCAL = properties.getProperty("db.password_local");

        } catch (IOException e) {
            e.fillInStackTrace();
            throw new RuntimeException("无法加载数据库配置文件", e);
        }
    }

    private final Connection connection;

    /**
     * 写表时分批的大小
     */
    private static final int INSERT_SIZE = 2000;
    /**
     * 读表时分批的大小
     */
    private static final int QUERY_SIZE = 10000;

    public MysqlDao() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");

        connection = DriverManager.getConnection(URL_LOCAL, USER_LOCAL, PASSWORD_LOCAL);
        logger.info("Successfully established connection to MySQL via JDBC.");
    }

    /**
     * 获取video_static中视频数量
     *
     * @return 所有视频AV号列表
     */
    private int getVideoCount() throws SQLException {
        String sql = "SELECT COUNT(*) AS count FROM video_static;";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.executeQuery();
        ResultSet resultSet = preparedStatement.getResultSet();
        int count = 0;
        if (resultSet.next()) {
            count = resultSet.getInt("count");
        }
        logger.info("Successfully query count of rows from video_static. count: {}", count);
        return count;
    }

    /**
     * 获取video_static中所有视频AV号列表
     *
     * @return 所有视频AV号列表
     */
    public List<Long> getAllVideoIdList() throws SQLException {
        List<Long> allVideoIdList = new ArrayList<>();
        int count = getVideoCount();
        int pageCount = (count + QUERY_SIZE - 1) / QUERY_SIZE;
        String sql = "SELECT `aid` FROM video_static LIMIT ?, ?;";
        // 使用for循环分页查询
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            // 计算每页的偏移量offset
            int offset = pageIndex * QUERY_SIZE;

            // 创建PreparedStatement并设置参数
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, offset);
            preparedStatement.setInt(2, QUERY_SIZE);

            // 执行查询
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                allVideoIdList.add(resultSet.getLong("aid"));
            }

            // 关闭ResultSet和PreparedStatement
            resultSet.close();
            preparedStatement.close();
        }
        return allVideoIdList;
    }

    /**
     * 插入video_static
     *
     * @param videoStaticDOList 视频静态信息列表
     */
    public void insertStatic(List<VideoStaticDO> videoStaticDOList) throws SQLException {
        String sql = "INSERT INTO video_static (aid, bvid, pubdate, title, description, tag, pic, type_id, user_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "bvid = VALUES(bvid), pubdate = VALUES(pubdate), title = VALUES(title), description = VALUES(description), " +
                "tag = VALUES(tag), pic = VALUES(pic), type_id = VALUES(type_id), user_id = VALUES(user_id);";

        // 创建PreparedStatement
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int count = 0;

            for (VideoStaticDO videoStaticDO : videoStaticDOList) {
                preparedStatement.setLong(1, videoStaticDO.aid());
                preparedStatement.setString(2, videoStaticDO.bvid());
                preparedStatement.setInt(3, videoStaticDO.pubdate());
                preparedStatement.setString(4, videoStaticDO.title());
                preparedStatement.setString(5, videoStaticDO.description());
                preparedStatement.setString(6, videoStaticDO.tag());
                preparedStatement.setString(7, videoStaticDO.pic());
                preparedStatement.setInt(8, videoStaticDO.typeDO().typeId());
                preparedStatement.setLong(9, videoStaticDO.userDO().mid());

                // 添加到批量中
                preparedStatement.addBatch();
                count++;

                // 当批量大小达到设定值时，执行批量插入
                if (count % INSERT_SIZE == 0) {
                    preparedStatement.executeBatch();
                }
            }

            // 插入剩余的记录
            preparedStatement.executeBatch();
        }
    }

    /**
     * 插入video_dynamic
     *
     * @param videoDynamicDOList 视频静态信息列表
     */
    public void insertDynamic(List<VideoDynamicDO> videoDynamicDOList) throws SQLException {
        String sql = "INSERT INTO video_dynamic (record_date, aid, bvid, coin, favorite, danmaku, view, reply, share, `like`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String today = dateFormat.format(new Date());

        // 创建PreparedStatement
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int count = 0;

            for (VideoDynamicDO video : videoDynamicDOList) {
                preparedStatement.setString(1, today);  // record_date 字符串，具体可以根据需求调整
                preparedStatement.setLong(2, video.aid());
                preparedStatement.setString(3, video.bvid());
                preparedStatement.setInt(4, video.coin());
                preparedStatement.setInt(5, video.favorite());
                preparedStatement.setInt(6, video.danmaku());
                preparedStatement.setInt(7, video.view());
                preparedStatement.setInt(8, video.reply());
                preparedStatement.setInt(9, video.share());
                preparedStatement.setInt(10, video.like());

                // 添加到批量中
                preparedStatement.addBatch();
                count++;

                // 当批量大小达到设定值时，执行批量插入
                if (count % INSERT_SIZE == 0) {
                    preparedStatement.executeBatch();
                }
            }

            // 插入剩余的记录
            preparedStatement.executeBatch();
        }
    }
}

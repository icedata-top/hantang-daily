package dao;

import dos.VideoStaticDO;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MysqlDao {
    private static final String URL_LOCAL;
    private static final String USER_LOCAL;
    private static final String PASSWORD_LOCAL;

    static {
        try {
            // 加载配置文件
            Properties properties = new Properties();
            FileInputStream input = new FileInputStream("config.properties");
            properties.load(input);

            // 读取配置
            URL_LOCAL = properties.getProperty("db.url");
            USER_LOCAL = properties.getProperty("db.user");
            PASSWORD_LOCAL = properties.getProperty("db.password");

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
        System.out.println("成功连接到数据库！");
    }

    /**
     * 获取video_static中视频数量
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
        return count;
    }

    /**
     * 获取video_static中所有视频AV号列表
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

}

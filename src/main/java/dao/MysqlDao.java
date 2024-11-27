package dao;

import dos.*;
import enums.DynamicInsertTableEnum;
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
            FileInputStream input = new FileInputStream("config.secret.properties");
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
    private static final int INSERT_SIZE = 4000;
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
     * 获取当前正在观测的视频列表
     *
     * @param priority 优先度
     * @return 视频AV号列表
     */
    public List<Long> getObservingVideoList(int priority) throws SQLException {
        String sql = "SELECT aid FROM video_static WHERE priority = ?;";
        // 创建PreparedStatement
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, priority);  // priority 优先度

            // 执行查询
            ResultSet resultSet = preparedStatement.executeQuery();
            List<Long> observingVideoList = new ArrayList<>();
            while (resultSet.next()) {
                observingVideoList.add(resultSet.getLong("aid"));
            }

            // 关闭ResultSet和PreparedStatement
            resultSet.close();
            return observingVideoList;
        }
    }

    /**
     * 获取歌曲的文本信息（标题、简介、标签）
     *
     * @param startTime 投稿时间的左端点
     * @param endTime 投稿时间的右端点
     */
    public List<VideoStaticDO> getVideoTextInfoList(int startTime, int endTime) throws SQLException {
        String sql = "SELECT `aid`,`title`,`tag`,`description` FROM video_static WHERE pubdate BETWEEN ? AND ?;";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, startTime);
            preparedStatement.setInt(2, endTime);

            // 执行查询
            ResultSet resultSet = preparedStatement.executeQuery();
            List<VideoStaticDO> resultList = new ArrayList<>();
            while (resultSet.next()) {
                resultList.add(new VideoStaticDO(
                        resultSet.getLong("aid"),
                        null,
                        -1,
                        resultSet.getString("title"),
                        resultSet.getString("description"),
                        resultSet.getString("tag"),
                        null,
                        null,
                        null
                ));
            }

            // 关闭ResultSet和PreparedStatement
            resultSet.close();
            return resultList;
        }
    }


    /**
     * 插入或更新video_static
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
            logger.info("Successfully insert into video_static. rows: {}", videoStaticDOList.size());
        }
    }

    /**
     * 插入video_dynamic
     *
     * @param videoDynamicDOList 视频静态信息列表
     */
    public void insertDynamic(List<VideoDynamicDO> videoDynamicDOList, DynamicInsertTableEnum tableEnum) throws SQLException {
        String sql = String.format("INSERT INTO %s (%s, aid, bvid, coin, favorite, danmaku, view, reply, share, `like`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", tableEnum.getTable(), tableEnum.getTimeColumn());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String today = dateFormat.format(new Date());
        int now = (int) (System.currentTimeMillis() / 1000L);

        // 创建PreparedStatement
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int count = 0;

            for (VideoDynamicDO video : videoDynamicDOList) {
                if (DynamicInsertTableEnum.DAILY.equals(tableEnum)) {
                    preparedStatement.setString(1, today);
                } else if (DynamicInsertTableEnum.MINUTE.equals(tableEnum)) {
                    preparedStatement.setInt(1, now);
                }
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
            logger.info("Successfully insert into video_dynamic. rows: {}", videoDynamicDOList.size());
        }
    }

    /**
     * 插入或更新dim_user
     */
    public void insertUserDim(List<UserDO> userDOList) throws SQLException {
        String sql = "INSERT INTO dim_user (user_id, name, face) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name), face = VALUES(face)";

        // 创建PreparedStatement
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int count = 0;

            for (UserDO user : userDOList) {
                preparedStatement.setLong(1, user.mid());   // user_id
                preparedStatement.setString(2, user.name()); // name
                preparedStatement.setString(3, user.face()); // face (可能为null)

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
            logger.info("Successfully insert into dim_user. rows: {}", userDOList.size());
        }
    }

    /**
     * 插入或更新dim_type
     */
    public void insertTypeDim(List<TypeDO> typeDOList) throws SQLException {
        String sql = "INSERT INTO dim_type (type_id, name) " +
                "VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name)";

        // 创建PreparedStatement
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int count = 0;

            for (TypeDO type : typeDOList) {
                preparedStatement.setInt(1, type.typeId());  // type_id
                preparedStatement.setString(2, type.typeName()); // name

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
            logger.info("Successfully insert into dim_type. rows: {}", typeDOList.size());
        }
    }

    /**
     * 插入到歌曲与虚拟歌手关系表 olap_rel_video_vocal
     */
    public void insertRelVideoVocal(List<VideoVocalDO> videoVocalDOList) throws SQLException {
        String sql = "INSERT INTO olap_rel_video_vocal (aid, vocal_id) " +
                "VALUES (?, ?)";

        // 创建PreparedStatement
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int count = 0;

            for (VideoVocalDO videoVocalDO : videoVocalDOList) {
                preparedStatement.setLong(1, videoVocalDO.aid());
                preparedStatement.setInt(2, videoVocalDO.vocalId());

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
            logger.info("Successfully insert into olap_rel_video_vocal. rows: {}", videoVocalDOList.size());
        }
    }
}

package dao;

import dos.UserDO;
import dos.VideoDynamicDO;
import dos.VideoStaticDO;
import dos.VideoWithPriorityDO;
import enums.DynamicInsertTableEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PostgresDao {
    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;
    private static final String DATABASE;
    private static final String SCHEMA;
    private static final String COLLECTION_STATE_TABLE;
    private static final String VIDEO_STATIC_TABLE;
    private static final String VIDEO_DAILY_TABLE;
    private static final String VIDEO_MINUTE_TABLE;
    private static final String DISCOVERED_USERS_TABLE;
    private static final int QUERY_SIZE = 10000;
    private static final int INSERT_SIZE = 4000;
    private static final Logger logger = LogManager.getLogger(PostgresDao.class);

    static {
        try {
            Properties properties = new Properties();
            FileInputStream input = new FileInputStream("config.secret.properties");
            properties.load(input);

            DATABASE = getRequiredProperty(properties, "postgres.database", "db.postgres.database");
            URL = getPostgresUrl(properties, DATABASE);
            USER = getRequiredProperty(properties, "postgres.user", "db.postgres.user");
            PASSWORD = getRequiredProperty(properties, "postgres.password", "db.postgres.password");
            SCHEMA = getOptionalProperty(properties, "postgres.schema", "db.postgres.schema", "");
            COLLECTION_STATE_TABLE = getQualifiedTableName(SCHEMA, "video_collection_state");
            VIDEO_STATIC_TABLE = getQualifiedTableName(SCHEMA, "video_static");
            VIDEO_DAILY_TABLE = getQualifiedTableName(SCHEMA, "video_daily");
            VIDEO_MINUTE_TABLE = getQualifiedTableName(SCHEMA, "video_minute");
            DISCOVERED_USERS_TABLE = getQualifiedTableName(SCHEMA, "discovered_users");
        } catch (IOException e) {
            e.fillInStackTrace();
            throw new RuntimeException("无法加载数据库配置文件", e);
        }
    }

    private final Connection connection;

    public PostgresDao() throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");

        connection = DriverManager.getConnection(URL, USER, PASSWORD);
        logger.info("Successfully established connection to PostgreSQL via JDBC.");
    }

    public static int getInsertBatchSize() {
        return INSERT_SIZE;
    }

    private static String getRequiredProperty(Properties properties, String key, String fallbackKey) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            value = properties.getProperty(fallbackKey);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required config property: " + key);
        }
        return value;
    }

    private static String getPostgresUrl(Properties properties, String database) {
        String url = getOptionalProperty(properties, "postgres.url", "db.postgres.url", "");
        if (!url.isBlank()) {
            requireUrlDatabase(url, database);
            return url;
        }

        String host = getRequiredProperty(properties, "postgres.host", "db.postgres.host");
        String port = getOptionalProperty(properties, "postgres.port", "db.postgres.port", "5432");
        return String.format("jdbc:postgresql://%s:%s/%s", host, port, database);
    }

    private static void requireUrlDatabase(String url, String database) {
        String pathPrefix = "/" + database;
        int pathIndex = url.indexOf(pathPrefix);
        if (pathIndex < 0) {
            throw new IllegalArgumentException("PostgreSQL URL must include postgres.database: " + database);
        }
        int databaseEndIndex = pathIndex + pathPrefix.length();
        if (databaseEndIndex < url.length()) {
            char nextChar = url.charAt(databaseEndIndex);
            if (nextChar != '?' && nextChar != ';') {
                throw new IllegalArgumentException("PostgreSQL URL must include postgres.database: " + database);
            }
        }
    }

    private static String getOptionalProperty(
            Properties properties,
            String key,
            String fallbackKey,
            String defaultValue
    ) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            value = properties.getProperty(fallbackKey);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static String quoteIdentifier(String identifier) {
        if (!identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL schema identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private static String getQualifiedTableName(String schema, String tableName) {
        if (schema.isBlank()) {
            return tableName;
        }
        return quoteIdentifier(schema) + "." + tableName;
    }

    /**
     * 获取每日动态采集的视频AV号列表。
     *
     * @return 每日动态采集的视频AV号列表
     */
    public List<Long> getDailyCollectionVideoIdList(boolean includeSundayOnly) throws SQLException {
        List<Long> videoIdList = new ArrayList<>();
        int count = getDailyCollectionVideoCount(includeSundayOnly);
        int pageCount = (count + QUERY_SIZE - 1) / QUERY_SIZE;
        String sql = "SELECT aid FROM " + COLLECTION_STATE_TABLE + " " +
                "WHERE (priority BETWEEN 1 AND 720 OR priority = 0 OR (? AND priority = -2)) " +
                "ORDER BY CASE " +
                "WHEN priority BETWEEN 1 AND 720 THEN 0 " +
                "WHEN priority = 0 THEN 1 " +
                "WHEN priority = -2 THEN 2 " +
                "ELSE 3 END, priority, aid " +
                "LIMIT ? OFFSET ?;";

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int offset = pageIndex * QUERY_SIZE;

            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setBoolean(1, includeSundayOnly);
                preparedStatement.setInt(2, QUERY_SIZE);
                preparedStatement.setInt(3, offset);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        videoIdList.add(resultSet.getLong("aid"));
                    }
                }
            }
        }
        return videoIdList;
    }

    private int getDailyCollectionVideoCount(boolean includeSundayOnly) throws SQLException {
        String sql = "SELECT COUNT(*) AS count FROM " + COLLECTION_STATE_TABLE + " " +
                "WHERE (priority BETWEEN 1 AND 720 OR priority = 0 OR (? AND priority = -2));";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setBoolean(1, includeSundayOnly);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                int count = 0;
                if (resultSet.next()) {
                    count = resultSet.getInt("count");
                }
                logger.info("Successfully query count of rows from video_collection_state for daily collection. count: {}", count);
                return count;
            }
        }
    }

    /**
     * 获取当前分钟应该采集的视频列表。
     *
     * @param minuteOfDay 当天第几分钟
     * @return 视频及采集间隔列表
     */
    public List<VideoWithPriorityDO> getDueMinuteCollectionVideoList(int minuteOfDay) throws SQLException {
        String sql = "SELECT aid, priority FROM " + COLLECTION_STATE_TABLE + " " +
                "WHERE priority BETWEEN 1 AND 720 AND MOD(?, priority) = 0 " +
                "ORDER BY priority, aid;";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, minuteOfDay);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                List<VideoWithPriorityDO> observingVideoList = new ArrayList<>();
                while (resultSet.next()) {
                    observingVideoList.add(new VideoWithPriorityDO(
                            resultSet.getLong("aid"),
                            resultSet.getInt("priority")
                    ));
                }
                return observingVideoList;
            }
        }
    }

    public void insertStatic(List<VideoStaticDO> videoStaticDOList) throws SQLException {
        String sql = "INSERT INTO " + VIDEO_STATIC_TABLE + " " +
                "(aid, bvid, pubdate, title, description, tag, pic, type_id, user_id, updated_at) " +
                "VALUES (?, ?, to_timestamp(?), ?, ?, ?, ?, ?, ?, now()) " +
                "ON CONFLICT (aid) DO UPDATE SET " +
                "bvid = EXCLUDED.bvid, " +
                "pubdate = EXCLUDED.pubdate, " +
                "title = EXCLUDED.title, " +
                "description = EXCLUDED.description, " +
                "tag = EXCLUDED.tag, " +
                "pic = EXCLUDED.pic, " +
                "type_id = EXCLUDED.type_id, " +
                "user_id = EXCLUDED.user_id, " +
                "updated_at = now();";

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
                if (videoStaticDO.typeDO() == null) {
                    preparedStatement.setNull(8, java.sql.Types.INTEGER);
                } else {
                    preparedStatement.setInt(8, videoStaticDO.typeDO().typeId());
                }
                if (videoStaticDO.userDO() == null) {
                    preparedStatement.setNull(9, java.sql.Types.BIGINT);
                } else {
                    preparedStatement.setLong(9, videoStaticDO.userDO().mid());
                }

                preparedStatement.addBatch();
                count++;
                if (count % INSERT_SIZE == 0) {
                    preparedStatement.executeBatch();
                }
            }

            preparedStatement.executeBatch();
            logger.info("Successfully insert into video_static. rows: {}", videoStaticDOList.size());
        }
    }

    public void insertDailyDynamic(List<VideoDynamicDO> videoDynamicDOList, String recordDate) throws SQLException {
        insertDynamic(videoDynamicDOList, DynamicInsertTableEnum.DAILY, recordDate, (int) (System.currentTimeMillis() / 1000L));
    }

    public void insertDynamic(List<VideoDynamicDO> videoDynamicDOList, DynamicInsertTableEnum tableEnum) throws SQLException {
        int now = (int) (System.currentTimeMillis() / 1000L);
        insertDynamic(videoDynamicDOList, tableEnum, null, now);
    }

    private void insertDynamic(
            List<VideoDynamicDO> videoDynamicDOList,
            DynamicInsertTableEnum tableEnum,
            String recordDate,
            int recordTime
    ) throws SQLException {
        String sql;
        if (DynamicInsertTableEnum.DAILY.equals(tableEnum)) {
            sql = "INSERT INTO " + VIDEO_DAILY_TABLE + " " +
                    "(record_date, aid, coin, favorite, danmaku, \"view\", reply, share, \"like\") " +
                    "VALUES (?::date, ?, ?, ?, ?, ?, ?, ?, ?);";
        } else {
            sql = "INSERT INTO " + VIDEO_MINUTE_TABLE + " " +
                    "(\"time\", aid, coin, favorite, danmaku, \"view\", reply, share, \"like\") " +
                    "VALUES (to_timestamp(?), ?, ?, ?, ?, ?, ?, ?, ?);";
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int count = 0;

            for (VideoDynamicDO video : videoDynamicDOList) {
                if (DynamicInsertTableEnum.DAILY.equals(tableEnum)) {
                    preparedStatement.setString(1, recordDate);
                } else {
                    preparedStatement.setInt(1, recordTime);
                }
                preparedStatement.setLong(2, video.aid());
                preparedStatement.setInt(3, video.coin());
                preparedStatement.setInt(4, video.favorite());
                preparedStatement.setInt(5, video.danmaku());
                preparedStatement.setInt(6, video.view());
                preparedStatement.setInt(7, video.reply());
                preparedStatement.setInt(8, video.share());
                preparedStatement.setInt(9, video.like());

                preparedStatement.addBatch();
                count++;
                if (count % INSERT_SIZE == 0) {
                    preparedStatement.executeBatch();
                }
            }

            preparedStatement.executeBatch();
            logger.info("Successfully insert into {}. rows: {}", tableEnum.getTable(), videoDynamicDOList.size());
        }
    }

    public void insertUserDim(List<UserDO> userDOList) throws SQLException {
        String sql = "INSERT INTO " + DISCOVERED_USERS_TABLE + " (user_id, user_name, face, last_updated) " +
                "VALUES (?, ?, ?, now()) " +
                "ON CONFLICT (user_id) DO UPDATE SET " +
                "user_name = EXCLUDED.user_name, " +
                "face = EXCLUDED.face, " +
                "last_updated = now();";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int count = 0;

            for (UserDO user : userDOList) {
                preparedStatement.setLong(1, user.mid());
                preparedStatement.setString(2, user.name());
                preparedStatement.setString(3, user.face());

                preparedStatement.addBatch();
                count++;
                if (count % INSERT_SIZE == 0) {
                    preparedStatement.executeBatch();
                }
            }

            preparedStatement.executeBatch();
            logger.info("Successfully insert into discovered_users. rows: {}", userDOList.size());
        }
    }
}

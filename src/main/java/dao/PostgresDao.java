package dao;

import dos.VideoWithPriorityDO;
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
    private static final int QUERY_SIZE = 10000;
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
}

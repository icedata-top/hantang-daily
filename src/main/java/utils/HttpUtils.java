package utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Random;

public class HttpUtils {
    private static final int DEFAULT_CONNECT_TIMEOUT = 5000; // 5秒
    private static final int DEFAULT_READ_TIMEOUT = 10000;   // 10秒

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:91.0) Gecko/20100101 Firefox/91.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Mobile/15E148 Safari/604.1"
    };

    // 生成随机User-Agent
    private static String getRandomUserAgent() {
        Random random = new Random();
        int index = random.nextInt(USER_AGENTS.length);
        return USER_AGENTS[index];
    }

    // 生成随机的DedeUserID (10位整数)
    private static String getRandomDedeUserID() {
        Random random = new Random();
        int dedeUserID = 1000000000 + random.nextInt(900000000); // 生成10位数字
        return String.valueOf(dedeUserID);
    }

    /**
     * 获取HTTP连接，实际上是准备HTTP请求头的各种参数。
     *
     * @param urlString URL字符串，需要加上已有的WBI验权参数w_rid和wts
     * @return HTTP连接
     */
    private static HttpURLConnection getHttpURLConnection(String urlString) throws URISyntaxException, IOException {
        URI uri = new URI(urlString);
        URL apiUrl = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
        connection.setReadTimeout(DEFAULT_READ_TIMEOUT);

        // 随机选取User-Agent
        String userAgent = getRandomUserAgent();
        // 随机生成DedeUserID
        String dedeUserID = getRandomDedeUserID();

        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Cookie", "buvid_fp_plain=undefined; DedeUserID=" + dedeUserID + ";");
        return connection;
    }

    /**
     * 调用HTTP API（支持重试机制）
     * @param urlString 完整的请求URL
     * @param maxRetries 最大重试次数（0表示不重试）
     * @param retryIntervalMs 重试间隔时间（毫秒）
     * @return 响应内容字符串
     * @throws IOException 当所有重试失败或发生不可恢复错误时抛出
     */
    public static String callApiWithRetry(String urlString, int maxRetries, long retryIntervalMs) throws IOException {
        Objects.requireNonNull(urlString, "URL cannot be null");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }

        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = getHttpURLConnection(urlString);

                int statusCode = connection.getResponseCode();
                if (statusCode == HttpURLConnection.HTTP_OK) {
                    return readResponse(connection);
                } else {
                    closeQuietly(connection);
                    throw new IOException("HTTP request failed with status: " + statusCode);
                }
            } catch (IOException e) {
                lastException = e;
                if (attempt < maxRetries) {
                    sleepQuietly(retryIntervalMs);
                }
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        throw new IOException("All " + maxRetries + " retry attempts failed, url = " + urlString, lastException);
    }

    // 私有方法：读取响应内容
    private static String readResponse(HttpURLConnection connection) throws IOException {
        try (InputStream inputStream = connection.getInputStream();
             InputStreamReader isr = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    // 私有方法：安静关闭连接
    private static void closeQuietly(HttpURLConnection connection) {
        try {
            if (connection != null) {
                connection.disconnect();
            }
        } catch (Exception ignored) {
            // 忽略关闭异常
        }
    }

    // 私有方法：线程睡眠（不抛出受检异常）
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

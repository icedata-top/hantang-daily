package business;

import api.BilibiliApi;
import dos.TypeDO;
import dos.VideoStaticDO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 今日静态数据
 */
public class TodayStaticDataJob {
    private final Set<Long> aidSet;
    private final List<VideoStaticDO> allVideoStaticDOList;
    private static final String[] KEYWORDS;
    private static final int TIME_RANGE;
    private static final int PAGE_SIZE;
    private static final Logger logger = LogManager.getLogger(TodayStaticDataJob.class);

    public TodayStaticDataJob() {
        aidSet = new HashSet<>();
        allVideoStaticDOList = new ArrayList<>();
    }

    static {
        try {
            // 加载配置文件
            Properties properties = new Properties();
            FileInputStream input = new FileInputStream("config.properties");
            InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            properties.load(reader);

            // 读取配置
            TIME_RANGE = Integer.parseInt(properties.getProperty("static.time_range", "86400"));
            PAGE_SIZE = Integer.parseInt(properties.getProperty("static.page_size", "50"));
            KEYWORDS = properties.getProperty("static.keywords", "洛天依,言和,乐正绫").replace("\n", "").split(",");

        } catch (IOException e) {
            e.fillInStackTrace();
            throw new RuntimeException("无法加载数据库配置文件", e);
        }
    }

    public void getData() throws IOException {
        long startTime = System.currentTimeMillis();
        BilibiliApi bilibiliApi = new BilibiliApi();

        // 视频投稿时间晚于videoPubdateLimit的才会写入库
        int videoPubdateLimit = ((int) (System.currentTimeMillis() / 1000L)) - TIME_RANGE;

        // 三层for循环：1.遍历所有关键词 2.每一个关键词遍历所有页 3.每一页遍历所有视频
        for (String keyword : KEYWORDS) {
            boolean isPubdateInLimit = true;
            int pageTotal = 1000 / PAGE_SIZE;
            for (int page = 1; page <= pageTotal && isPubdateInLimit; page++) {
                logger.debug("Getting static data (search results) from Bilibili API. keyword: {}, page: {}.", keyword, page);
                List<VideoStaticDO> curVideoStaticDOList = bilibiliApi.getSearchResult(keyword, page, PAGE_SIZE);
                for (VideoStaticDO videoStaticDO : curVideoStaticDOList) {
                    // 如果超出时间范围，要跳出两层for循环。
                    if (videoStaticDO.pubdate() < videoPubdateLimit) {
                        isPubdateInLimit = false;
                        break;
                    }

                    // 根据AV号哈希去重。比如一首洛天依、乐正绫合唱的歌，会被搜到两次。
                    long aid = videoStaticDO.aid();
                    if (!aidSet.contains(aid)) {
                        aidSet.add(aid);
                        allVideoStaticDOList.add(videoStaticDO);
                    }
                }
            }
        }
        long deltaTime = System.currentTimeMillis() - startTime;
        logger.info("Successfully get HTTP response from Bilibili. Time: {} ms, count: {}", deltaTime, aidSet.size());
    }

    public List<VideoStaticDO> getAllVideoStaticDOList() {
        return allVideoStaticDOList;
    }

    /**
     * 静态信息里面包含了分区信息，从这里提取，以便插入到分区的维度表。
     */
    public List<TypeDO> getTypeDOList() {
        return allVideoStaticDOList.stream().map(VideoStaticDO::typeDO).distinct().toList();
    }
}

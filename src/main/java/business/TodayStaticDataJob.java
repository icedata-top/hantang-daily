package business;

import api.BilibiliApi;
import dos.VideoStaticDO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 今日静态数据
 */
public class TodayStaticDataJob {
    private final Set<Long> aidSet;
    private final List<VideoStaticDO> allVideoStaticDOList;
    private static final String[] KEYWORDS = new String[] {"洛天依", "乐正绫", "言和"};
    private static final int TIME_RANGE = 24 * 60 * 60; // 1 day
    private static final int PAGE_SIZE = 50;
    private static final Logger logger = LogManager.getLogger(TodayStaticDataJob.class);

    public TodayStaticDataJob() {
        aidSet = new HashSet<>();
        allVideoStaticDOList = new ArrayList<>();
    }

    public void getData() throws IOException {
        long startTime = System.currentTimeMillis();
        BilibiliApi bilibiliApi = new BilibiliApi();

        // 视频投稿时间晚于videoPubdateLimit的才会写入库
        int videoPubdateLimit = ((int) (System.currentTimeMillis() / 1000L)) - TIME_RANGE;

        // 三层for循环：1.遍历所有关键词 2.每一个关键词遍历所有页 3.每一页遍历所有视频
        for (String keyword : KEYWORDS) {
            boolean isPubdateInLimit = true;
            for (int page = 1; page <= 20 && isPubdateInLimit; page++) {
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
}

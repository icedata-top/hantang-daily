package business;

import com.mysql.cj.util.StringUtils;
import dao.MysqlDao;
import dos.VideoStaticDO;
import dos.VideoVocalDO;
import enums.VocalEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * 分析虚拟歌手
 */
public class AnalyzeVocalJob {
    private static final Logger logger = LogManager.getLogger(AnalyzeVocalJob.class);

    private String preHandle(String string) {
        String lower = string.toLowerCase();
        return lower.replace("星尘minus", "minus");
    }

    /**
     * 视频包含歌手。如果包含，添加歌手信息到list中。如果不包含，则无行为。
     * @param list 歌手列表
     * @param string 字段内容
     * @param lambda 对歌手信息的函数
     */
    private void containsVocal(List<VocalEnum> list, String string,  Function<VocalEnum, String> lambda) {
        if (StringUtils.isNullOrEmpty(string)) {
            return;
        }
        for (VocalEnum vocalEnum : VocalEnum.values()) {
            String[] keywords = lambda.apply(vocalEnum).split("\\|");
            for (String keyword : keywords) {
                if (!StringUtils.isNullOrEmpty(keyword) && preHandle(string).contains(keyword)) {
                    list.add(vocalEnum);
                    break; // 避免这个虚拟歌手被添加两次
                }
            }
        }
    }

    /**
     * 从单个视频静态信息（主要是标题、tag、简介）中分析出这个视频是哪些歌手唱的。
     * @param videoStaticDO 视频静态信息
     * @return 演唱这个视频的歌手
     */
    private List<VideoVocalDO> analyzeVocal(VideoStaticDO videoStaticDO) {
        List<VocalEnum> list = new ArrayList<>();
        List<String> videoFields = Arrays.asList(
                videoStaticDO.title(),
                videoStaticDO.tag(),
                videoStaticDO.description()
        );
        List<Function<VocalEnum, String>> functions = Arrays.asList(
                VocalEnum::getName,
                VocalEnum::getAlias
        );

        // 遍历视频静态信息和 VocalEnum getter 方法
        for (Function<VocalEnum, String> function : functions) {
            for (String field : videoFields) {
                if (list.isEmpty()) {
                    containsVocal(list, field, function);
                }
            }
            if (!list.isEmpty()) {
                break;
            }
        }

        // 构建结果
        List<VideoVocalDO> videoVocalDOList = new ArrayList<>();
        for (VocalEnum vocalEnum : list) {
            videoVocalDOList.add(new VideoVocalDO(videoStaticDO.aid(), vocalEnum.getVocalId()));
        }
        return videoVocalDOList;
    }

    /**
     * 从多个视频静态信息（主要是标题、tag、简介）中分析出这个视频是哪些歌手唱的。
     * @param videoStaticDOList 视频静态信息列表
     * @return 歌手列表
     */
    private List<VideoVocalDO> analyze(List<VideoStaticDO> videoStaticDOList) {
        List<VideoVocalDO> videoVocalDOList = new ArrayList<>();
        for (VideoStaticDO videoStaticDO :videoStaticDOList) {
            List<VideoVocalDO> list = analyzeVocal(videoStaticDO);
            videoVocalDOList.addAll(list);
        }
        return videoVocalDOList;
    }

    /**
     * 从数据表读入视频数据，并且分析是哪些歌手唱的，再插入到数据表中。
     */
    public void run() throws SQLException, ClassNotFoundException {
        MysqlDao mysqlDao = new MysqlDao();
        int totalSize = 0;

        // 定义起始和结束年月
        int startYear = 2024;
        int startMonth = 9;
        int endYear = 2024;
        int endMonth = 11;

        // 遍历每个月
        for (int year = startYear; year <= endYear; year++) {
            int startM = (year == startYear) ? startMonth : 1;
            int endM = (year == endYear) ? endMonth : 12;

            for (int month = startM; month <= endM; month++) {
                // 获取该月的第一天的开始时间
                LocalDateTime firstDayOfMonth = LocalDateTime.of(year, month, 1, 0, 0, 0);
                int startTime = (int) firstDayOfMonth.toEpochSecond(ZoneOffset.UTC);

                // 获取该月的最后一天的结束时间
                LocalDateTime lastDayOfMonth = firstDayOfMonth
                        .plusMonths(1) // 跳到下个月
                        .minusSeconds(1); // 回退一秒
                int endTime = (int) lastDayOfMonth.toEpochSecond(ZoneOffset.UTC);

                // 调用函数
                logger.debug("To read video text info for month {}-{}.", year, month);
                List<VideoStaticDO> videoStaticDOList = mysqlDao.getVideoTextInfoList(startTime, endTime);
                logger.debug("To analyze vocal for month {}-{}.", year, month);
                List<VideoVocalDO> videoVocalDOList = analyze(videoStaticDOList);
                logger.debug("To save data for month {}-{}.", year, month);
                mysqlDao.insertRelVideoVocal(videoVocalDOList);
                totalSize += videoStaticDOList.size();
            }
        }

        logger.info("Success FINISH analyzing vocal info for video. totalSize: {}", totalSize);
    }
}

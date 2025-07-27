package business;

import dos.VideoStaticDO;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 按照特定规则对视频进行过滤，保留符合白名单分区且不包含黑名单关键词的数据。
 */
public class StaticDataFilter {
    private final Set<Integer> allowedTypeIds;
    private final List<String> blockedKeywords;

    public StaticDataFilter(Properties properties) {
        this.allowedTypeIds = parseAllowedTypeIds(
                properties.getProperty("static.type_id_white_list", "")
        );
        this.blockedKeywords = parseBlockedKeywords(
                properties.getProperty("static.keyword_black_list", "")
        );
    }

    private Set<Integer> parseAllowedTypeIds(String configStr) {
        return Arrays.stream(configStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
    }

    private List<String> parseBlockedKeywords(String configStr) {
        return Arrays.stream(configStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 检查视频是否符合保留条件
     * @param videoStaticDO 视频静态数据对象
     * @return true表示保留，false表示过滤
     */
    public boolean shouldRetain(VideoStaticDO videoStaticDO) {
        if (isInvalidVideo(videoStaticDO)) {
            return false;
        }

        return isAllowedType(videoStaticDO) && !containsBlockedKeywords(videoStaticDO);
    }

    private boolean isInvalidVideo(VideoStaticDO video) {
        return video == null || video.tag() == null
                || video.title() == null || video.typeDO() == null;
    }

    private boolean isAllowedType(VideoStaticDO video) {
        return allowedTypeIds.contains(video.typeDO().typeId()); // 白名单检查
    }

    private boolean containsBlockedKeywords(VideoStaticDO video) {
        return blockedKeywords.stream()
                .anyMatch(keyword ->
                        video.title().contains(keyword) ||
                                video.tag().contains(keyword)
                );
    }
}
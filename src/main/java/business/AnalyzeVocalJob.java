package business;

import com.mysql.cj.util.StringUtils;
import dos.VideoStaticDO;
import dos.VideoVocalDO;
import enums.VocalEnum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * 分析虚拟歌手
 */
public class AnalyzeVocalJob {
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
                if (string.toLowerCase().contains(keyword)) {
                    list.add(vocalEnum);
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
    public List<VideoVocalDO> analyze(List<VideoStaticDO> videoStaticDOList) {
        List<VideoVocalDO> videoVocalDOList = new ArrayList<>();
        for (VideoStaticDO videoStaticDO :videoStaticDOList) {
            List<VideoVocalDO> list = analyzeVocal(videoStaticDO);
            videoVocalDOList.addAll(list);
        }
        return videoVocalDOList;
    }
}

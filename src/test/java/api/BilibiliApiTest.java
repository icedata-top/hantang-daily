package api;

import dos.VideoStaticDO;

import java.io.IOException;
import java.util.List;

public class BilibiliApiTest {
    // 示例调用
    public static void main(String[] args) throws IOException {
        BilibiliApi bilibiliApi = new BilibiliApi();
        List<VideoStaticDO> videoStaticDOList = bilibiliApi.getSearchResult("洛天依", 1, 42);
        for (VideoStaticDO videoStaticDO : videoStaticDOList) {
            System.out.println(videoStaticDO.aid() + "\t"+ videoStaticDO.title());
        }
    }
}

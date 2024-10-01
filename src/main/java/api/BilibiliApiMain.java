package api;

import dos.VideoStaticDO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BilibiliApiMain {
    public static void main(String[] args) throws IOException {
        BilibiliApi bilibiliApi = new BilibiliApi();
        List<Long> aidList = new ArrayList<>();
        aidList.add(6009789L);
        aidList.add(3905462L);
        System.out.println(bilibiliApi.getVideoInfo(aidList));
        List<VideoStaticDO> videoStaticDOList = bilibiliApi.getSearchResult("洛天依", 1, 42);
        for (VideoStaticDO videoStaticDO : videoStaticDOList) {
            System.out.println(videoStaticDO.aid() + "\t" + videoStaticDO.userDO().mid() + "\t" + videoStaticDO.title());
        }
    }
}

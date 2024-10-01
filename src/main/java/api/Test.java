package api;

import com.alibaba.fastjson.JSONObject;
import dos.VideoStaticDO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class Test {
    // 示例调用
    public static void main(String[] args) throws IOException, URISyntaxException, NoSuchAlgorithmException {
        BilibiliApi bilibiliApi = new BilibiliApi();
        List<VideoStaticDO> videoStaticDOList = bilibiliApi.getSearchResult("洛天依", 1, 42);
        for (VideoStaticDO videoStaticDO : videoStaticDOList) {
            System.out.println(videoStaticDO.aid() + "\t"+ videoStaticDO.title());
        }
    }
}

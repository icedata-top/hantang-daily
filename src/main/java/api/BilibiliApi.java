package api;

import com.alibaba.fastjson.JSONArray;
import dos.TypeDO;
import dos.UserDO;
import dos.VideoDynamicDO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dos.VideoStaticDO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BilibiliApi {
    private static final Logger logger = LogManager.getLogger(BilibiliApi.class);

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
    private HttpURLConnection getHttpURLConnection(String urlString) throws URISyntaxException, IOException {
        URI uri = new URI(urlString);
        URL apiUrl = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);

        // 随机选取User-Agent
        String userAgent = getRandomUserAgent();
        // 随机生成DedeUserID
        String dedeUserID = getRandomDedeUserID();

        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Cookie", "buvid_fp_plain=undefined; DedeUserID=" + dedeUserID + ";");
        return connection;
    }

    /**
     * 按URL字符串调用API。考虑到B站的API参数都是带在URL里的，不需要额外写请求体。
     *
     * @param urlString URL字符串，需要带上参数（含WBI验权参数w_rid和wts）
     * @return HTTP响应字符串
     */
    private String callApiByUrlString(String urlString) throws IOException {
        try {
            long startTime = System.currentTimeMillis();
            HttpURLConnection connection = getHttpURLConnection(urlString);
            int responseCode = connection.getResponseCode();

            // 如果响应码是200，则读取响应体
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                connection.disconnect();
                long deltaTime = System.currentTimeMillis() - startTime;
                logger.info("Successfully get HTTP response from Bilibili. Time: {} ms, URL: {}", deltaTime, urlString);
                return response.toString();
            } else {
                connection.disconnect();
                throw new IOException("Failed to fetch video info. HTTP response code: " + responseCode);
            }
        } catch (Exception e) {
            throw new IOException("Failed to construct URL or fetch video info", e);
        }
    }

    /**
     * 访问Bilibili API，批量获取视频信息。
     *
     * @param aidList 视频AV号列表
     * @return HTTP响应
     * @throws IOException 如果请求失败或响应码不是200
     */
    private String getVideoInfoApi(List<Long> aidList) throws IOException {
        StringBuilder urlString = new StringBuilder();
        urlString.append("https://api.bilibili.com/medialist/gateway/base/resource/infos?resources=");
        for (Long aid : aidList) {
            urlString.append(aid).append(":2,");
        }
        urlString.deleteCharAt(urlString.length() - 1);
        return callApiByUrlString(urlString.toString());
    }

    /**
     * 批量获取视频信息
     *
     * @param aidList 视频的AV号列表
     * @return 视频动态数据列表
     */
    public List<VideoDynamicDO> getVideoInfo(List<Long> aidList) throws IOException {
        List<VideoDynamicDO> result = new ArrayList<>();

        // 发起HTTP请求
        String jsonResponse = getVideoInfoApi(aidList);
        JSONObject jsonObject = JSON.parseObject(jsonResponse);
        JSONArray data = jsonObject.getJSONArray("data");

        // 解析响应体装入result
        for (int i = 0; i < data.size(); i++) {
            JSONObject dataElem = data.getJSONObject(i);
            JSONObject cntInfo = dataElem.getJSONObject("cnt_info");
            JSONObject upper = dataElem.getJSONObject("upper");

            VideoDynamicDO videoDynamicDO = new VideoDynamicDO(
                    dataElem.getLongValue("id"), // aid
                    dataElem.getString("bvid"), // bvid
                    cntInfo.getIntValue("coin"), // coin
                    cntInfo.getIntValue("collect"), // favorite
                    cntInfo.getIntValue("danmaku"), // danmaku
                    cntInfo.getIntValue("play"), // view
                    cntInfo.getIntValue("reply"), // reply
                    cntInfo.getIntValue("share"), // share
                    cntInfo.getIntValue("thumb_up"), // like
                    new UserDO(
                            upper.getLongValue("mid"),
                            upper.getString("name"),
                            upper.getString("face").replace("https:", "").replace("http:", "")
                    ) // userDO
            );
            result.add(videoDynamicDO);
        }

        return result;
    }

    /**
     * 访问Bilibili API，按关键词搜索。
     *
     * @param keyword  关键词，入参不必要编码。
     * @param page     页码，起始页码1
     * @param pageSize 页面大小，建议50
     * @return HTTP响应
     */
    private String getSearchResultApi(String keyword, int page, int pageSize) throws IOException {
        // https://api.bilibili.com/x/web-interface/wbi/search/type?__refresh__=true&_extra=&ad_resource=5654&category_id=&context=&dynamic_offset=0&from_source=&from_spmid=333.337&gaia_vtoken=&highlight=1&keyword=%E6%B4%9B%E5%A4%A9%E4%BE%9D&order=click&page=1&page_size=42&platform=pc&pubtime_begin_s=0&pubtime_end_s=0&qv_id=iVHJSCLIWTe9FksiKu8bIfxA6MD53z3P&search_type=video&single_column=0&source_tag=3&web_location=1430654&w_rid=dbcec74cd4e721a8902cf64211dd62a5&wts=1727799448
        String encodeKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String urlString2 = String.format(
                "https://api.bilibili.com/x/web-interface/wbi/search/type?__refresh__=true&_extra=&ad_resource=5654&category_id=&context=&dynamic_offset=0&from_source=&from_spmid=333.337&gaia_vtoken=&highlight=1&keyword=%s&order=%s&page=%d&page_size=%d&platform=pc&pubtime_begin_s=0&pubtime_end_s=0&qv_id=iVHJSCLIWTe9FksiKu8bIfxA6MD53z3P&search_type=video&single_column=0&source_tag=3&web_location=1430654",
                encodeKeyword,
                "pubdate",
                page,
                pageSize
        );
        String urlString = Wbi.getInstance().addWbiParam(urlString2);
        return callApiByUrlString(urlString);
    }

    /**
     * 访问Bilibili API，按关键词搜索。在这个函数中，将会解析HTTP响应。
     *
     * @param keyword  关键词，入参不必要编码。
     * @param page     页码，起始页码1
     * @param pageSize 页面大小，建议50
     * @return 视频静态数据列表
     */
    public List<VideoStaticDO> getSearchResult(String keyword, int page, int pageSize) throws IOException {
        List<VideoStaticDO> videoList = new ArrayList<>();
        // 发起HTTP请求
        String jsonResponse = getSearchResultApi(keyword, page, pageSize);

        // 替换字符串
        jsonResponse = jsonResponse.replace("&amp;", "@");
        jsonResponse = jsonResponse.replace("\\u003cem class=\\\"keyword\\\"\\u003e", "");
        jsonResponse = jsonResponse.replace("\\u003c/em\\u003e", "");

        JSONObject jsonObject = JSON.parseObject(jsonResponse);
        JSONObject data = jsonObject.getJSONObject("data");
        if (data == null || !data.containsKey("result")) {
            logger.error("Cannot get search data from Bilibili API. keyword: {}, page: {}, pageSize: {}", keyword, page, pageSize);
            return videoList;
        }
        JSONArray result = data.getJSONArray("result");
        // 解析响应体装入videoList
        for (int i = 0; i < result.size(); i++) {
            JSONObject resultElem = result.getJSONObject(i);
            VideoStaticDO videoStaticDO = new VideoStaticDO(
                    resultElem.getLongValue("id"),
                    resultElem.getString("bvid"),
                    resultElem.getIntValue("pubdate"),
                    resultElem.getString("title"),
                    resultElem.getString("description"),
                    resultElem.getString("tag"),
                    resultElem.getString("pic").replace("https:", "").replace("http:", ""),
                    new TypeDO(
                            resultElem.getIntValue("typeid"),
                            resultElem.getString("typename")
                    ),
                    new UserDO(
                            resultElem.getLongValue("mid"),
                            resultElem.getString("author"),
                            resultElem.getString("upic").replace("https:", "").replace("http:", "")
                    )
            );
            videoList.add(videoStaticDO);
        }
        return videoList;
    }
}

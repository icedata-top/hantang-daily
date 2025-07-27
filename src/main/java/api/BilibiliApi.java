package api;

import com.alibaba.fastjson.JSONArray;
import dos.TypeDO;
import dos.UserDO;
import dos.VideoDynamicDO;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dos.VideoStaticDO;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.HttpUtils;

public class BilibiliApi {
    private static final Logger logger = LogManager.getLogger(BilibiliApi.class);
    private static final String PROXY_BASE_URL;
    private static final String OFFICIAL_BASE_URL = "https://api.bilibili.com";
    private static final long COOL_DOWN_PERIOD = 15 * 60 * 1000; // 15分钟
    private volatile long bilibiliCoolDownUntil = 0; // 原子性访问

    static {
        try {
            Properties properties = new Properties();
            FileInputStream input = new FileInputStream("config.secret.properties");
            properties.load(input);
            PROXY_BASE_URL = properties.getProperty("proxy.base_url");
        } catch (IOException e) {
            e.fillInStackTrace();
            throw new RuntimeException("无法加载数据库配置文件", e);
        }
    }

    /**
     * 通过API路径调用B站接口（先尝试官方API，失败后尝试代理API）
     *
     * @param apiPath API路径（含参数，如/x/space/wbi/acc/info?w_rid=xxx&wts=xxx）
     * @return HTTP响应内容
     * @throws IOException 当所有尝试均失败时抛出
     */
    private String fetchBilibiliApiWithFallback(String apiPath) throws IOException {
        long startTime = System.currentTimeMillis();
        String result;
        IOException lastException = null;

        // 检查是否在冷却期内
        boolean inCoolDown = System.currentTimeMillis() < bilibiliCoolDownUntil;

        // 优先尝试官方域名（如果不在冷却期）
        if (!inCoolDown) {
            try {
                String officialUrl = combineUrl(OFFICIAL_BASE_URL, apiPath);
                result = HttpUtils.callApiWithRetry(officialUrl, 1, 2000);
                if (StringUtils.isNotEmpty(result)) {
                    logSuccess("Bilibili Official", startTime, officialUrl);
                    return result;
                } else {
                    throw new IOException("Failed to get data from Bilibili official API. response is null or empty.");
                }
            } catch (IOException e) {
                lastException = e;
                logger.warn("Official API request failed, activating 15-minute cooldown. Path: {}", apiPath, e);
                bilibiliCoolDownUntil = System.currentTimeMillis() + COOL_DOWN_PERIOD; // 开启冷却
            }
        }

        // 降级到代理域名（重试3次）
        try {
            String proxyUrl = combineUrl(PROXY_BASE_URL, apiPath);
            result = HttpUtils.callApiWithRetry(proxyUrl, 3, 2000);
            if (StringUtils.isNotEmpty(result)) {
                logSuccess("Proxy", startTime, proxyUrl);
                return result;
            }
        } catch (IOException e) {
            logger.error("Proxy API request also failed. Path: {}", apiPath, e);
            throw new IOException("Failed after trying both official and proxy domains",
                    lastException != null ? lastException : e);
        }

        throw new IOException("Got empty response from both sources");
    }


    /**
     * 日志成功
     *
     * @param source    来源
     * @param startTime 起始时间
     * @param url       URL
     */
    private void logSuccess(String source, long startTime, String url) {
        long deltaTime = System.currentTimeMillis() - startTime;
        logger.info("Successfully fetched from {} API. Time: {} ms, URL: {}",
                source, deltaTime, url);
    }

    /**
     * 合并 URL，会自动判断是否有没有“/”
     *
     * @param baseUrl 基础 URL，例如 “https://api.bilibili.com”
     * @param path    路径，例如 “/medialist/gateway/base/resource/infos?resources”
     */
    private static String combineUrl(String baseUrl, String path) {
        if (StringUtils.isEmpty(baseUrl) || StringUtils.isEmpty(path)) {
            throw new IllegalArgumentException("Base URL or Path can not be null or empty");
        }
        if (path.startsWith("/")) {
            return baseUrl + path;
        }
        return baseUrl + "/" + path;
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
        urlString.append("/medialist/gateway/base/resource/infos?resources=");
        for (Long aid : aidList) {
            urlString.append(aid).append(":2,");
        }
        urlString.deleteCharAt(urlString.length() - 1);
        return fetchBilibiliApiWithFallback(urlString.toString());
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
                    dataElem.getIntValue("pubtime"), // pubtime
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
                "/x/web-interface/wbi/search/type?__refresh__=true&_extra=&ad_resource=5654&category_id=&context=&dynamic_offset=0&from_source=&from_spmid=333.337&gaia_vtoken=&highlight=1&keyword=%s&order=%s&page=%d&page_size=%d&platform=pc&pubtime_begin_s=0&pubtime_end_s=0&qv_id=iVHJSCLIWTe9FksiKu8bIfxA6MD53z3P&search_type=video&single_column=0&source_tag=3&web_location=1430654",
                encodeKeyword,
                "pubdate",
                page,
                pageSize
        );
        String urlString = Wbi.getInstance().addWbiParam(urlString2);
        return fetchBilibiliApiWithFallback(urlString);
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

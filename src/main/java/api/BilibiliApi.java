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
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dos.VideoStaticDO;

public class BilibiliApi {

    /**
     * 获取HTTP连接，实际上是准备HTTP请求头的各种参数。
     * @param urlString URL字符串
     * @return HTTP连接
     */
    private HttpURLConnection getHttpURLConnection(String urlString) throws URISyntaxException, IOException {
        URI uri = new URI(urlString);
        URL apiUrl = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36");
        connection.setRequestProperty("Cookie", "buvid_fp_plain=undefined;DedeUserID=1145141919;");
        return connection;
    }

    /**
     * 按URL字符串调用API。考虑到B站的API参数都是带在URL里的，不需要额外写请求体。
     *
     * @param urlString URL字符串，需要带上参数
     * @return HTTP响应字符串
     */
    private String callApiByUrlString(String urlString) throws IOException {
        try {
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

            // 使用构造函数创建 VideoDynamicDO 对象
            VideoDynamicDO videoDynamicDO = new VideoDynamicDO(
                    dataElem.getLongValue("id"), // aid
                    dataElem.getString("bvid"), // bvid
                    cntInfo.getIntValue("coin"), // coin
                    cntInfo.getIntValue("collect"), // favorite
                    cntInfo.getIntValue("danmaku"), // danmaku
                    cntInfo.getIntValue("play"), // view
                    cntInfo.getIntValue("reply"), // reply
                    cntInfo.getIntValue("share"), // share
                    cntInfo.getIntValue("like"), // like
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
     * @param keyword 关键词，入参不必要编码。
     * @param page 页码，起始页码1
     * @param pageSize 页面大小，建议50
     * @return HTTP响应
     */
    public String getSearchResultApi(String keyword, int page, int pageSize) throws IOException {
        // https://api.bilibili.com/x/web-interface/wbi/search/type?category_id=&search_type=video&ad_resource=5654&__refresh__=true&_extra=&context=&page=1&page_size=42&order=pubdate&pubtime_begin_s=0&pubtime_end_s=0&from_source=&from_spmid=333.337&platform=pc&highlight=1&single_column=0&keyword=%E6%B4%9B%E5%A4%A9%E4%BE%9D&qv_id=tG0mRpB1A0CXdLHy1zTmmwqwIm284e0O&source_tag=3&gaia_vtoken=&dynamic_offset=0&web_location=1430654&w_rid=f43f9e9e3aec7fc9f6cc5726fa4dc02c&wts=1727775241
        String encodeKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String urlString2 = String.format(
                "https://api.bilibili.com/x/web-interface/wbi/search/type?category_id=&search_type=video&ad_resource=5654&__refresh__=true&_extra=&context=&page=%d&page_size=%d&order=pubdate&pubtime_begin_s=0&pubtime_end_s=0&from_source=&from_spmid=333.337&platform=pc&highlight=1&single_column=0&keyword=%s&qv_id=tG0mRpB1A0CXdLHy1zTmmwqwIm284e0O&source_tag=3&gaia_vtoken=&dynamic_offset=0&web_location=1430654&w_rid=f43f9e9e3aec7fc9f6cc5726fa4dc02c&wts=1727775241",
                page,
                pageSize,
                encodeKeyword
                );
        return callApiByUrlString(urlString2);
    }

    /**
     * 访问Bilibili API，按关键词搜索。在这个函数中，将会解析HTTP响应。
     * @param keyword 关键词，入参不必要编码。
     * @param page 页码，起始页码1
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

package api;

import com.alibaba.fastjson.JSONArray;
import dos.VideoDO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
public class BilibiliApi {
    /**
     * 访问Bilibili API，批量获取视频信息。
     * @param aidList 视频AV号列表
     * @return HTTP响应体
     * @throws IOException 如果请求失败或响应码不是200
     */
    private String getVideoInfo(List<Long> aidList) throws IOException {
        StringBuilder urlString = new StringBuilder();
        urlString.append("https://api.bilibili.com/medialist/gateway/base/resource/infos?resources=");
        for (Long aid : aidList) {
            urlString.append(aid).append(":2,");
        }
        urlString.deleteCharAt(urlString.length() - 1);

        try {
            // 使用 URI 代替 URL，并通过 toURL() 转换为 URL
            URI uri = new URI(urlString.toString());
            URL apiUrl = uri.toURL();

            // 打开连接
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 设置连接超时时间
            connection.setReadTimeout(5000);    // 设置读取超时时间

            // 获取响应码
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
     * 批量获取视频信息
     * @param aidList 视频的AV号列表
     * @return 视频信息列表
     */
    public List<VideoDO> getVideoInfoList(List<Long> aidList) throws IOException {
        List<VideoDO> result = new ArrayList<>();

        // 发起HTTP请求
        String jsonResponse = getVideoInfo(aidList);
        JSONObject jsonObject = JSON.parseObject(jsonResponse);
        JSONArray data = jsonObject.getJSONArray("data");

        // 解析响应体装入result
        for (int i = 0; i < data.size(); i++) {
            JSONObject dataElem = data.getJSONObject(i);
            JSONObject cntInfo = dataElem.getJSONObject("cnt_info");
            JSONObject upper = dataElem.getJSONObject("upper");
            VideoDO videoDO = new VideoDO();
            videoDO.aid = dataElem.getLongValue("id");
            videoDO.bvid = dataElem.getString("bvid");
            videoDO.coin = cntInfo.getIntValue("coin");
            videoDO.favorite = cntInfo.getIntValue("collect");
            videoDO.danmaku = cntInfo.getIntValue("danmaku");
            videoDO.view = cntInfo.getIntValue("play");
            videoDO.reply = cntInfo.getIntValue("reply");
            videoDO.share = cntInfo.getIntValue("share");
            videoDO.like = cntInfo.getIntValue("like");
            videoDO.uploaderId = upper.getLongValue("mid");
            result.add(videoDO);
        }

        return result;
    }
}

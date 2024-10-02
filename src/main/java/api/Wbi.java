package api;

import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bilibili验权器，使用单例模式，因为一个项目运行一次只需要被初始化一次。
 * <a href="https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/wbi.md">文档</a>
 */
public class Wbi {
    private static volatile Wbi instance;
    private String rawWbiKey;
    private String mixinKey;

    // 定义MIXIN_KEY_ENC_TAB数组
    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42,
            19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60,
            51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    private Wbi() throws IOException {
        // 私有构造函数，避免外部直接实例化
        try {
            initRawWbiKey();
            mixinKey = genMixinKey(rawWbiKey);

        } catch (Exception e) {
            e.fillInStackTrace();
            throw new IOException("Exception happened when initial Wbi instance. " + e);
        }
    }

    public static Wbi getInstance() throws IOException {
        // 第一次检查，如果实例已存在，则直接返回
        if (instance == null) {
            synchronized (Wbi.class) {
                // 第二次检查，确保多线程环境下的安全性
                if (instance == null) {
                    instance = new Wbi();
                }
            }
        }
        return instance;
    }

    /**
     * 获取今日的img_url和sub_url，拼接得到rawWbiKey
     */
    private void initRawWbiKey() throws Exception {
        String imgKey = "7cd084941338484aae1ad9425b84077c";
        String subKey = "4932caff0ff746eab6f01bf08b70ac45";
        rawWbiKey = imgKey + subKey;
        /*
        // 定义请求的URL
        String urlString = "https://api.bilibili.com/x/web-interface/nav";
        URI uri = new URI(urlString);
        URL url = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

        // 读取响应
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) { // 成功
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // 解析响应的JSON
            JSONObject jsonObject = JSONObject.parseObject(response.toString());
            JSONObject dataObject = jsonObject.getJSONObject("data");
            JSONObject wbiImgObject = dataObject.getJSONObject("wbi_img");

            // 获取img_url和sub_url
            String imgUrl = wbiImgObject.getString("img_url");
            String subUrl = wbiImgObject.getString("sub_url");

            // 提取wbi/到.png之间的字符串
            String imgUrlSegment = extractWbiSegment(imgUrl);
            String subUrlSegment = extractWbiSegment(subUrl);

            rawWbiKey = imgUrlSegment + subUrlSegment;
        } else {
            throw new Exception("Failed to fetch data. HTTP response code: " + responseCode);
        }

         */
    }

    /**
     * 提取wbi/到.png之间的部分
     *
     * @param input 示例“https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png”
     */
    private static String extractWbiSegment(String input) {
        int wbiIndex = input.indexOf("wbi/");
        int pngIndex = input.indexOf(".png");
        if (wbiIndex != -1 && pngIndex != -1) {
            return input.substring(wbiIndex + 4, pngIndex);
        }
        return null;
    }

    /**
     * 根据rawWbiKey，进行换位加密获取mixinKey。
     *
     * @param rawWbiKeyString 字符串rawWbiKey
     */
    private static String genMixinKey(String rawWbiKeyString) {
        byte[] rawWbiKey = rawWbiKeyString.getBytes(StandardCharsets.UTF_8);
        List<Byte> mixinKeyList = new ArrayList<>();

        // 遍历MIXIN_KEY_ENC_TAB，取出rawWbiKey中对应位置的字节
        for (int n : MIXIN_KEY_ENC_TAB) {
            mixinKeyList.add(rawWbiKey[n]);
        }

        // 转换为byte数组
        byte[] mixinKeyBytes = new byte[mixinKeyList.size()];
        for (int i = 0; i < mixinKeyList.size(); i++) {
            mixinKeyBytes[i] = mixinKeyList.get(i);
        }

        // 将字节数组转为字符串并截取前32个字符
        String mixinKey = new String(mixinKeyBytes, StandardCharsets.UTF_8);
        if (mixinKey.length() > 32) {
            mixinKey = mixinKey.substring(0, 32);
        }

        return mixinKey;
    }

    /**
     * 根据原有的URL参数，增加新的WBI URL参数w_rid和wts
     *
     * @param url 原有的URL
     * @return 增加WBI参数之后的URL
     */
    public String addWbiParam(String url) throws IOException {
        // 计算当前时间戳
        int wts = (int) (System.currentTimeMillis() / 1000L);
        wts = 1727799448;

        // 提取url的?后面的部分
        String param = url.contains("?") ? url.substring(url.indexOf("?") + 1) : url;
        String newKey = param + "&wts=" + wts + this.mixinKey;
        String wRid = md5(newKey);
        return url + "&w_rid=" + wRid + "&wts=" + wts;

    }

    private static final char[] hexDigits = "0123456789abcdef".toCharArray();

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            char[] result = new char[messageDigest.length * 2];
            for (int i = 0; i < messageDigest.length; i++) {
                result[i * 2] = hexDigits[(messageDigest[i] >> 4) & 0xF];
                result[i * 2 + 1] = hexDigits[messageDigest[i] & 0xF];
            }
            return new String(result);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}


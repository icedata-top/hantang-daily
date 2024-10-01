import api.BilibiliApi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        BilibiliApi bilibiliApi = new BilibiliApi();
        List<Long> aidList = new ArrayList<>();
        aidList.add(6009789L);
        aidList.add(3905462L);
        System.out.println(bilibiliApi.getVideoInfoList(aidList));
    }
}

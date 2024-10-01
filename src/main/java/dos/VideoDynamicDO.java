package dos;

public record VideoDynamicDO(
        long aid,
        String bvid,
        int coin,
        int favorite,
        int danmaku,
        int view,
        int reply,
        int share,
        int like,
        UserDO userDO
) {}

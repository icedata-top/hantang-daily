package dos;

public class VideoDO {
    public long aid;
    public String bvid;
    public int coin;
    public int favorite;
    public int danmaku;
    public int view;
    public int reply;
    public int share;
    public int like;
    public long uploaderId;

    @Override
    public String toString() {
        return String.format(
                "VideoDO {aid=%d, bvid='%s', coin=%d, favorite=%d, danmaku=%d, view=%d, reply=%d, share=%d, like=%d, uploaderId=%d}",
                aid, bvid, coin, favorite, danmaku, view, reply, share, like, uploaderId
        );
    }
}

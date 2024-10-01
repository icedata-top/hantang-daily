package dos;

public record VideoStaticDO (
    long aid,
    String bvid,
    int pubdate,
    String title,
    String description,
    String tag,
    String pic,
    TypeDO typeDO,
    UserDO userDO
) {

}

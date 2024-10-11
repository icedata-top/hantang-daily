package enums;

public enum VocalEnum {
    // Vsinger
    LUO_TIANYI(1, "洛天依", "Vsinger", "天依"),
    YANHE(2, "言和", "Vsinger", "阿和"),
    YUEZHENG_LING(3, "乐正绫", "Vsinger", "阿绫"),
    YUEZHENG_LONGYA(4, "乐正龙牙", "Vsinger", "龙牙"),
    ZHIYU_MOKE(5, "徵羽摩柯", "Vsinger", "摩柯"),
    MO_QINGXIAN(6, "墨清弦", "Vsinger", ""),

    // Medium5
    STARDUST(11, "星尘", "Medium5", "尘宝|infinity"),
    HAIYI(12, "海伊", "Medium5", ""),
    CHIYU(13, "赤羽", "Medium5", ""),
    SHIAN(14, "诗岸", "Medium5", "山山"),
    CANGQIONG(15, "苍穹", "Medium5", ""),
    YONGYE(16, "永夜", "Medium5", "minus"),

    // Crypton
    HATSUNE_MIKU(21, "初音ミク", "Crypton", "miku|初音"),
    KAGAMINE_RIN(22, "鏡音リン", "Crypton", "rin|镜音铃"),
    KAGAMINE_LEN(23, "鏡音レン", "Crypton", "len|镜音连"),
    MEGURINE_LUKA(24, "巡音ルカ", "Crypton", "luka"),
    MEIKO(25, "メイコ", "Crypton", "meiko"),
    KAITO(26, "カイト", "Crypton", "kaito"),
    ;


    private final int vocalId;
    private final String name;
    private final String group;
    private final String alias;

    VocalEnum(int vocalId, String name, String group, String alias) {
        this.vocalId = vocalId;
        this.name = name;
        this.group = group;
        this.alias = alias;
    }

    public int getVocalId() {
        return vocalId;
    }

    public String getName() {
        return name;
    }

    public String getGroup() {
        return group;
    }

    public String getAlias() {
        return alias;
    }
}


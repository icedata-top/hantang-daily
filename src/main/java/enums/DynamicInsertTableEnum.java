package enums;

public enum DynamicInsertTableEnum {
    DAILY("video_daily", "record_date"),
    MINUTE("video_minute", "time");

    // 将属性设置为 final
    private final String table;
    private final String timeColumn;

    // 构造方法
    DynamicInsertTableEnum(String table, String timeColumn) {
        this.table = table;
        this.timeColumn = timeColumn;
    }

    // Getter for table
    public String getTable() {
        return table;
    }

    // Getter for timeColumn
    public String getTimeColumn() {
        return timeColumn;
    }
}


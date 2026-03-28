package api;

/**
 * API 状态监听器接口
 * 当 BilibiliApi 在官方 API 和代理 API 之间切换时，通知订阅者
 */
public interface ApiStateListener {
    /**
     * 当 API 源发生切换时调用
     * @param useProxy true=使用代理 API, false=使用官方 API
     */
    void onApiStateChanged(boolean useProxy);
}

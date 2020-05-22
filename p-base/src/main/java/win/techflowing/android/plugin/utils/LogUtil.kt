package win.techflowing.android.plugin.utils

/**
 * Log工具
 *
 * @author techflowing
 * @since 2019-09-18 23:42
 */
object LogUtil {
    private const val TAG = "Plugin："
    private const val DEV = false

    fun log(tag: String, msg: String?) {
        println("$tag:$msg")
    }

    fun log(msg: String?) {
        log(TAG, msg)
    }

    fun logWithDev(msg: String?) {
        if (DEV) {
            log(TAG, msg)
        }
    }

}
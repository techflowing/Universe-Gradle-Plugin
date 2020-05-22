package win.techflowing.android.plugin

import java.io.InputStream

/**
 * ClassReader 入口
 *
 * @author techflowing@gmail.com
 * @version 1.0.0
 * @since 2020/5/13 1:13 AM
 */
interface IClassReader {
    /**
     * 读取 class 文件
     */
    @Throws(Exception::class)
    fun readSingleClassToByteArray(inputStream: InputStream): ByteArray

    /**
     * 是否可读
     */
    fun canReadableClass(fullQualifiedClassName: String): Boolean

    /**
     * 扫描Class开始
     */
    fun onReadClassStart();

    /**
     * 扫描所有Class结束
     */
    fun onReadClassEnd()
}
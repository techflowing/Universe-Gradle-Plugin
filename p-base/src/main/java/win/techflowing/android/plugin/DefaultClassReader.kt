package win.techflowing.android.plugin

import org.apache.commons.io.IOUtils
import java.io.IOException
import java.io.InputStream

/**
 * 默认Class处理类，不做任何处理
 *
 * @author techflowing@gmail.com
 * @version 1.0.0
 * @since 2020/5/13 1:14 AM
 */
class DefaultClassReader : IClassReader {

    @Throws(IOException::class)
    override fun readSingleClassToByteArray(inputStream: InputStream): ByteArray {
        return IOUtils.toByteArray(inputStream)
    }

    /**
     * 全部不用读取
     */
    override fun canReadableClass(fullQualifiedClassName: String): Boolean {
        return false
    }

    override fun onReadClassStart() {

    }

    override fun onReadClassEnd() {

    }
}
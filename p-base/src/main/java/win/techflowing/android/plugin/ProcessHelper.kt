package win.techflowing.android.plugin

import com.android.build.api.transform.TransformOutputProvider
import com.android.builder.model.AndroidProject
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import win.techflowing.android.plugin.utils.LogUtil
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 类扫描帮助类，解析Jar包内文件，处理单个文件类等
 *
 * @author techflowing@gmail.com
 * @version 1.0.0
 * @since 2020/5/12 10:46 PM
 */
class ProcessHelper(reader: IClassReader?) {

    private val ZERO = FileTime.fromMillis(0)
    private val FILE_SEP = File.separator
    private var classReader: IClassReader = reader ?: DefaultClassReader()

    /**
     * 处理 intermediates/transforms/dexBuilder 文件
     */
    fun cleanDexBuilderFolder(filePath: String) {
        val path = Paths.get(filePath, AndroidProject.FD_INTERMEDIATES, "transforms", "dexBuilder")
        val file = path.toFile()
        if (file.exists()) {
            try {
                com.android.utils.FileUtils.deleteDirectoryContents(file)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 删除 OutputFolder 的文件
     */
    fun cleanOutputFolder(outputProvider: TransformOutputProvider) {
        try {
            outputProvider.deleteAll()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 处理 jar 文件
     */
    @Throws(Exception::class)
    fun handleJar(inputJar: File, outputJar: File) {
        LogUtil.logWithDev("处理Jar，InputJar：${inputJar.absolutePath}, outputJar: ${outputJar.absolutePath}")
        val inputZip = ZipFile(inputJar)
        val outputZip = ZipOutputStream(
            BufferedOutputStream(
                Files.newOutputStream(outputJar.toPath())
            )
        )
        val inEntries = inputZip.entries()
        while (inEntries.hasMoreElements()) {
            val entry = inEntries.nextElement()
            val originalFile: InputStream = BufferedInputStream(inputZip.getInputStream(entry))
            val outEntry = ZipEntry(entry.name)
            var newEntryContent: ByteArray
            // seperator of entry name is always '/', even in windows
            newEntryContent = if (!classReader.canReadableClass(outEntry.name.replace("/", "."))) {
                IOUtils.toByteArray(originalFile)
            } else {
                classReader.readSingleClassToByteArray(originalFile)
            }
            val crc32 = CRC32()
            crc32.update(newEntryContent, 0, newEntryContent.size)
            outEntry.crc = crc32.value
            outEntry.method = ZipEntry.STORED
            outEntry.size = newEntryContent.size.toLong()
            outEntry.compressedSize = newEntryContent.size.toLong()
            outEntry.lastAccessTime = ZERO
            outEntry.lastModifiedTime = ZERO
            outEntry.creationTime = ZERO
            outputZip.putNextEntry(outEntry)
            outputZip.write(newEntryContent)
            outputZip.closeEntry()
        }
        outputZip.flush()
        outputZip.close()
    }

    /**
     * 处理单个文件
     */
    @Throws(java.lang.Exception::class)
    fun handleSingleClassToFile(inputFile: File, outputFile: File, inputBaseDir: String, isOpen: Boolean) {
        LogUtil.logWithDev("处理Class，inputFile：.${inputFile.absolutePath} , outputFile: .${outputFile.absolutePath}")
        var inputDirPath = inputBaseDir
        if (!inputDirPath.endsWith(FILE_SEP)) {
            inputDirPath += FILE_SEP
        }
        if (isOpen && classReader.canReadableClass(inputFile.absolutePath.replace(inputDirPath, "").replace(FILE_SEP, "."))) {
            FileUtils.touch(outputFile)
            val inputStream: InputStream = FileInputStream(inputFile)
            val bytes: ByteArray = classReader.readSingleClassToByteArray(inputStream)
            val fos = FileOutputStream(outputFile)
            fos.write(bytes)
            fos.close()
            inputStream.close()
        } else {
            if (inputFile.isFile) {
                FileUtils.touch(outputFile)
                FileUtils.copyFile(inputFile, outputFile)
            }
        }
    }
}
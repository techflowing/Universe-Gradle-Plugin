package win.techflowing.android.plugin

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.google.common.io.Files
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import win.techflowing.android.plugin.utils.LogUtil
import java.io.File
import java.io.IOException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit

/**
 * Transform 封装类，包含多线程并发
 *
 * @author techflowing@gmail.com
 * @version 1.0.0
 * @since 2020/5/12 1:17 AM
 */
abstract class SimpleTransform(private val project: Project) : Transform() {

    private lateinit var processHelper: ProcessHelper

    /** 线程池 */
    private val executor = ForkJoinPool(
        Runtime.getRuntime().availableProcessors(),
        ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true
    )

    override fun getInputTypes(): Set<QualifiedContent.ContentType?>? {
        return TransformManager.CONTENT_CLASS
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    override fun isIncremental(): Boolean {
        return true
    }

    override fun isCacheable(): Boolean {
        return false
    }

    override fun getName(): String {
        return javaClass.simpleName
    }

    override fun transform(transformInvocation: TransformInvocation) {
        beginTransform()
        super.transform(transformInvocation)

        if (!enable()) {
            endTransform()
            return
        }

        val inputs = transformInvocation.inputs
        val outputProvider = transformInvocation.outputProvider
        val isIncremental = transformInvocation.isIncremental

        val classReader = getClassReader(transformInvocation)
        processHelper = ProcessHelper(classReader)

        // 如果不是自动增长,就删除文件 DexBuilderFolder 和 OutputFolder
        if (!isIncremental) {
            executor.execute {
                processHelper.cleanDexBuilderFolder(project.buildDir.absolutePath)
            }
            executor.execute {
                processHelper.cleanOutputFolder(outputProvider)
            }
            executor.awaitQuiescence(1, TimeUnit.MINUTES)
        }

        classReader?.onReadClassStart()

        inputs.forEach { input ->
            input?.jarInputs?.forEach { jarInput ->
                LogUtil.logWithDev("inputJar: ${jarInput.file.absolutePath}")
                val dest = outputProvider.getContentLocation(
                    jarInput.file.absolutePath,
                    jarInput.contentTypes,
                    jarInput.scopes,
                    Format.JAR
                )
                if (isIncremental) {
                    when (jarInput.status) {
                        null, Status.NOTCHANGED -> {
                            // 不处理
                        }
                        Status.ADDED, Status.CHANGED -> {
                            handleJarInput(jarInput.file, dest)
                        }
                        Status.REMOVED -> {
                            if (dest.exists()) {
                                FileUtils.forceDelete(dest)
                            }
                        }
                    }
                } else {
                    handleJarInput(jarInput.file, dest)
                }
            }
            input?.directoryInputs?.forEach { directoryInput ->
                LogUtil.logWithDev("directoryInput: ${directoryInput.file.absolutePath}")
                val dest = outputProvider.getContentLocation(
                    directoryInput.name,
                    directoryInput.contentTypes,
                    directoryInput.scopes,
                    Format.DIRECTORY
                )
                FileUtils.forceMkdir(dest)
                if (isIncremental) {
                    val srcDirPath = directoryInput.file.absolutePath
                    val destDirPath = dest.absolutePath
                    val fileStatusMap = directoryInput.changedFiles
                    fileStatusMap.forEach { (file, status) ->
                        val destFile = File(file.absolutePath.replace(srcDirPath, destDirPath))
                        when (status) {
                            null, Status.NOTCHANGED -> {
                                // 不处理
                            }
                            Status.REMOVED -> if (destFile.exists()) {
                                destFile.delete()
                            }
                            Status.ADDED, Status.CHANGED -> {
                                try {
                                    FileUtils.touch(destFile)
                                } catch (e: IOException) {
                                    Files.createParentDirs(destFile)
                                }
                                handleSingleFile(file, destFile, srcDirPath)
                            }
                        }
                    }
                } else {
                    handleDirectory(directoryInput.file, dest)
                }
            }
        }
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.MINUTES)

        // 扫描所有文件结束
        classReader?.onReadClassEnd()

        endTransform()
    }

    private fun handleJarInput(srcJar: File, destJar: File) {
        executor.execute {
            try {
                if (enable()) {
                    processHelper.handleJar(srcJar, destJar)
                } else {
                    FileUtils.copyFile(srcJar, destJar)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleDirectory(inputDir: File, outputDir: File) {
        if (enable()) {
            val inputDirPath = inputDir.absolutePath
            val outputDirPath = outputDir.absolutePath
            if (inputDir.isDirectory) {
                com.android.utils.FileUtils.getAllFiles(inputDir).forEach { file ->
                    executor.execute {
                        val filePath = file.absolutePath
                        val outputFile = File(filePath.replace(inputDirPath, outputDirPath))
                        try {
                            processHelper.handleSingleClassToFile(file, outputFile, inputDirPath, enable())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } else {
            executor.execute {
                try {
                    FileUtils.copyDirectory(inputDir, outputDir)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun handleSingleFile(inputFile: File, destFile: File, srcDirPath: String) {
        executor.execute {
            try {
                processHelper.handleSingleClassToFile(inputFile, destFile, srcDirPath, enable())
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * transform开始回调
     */
    protected open fun beginTransform() {
        LogUtil.log("beginTransform")
    }

    /**
     * transform结束回调
     */
    protected open fun endTransform() {
        LogUtil.log("endTransform")
    }

    abstract fun enable(): Boolean

    abstract fun getClassReader(transformInvocation: TransformInvocation): IClassReader?
}
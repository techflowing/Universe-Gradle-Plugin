package win.techflowing.android.plugin.creation

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformManager
import com.google.common.collect.ImmutableSet
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import win.techflowing.android.plugin.ASM
import win.techflowing.android.plugin.creation.model.ModuleApplication
import win.techflowing.android.plugin.utils.LogUtil
import java.io.File
import java.io.FileOutputStream


/**
 * 处理器，所有类扫描完成后，统一处理
 *
 * @author techflowing@gmail.com
 * @version 1.0.0
 * @since 2020/5/18 12:31 AM
 */
class Processor private constructor() {

    private var moduleAppList = mutableListOf<ModuleApplication>()

    companion object {
        private val instance: Processor by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            Processor()
        }

        @JvmStatic
        fun get(): Processor {
            return instance
        }
    }

    /**
     * 清除列表，避免缓存带来的问题
     */
    fun clearModuleApplicationList() {
        moduleAppList.clear()
    }

    /**
     * 暂存 @ModuleApplication 类信息
     */
    fun addModuleApplication(application: ModuleApplication) {
        val exist = moduleAppList.find {
            it.className == application.className
        }
        if (exist == null) {
            moduleAppList.add(application)
        }
    }

    /**
     * 生成 ModuleListProviderImpl 类
     */
    fun generateModuleListProviderImplClass(outputProvider: TransformOutputProvider) {
        val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
        classWriter.visit(
            ASM.VERSION,
            Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
            "win/techflowing/android/runtime/ModuleListProviderImpl",
            null,
            "java/lang/Object",
            arrayOf("win/techflowing/android/runtime/IModuleListProvider")
        )
        // 添加默认的构造方法
        var methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        methodVisitor.visitInsn(Opcodes.RETURN)
        methodVisitor.visitEnd()

        // 添加静态属性： private static List<IModuleApplication> list = new ArrayList<>();
        val fieldVisitor = classWriter.visitField(
            Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC,
            "list",
            "Ljava/util/List;",
            "Ljava/util/List<win/techflowing/android/runtime/IModuleApplication;>;",
            null
        )
        fieldVisitor.visitEnd()
        // 添加静态初始化代码块,
        // lite = new ArrayList<>();
        methodVisitor = classWriter.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
        methodVisitor.visitInsn(Opcodes.DUP)
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        methodVisitor.visitFieldInsn(
            Opcodes.PUTSTATIC,
            "win/techflowing/android/runtime/ModuleListProviderImpl",
            "list",
            "Ljava/util/List;"
        )
        // list.add(new ApplicationModule());
        moduleAppList.sortBy(ModuleApplication::priority)
        moduleAppList.forEach { moduleApp ->
            LogUtil.log("${moduleApp.className}:->${moduleApp.priority}")
            addModuleApplicationToListMethodVisitor(methodVisitor, moduleApp.className)
        }
        methodVisitor.visitInsn(Opcodes.RETURN)
        methodVisitor.visitEnd()

        // 实现 List<IModuleApplication> getModuleList() 方法
        methodVisitor = classWriter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "getModuleList",
            "()Ljava/util/List;",
            "()Ljava/util/List<Lwin/techflowing/android/runtime/IModuleApplication;>;",
            null
        )
        methodVisitor.visitFieldInsn(
            Opcodes.GETSTATIC,
            "win/techflowing/android/runtime/ModuleListProviderImpl",
            "list",
            "Ljava/util/List;"
        )
        methodVisitor.visitInsn(Opcodes.ARETURN)
        methodVisitor.visitEnd()

        // 输出
        val output = outputProvider.getContentLocation(
            "creation-generate", TransformManager.CONTENT_CLASS,
            ImmutableSet.of(QualifiedContent.Scope.PROJECT), Format.DIRECTORY
        )
        LogUtil.logWithDev("输出路径：" + output.absolutePath)
        val outputDir = File(output, "win/techflowing/android/runtime")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val outputStream = FileOutputStream(File(outputDir, "ModuleListProviderImpl.class"))
        outputStream.write(classWriter.toByteArray())
        outputStream.close()
    }

    /**
     * 执行类似 list.add(new ApplicationModule()) 操作
     */
    private fun addModuleApplicationToListMethodVisitor(methodVisitor: MethodVisitor, name: String) {
        LogUtil.log("添加ModuleApplication类：$name")
        methodVisitor.visitFieldInsn(
            Opcodes.GETSTATIC,
            "win/techflowing/android/runtime/ModuleListProviderImpl",
            "list",
            "Ljava/util/List;"
        )
        methodVisitor.visitTypeInsn(Opcodes.NEW, name)
        methodVisitor.visitInsn(Opcodes.DUP)
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", "()V", false)
        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true)
        methodVisitor.visitInsn(Opcodes.POP)
    }
}
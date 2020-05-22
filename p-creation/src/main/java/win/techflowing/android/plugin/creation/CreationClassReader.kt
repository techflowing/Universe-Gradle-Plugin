package win.techflowing.android.plugin.creation

import com.android.build.api.transform.TransformInvocation
import org.objectweb.asm.*
import win.techflowing.android.plugin.ASM
import win.techflowing.android.plugin.SimpleClassReader
import win.techflowing.android.plugin.creation.model.ModuleApplication
import win.techflowing.android.plugin.utils.LogUtil

/**
 * Class 扫描
 *
 * @author techflowing@gmail.com
 * @version 1.0.0
 * @since 2020/5/15 1:18 AM
 */
class CreationClassReader(private val transformInvocation: TransformInvocation) : SimpleClassReader() {


    override fun createClassVisitor(classWriter: ClassWriter): ClassVisitor {
        return CreationClassVisitor(classWriter)
    }

    override fun onReadClassStart() {
        Processor.get().clearModuleApplicationList()
    }

    override fun onReadClassEnd() {
        Processor.get().generateModuleListProviderImplClass(transformInvocation.outputProvider)
    }

    /**
     * 类扫描器
     */
    class CreationClassVisitor(classVisitor: ClassVisitor) : ClassVisitor(ASM.VERSION, classVisitor) {

        private lateinit var className: String
        /**
         * 当前类是否是 @AppApplication 注解标识的类
         */
        private var appApplication = false
        /**
         * 标识 @AppApplication 注解的类是否定义 OnCreate 方法
         */
        private var onCreateDefined = false
        private var attachBaseContextDefined = false
        private var onConfigurationChangedDefined = false
        private var onLowMemoryDefined = false
        private var onTerminateDefined = false
        private var onTrimMemoryDefined = false

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            super.visit(version, access, name, signature, superName, interfaces)
            className = name
        }

        /**
         * 扫描类注解
         */
        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            when (descriptor) {
                Constants.ANNOTATION_APP_APPLICATION -> {
                    LogUtil.log("扫描到AppApplication：$className")
                    appApplication = true
                }
                Constants.ANNOTATION_MODULE_APPLICATION -> {
                    return ModuleApplicationAnnotationVisitor { priority ->
                        LogUtil.log("扫描到ModuleApplication：$className, 优先级：$priority")
                        Processor.get().addModuleApplication(ModuleApplication(priority, className))
                    }
                }
            }
            return super.visitAnnotation(descriptor, visible)
        }

        /**
         * 扫描处理方法，只处理 @AppApplication 类
         */
        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
            if (appApplication) {
                when (name + descriptor) {
                    "onCreate()V" -> {
                        onCreateDefined = true
                        return AddCallModuleApplicationMethodVisitor(methodVisitor, "onCreate", "()V", false, false)
                    }
                    "attachBaseContext(Landroid/content/Context;)V" -> {
                        attachBaseContextDefined = true
                        return AddCallModuleApplicationMethodVisitor(
                            methodVisitor,
                            "attachBaseContext",
                            "(Landroid/content/Context;)V",
                            true,
                            false
                        )
                    }
                    "onConfigurationChanged(Landroid/content/res/Configuration;)V" -> {
                        onConfigurationChangedDefined = true
                        return AddCallModuleApplicationMethodVisitor(
                            methodVisitor,
                            "onConfigurationChanged",
                            "(Landroid/content/res/Configuration;)V",
                            true,
                            false
                        )
                    }
                    "onLowMemory()V" -> {
                        onLowMemoryDefined = true
                        return AddCallModuleApplicationMethodVisitor(methodVisitor, "onLowMemory", "()V", false, false)
                    }
                    "onTerminate()V" -> {
                        onTerminateDefined = true
                        return AddCallModuleApplicationMethodVisitor(methodVisitor, "onTerminate", "()V", false, false)
                    }
                    "onTrimMemory(I)V" -> {
                        onTrimMemoryDefined = true
                        return AddCallModuleApplicationMethodVisitor(methodVisitor, "onTrimMemory", "(I)V", false, true)
                    }
                }
            }
            return methodVisitor
        }

        /**
         * 扫描处理方法，只处理 @AppApplication 类，补全方法
         */
        override fun visitEnd() {
            if (appApplication) {
                if (!attachBaseContextDefined) {
                    defineMethod(4, "attachBaseContext", "(Landroid/content/Context;)V", true, false)
                }
                if (!onCreateDefined) {
                    defineMethod(1, "onCreate", "()V", false, false)
                }
                if (!onConfigurationChangedDefined) {
                    defineMethod(1, "onConfigurationChanged", "(Landroid/content/res/Configuration;)V", true, false)
                }
                if (!onLowMemoryDefined) {
                    defineMethod(1, "onLowMemory", "()V", false, false)
                }
                if (!onTerminateDefined) {
                    defineMethod(1, "onTerminate", "()V", false, false)
                }
                if (!onTrimMemoryDefined) {
                    defineMethod(1, "onTrimMemory", "(I)V", false, true)
                }
            }
            super.visitEnd()
        }

        /**
         * 添加方法实现
         */
        private fun defineMethod(access: Int, name: String, desc: String, aLoad: Boolean, iLoad: Boolean) {
            val methodVisitor = this.visitMethod(access, name, desc, null, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            if (aLoad) {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            }
            if (iLoad) {
                methodVisitor.visitVarInsn(Opcodes.ILOAD, 1)
            }
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/app/Application", name, desc, false)
            methodVisitor.visitInsn(Opcodes.RETURN)
            methodVisitor.visitEnd()
        }
    }

    /**
     * 添加 AppApplication 内方法到 ModuleApplicationContainer 的方法调用
     */
    class AddCallModuleApplicationMethodVisitor(
        methodVisitor: MethodVisitor,
        private val name: String,
        private val descriptor: String,
        private val aLoad: Boolean,
        private val iLoad: Boolean
    ) : MethodVisitor(ASM.VERSION, methodVisitor) {

        override fun visitInsn(opcode: Int) {
            when (opcode) {
                Opcodes.IRETURN,
                Opcodes.FRETURN,
                Opcodes.ARETURN,
                Opcodes.LRETURN,
                Opcodes.DRETURN,
                Opcodes.RETURN -> {
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "win/techflowing/android/runtime/ModuleApplicationContainer",
                        "get",
                        "()Lwin/techflowing/android/runtime/ModuleApplicationContainer;",
                        false
                    )
                    if (aLoad) {
                        mv.visitVarInsn(Opcodes.ALOAD, 1)
                    }
                    if (iLoad) {
                        mv.visitVarInsn(Opcodes.ILOAD, 1)
                    }
                    mv.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "win/techflowing/android/runtime/ModuleApplicationContainer",
                        name,
                        descriptor,
                        false
                    )
                }
            }
            super.visitInsn(opcode)
        }
    }

    /**
     * ModuleApplication 注解扫描
     */
    class ModuleApplicationAnnotationVisitor(val callback: (Int) -> Unit) : AnnotationVisitor(ASM.VERSION) {

        /**
         * 获取优先级属性
         */
        private var priority = Constants.ANNOTATION_MODULE_APPLICATION_PRIORITY_DEFAULT

        override fun visit(name: String?, value: Any?) {
            super.visit(name, value)
            if (Constants.ANNOTATION_MODULE_APPLICATION_PRIORITY == name && value is Int) {
                priority = value
            }
        }

        override fun visitEnd() {
            super.visitEnd()
            callback(priority)
        }
    }
}
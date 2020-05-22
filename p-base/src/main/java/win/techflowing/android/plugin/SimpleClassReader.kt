package win.techflowing.android.plugin

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import java.io.InputStream

/**
 * Class 读取
 *
 * @author techflowing@gmail.com
 * @version 1.0.0
 * @since 2020/5/15 1:24 AM
 */
abstract class SimpleClassReader : IClassReader {

    abstract fun createClassVisitor(classWriter: ClassWriter): ClassVisitor

    override fun readSingleClassToByteArray(inputStream: InputStream): ByteArray {
        val classReader = ClassReader(inputStream)
        val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
        val asmClassVisitor = createClassVisitor(classWriter)
        classReader.accept(asmClassVisitor, ClassReader.EXPAND_FRAMES)
        return classWriter.toByteArray()
    }

    override fun canReadableClass(fullQualifiedClassName: String): Boolean {
        return (fullQualifiedClassName.endsWith(".class")
                && !fullQualifiedClassName.startsWith("androidx.")
                && !fullQualifiedClassName.startsWith("android.")
                && !fullQualifiedClassName.contains("R$")
                && !fullQualifiedClassName.contains("R.class")
                && !fullQualifiedClassName.contains("BuildConfig.class"))

    }
}
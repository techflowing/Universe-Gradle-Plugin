package win.techflowing.android.plugin.creation

import com.android.build.api.transform.TransformInvocation
import org.gradle.api.Project
import win.techflowing.android.plugin.IClassReader
import win.techflowing.android.plugin.SimpleTransform
import win.techflowing.android.plugin.utils.LogUtil

/**
 * Transform
 *
 * @author techflowing@gmail.com
 * @version 1.0.0
 * @since 2020/5/15 1:09 AM
 */
class CreationTransform(project: Project, private val extension: CreationExtension) : SimpleTransform(project) {

    override fun getName(): String {
        return "Creation"
    }

    override fun enable(): Boolean {
        return extension.enable
    }

    override fun isIncremental(): Boolean {
        return false
    }

    override fun beginTransform() {
        LogUtil.log("Creation-Plugin 开始")
    }

    override fun endTransform() {
        LogUtil.log("Creation-Plugin 结束")
    }

    override fun getClassReader(transformInvocation: TransformInvocation): IClassReader? {
        return CreationClassReader(transformInvocation)
    }
}
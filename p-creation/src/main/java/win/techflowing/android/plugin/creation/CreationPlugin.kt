package win.techflowing.android.plugin.creation

import com.android.build.gradle.AppExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 创世，用于Module之间初始化调用分发
 *
 * @author techflowing@gmail.com
 * @version 1.0.0
 * @since 2020/5/15 1:00 AM
 */
class CreationPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw GradleException("CreationPlugin: Android Application plugin required")
        }
        val extension = project.extensions.create(CreationExtension.NAME, CreationExtension::class.java)
        val appExtension = project.extensions.getByType(AppExtension::class.java)
        appExtension.registerTransform(CreationTransform(project, extension))
    }
}
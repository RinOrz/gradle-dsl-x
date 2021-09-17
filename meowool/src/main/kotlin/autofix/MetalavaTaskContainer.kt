package autofix

import org.gradle.api.Project
import org.gradle.api.file.FileCollection

internal abstract class MetalavaTaskContainer {
    protected val Boolean.flagValue: String get() = if (this) "yes" else "no"

    protected fun Boolean.flag(flagValue: String): List<String> = if (this) {
        listOf(flagValue)
    } else {
        emptyList()
    }

    protected fun Project.getMetalavaClasspath(version: String): FileCollection {
        val configuration = configurations.findByName("metalava") ?: configurations.create("metalava").apply {
            val dependency = this@getMetalavaClasspath.dependencies.create(
                "com.android.tools.metalava:metalava:$version"
            )
            dependencies.add(dependency)
        }
        return files(configuration)
    }
}

package org.jetbrains.compose.reload

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

@Suppress("unused")
class ComposeHotReloadPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.create(composeHotReloadExtensionName, ComposeHotReloadExtension::class.java, target)

        target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            target.onKotlinPluginApplied()
        }

        target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            target.onKotlinPluginApplied()
        }
    }
}

private fun Project.onKotlinPluginApplied() {
    /* Test to find 'Isolated Classpath' issues */
    try {
        assert(project.extensions.getByName("kotlin") is KotlinProjectExtension)
    } catch (t: LinkageError) {
        throw IllegalStateException("'Inaccessible Kotlin Plugin'")
    }

    setupComposeHotReloadRuntimeDependency()
    setupComposeHotReloadVariant()
    setupComposeReloadHotClasspathTasks()
    setupComposeHotReloadExecTasks()
    setupComposeHotRunConventions()
    setupComposeDevCompilation()

    tasks.withType<KotlinJvmCompile>().configureEach { task ->
        if (isIdeaSync.orNull == true) return@configureEach

        task.compilerOptions.freeCompilerArgs.addAll(
            //"-P", "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true"
        )
    }
}

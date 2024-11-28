package org.jetbrains.compose.reload.agent.utils

import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.classpathAsList
import org.jetbrains.kotlin.incremental.destinationAsFile
import org.junit.jupiter.api.extension.*
import org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

interface Compiler {
    /**
     * @param code Map from Relative Source File Path to the source code itself.
     */
    fun compile(code: Map<String, String>): Map<String, ByteArray>
}

internal fun Compiler.compile(vararg code: Pair<String, String>): Map<String, ByteArray> {
    return compile(code.toMap())
}

internal fun Compiler.compile(sourceCode: String) = compile("Test.kt" to sourceCode)

@ExtendWith(CompilerProvider::class)
annotation class WithCompiler

@OptIn(ExperimentalPathApi::class)
class CompilerProvider : ParameterResolver, BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        context.getStore(GLOBAL).put("compilerDir", Files.createTempDirectory("compiler"))
    }

    override fun afterEach(context: ExtensionContext) {
        context.getStore(GLOBAL).remove("compilerDir", Path::class.java)
            .deleteRecursively()
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == Compiler::class.java
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any? {
        return CompilerImpl(extensionContext.getStore(GLOBAL).get("compilerDir", Path::class.java)!!)
    }
}

private class CompilerImpl(
    private val workingDir: Path
) : Compiler {
    override fun compile(code: Map<String, String>): Map<String, ByteArray> {
        code.forEach { path, sourceCode ->
            workingDir.resolve(path).createParentDirectories().writeText(sourceCode)
        }

        val classesDir = workingDir.resolve("classes")

        val arguments = K2JVMCompilerArguments()
        arguments.moduleName = "testModule"
        arguments.noStdlib = true
        arguments.kotlinHome = null
        arguments.destinationAsFile = workingDir.resolve("classes").toFile()

        arguments.classpathAsList = System.getProperty("testCompilerClasspath")
            .split(File.pathSeparator)
            .map { absolutePath -> File(absolutePath) }

        arguments.pluginClasspaths = System.getProperty("testComposeCompilerClasspath")
            .split(File.pathSeparator)
            .toTypedArray()

        arguments.pluginOptions = arrayOf(
            "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=OptimizeNonSkippingGroups",
            "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true"
        )

        arguments.freeArgs = code.keys.map { path -> workingDir.resolve(path).absolutePathString() }

        val result = K2JVMCompiler().exec(
            PrintingMessageCollector(System.err, MessageRenderer.GRADLE_STYLE, true),
            Services.EMPTY,
            arguments
        )

        if (result.code != 0) {
            error("Failed compiling code (${result.code})")
        }

        return classesDir.listDirectoryEntries("*.class").associate { path ->
            path.relativeTo(classesDir).pathString to path.readBytes()
        }
    }
}

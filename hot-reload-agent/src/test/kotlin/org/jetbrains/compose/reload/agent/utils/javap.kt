package org.jetbrains.compose.reload.agent.utils

import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.fail
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.util.spi.ToolProvider
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText


@Suppress("unused") // Debugging utility!
fun javap(bytecode: ByteArray): String {
    val javap = ToolProvider.findFirst("javap").orElseThrow()
    val out = StringWriter()
    val err = StringWriter()

    val tmpDir = Files.createTempDirectory("javap-tmp")
    val targetFile = tmpDir.resolve("Bytecode.class")
    targetFile.writeBytes(bytecode)

    javap.run(PrintWriter(out), PrintWriter(err), "-v", "-p", targetFile.absolutePathString())

    return out.toString() + err.toString()
}

@Suppress("unused") // Debugging utility!
fun javap(code: Map<String, ByteArray>): Map<String, String> {
    return code.mapValues { javap(it.value) }
}

fun checkJavap(testInfo: TestInfo, name: String = "", code: Map<String, ByteArray>) {
    fun String.asFileName(): String = replace("""\\W+""", "_")

    val directory = Path("src/test/resources/javap")
        .resolve(testInfo.testClass.get().name.asFileName().replace(".", "/"))
        .resolve(testInfo.testMethod.get().name.asFileName())
        .resolve(name.asFileName())

    val actualContent = code
        .mapKeys { (path, _) -> directory.resolve("$path.javap.txt") }
        .mapValues { (_, code) ->
            javap(code).trim()
                .replace(Regex("""/.*/Bytecode.class"""), "<bytecode path>")
                .replace(Regex("Last modified.*;"), "Last modified <Date>;")
        }

    if (!directory.exists()) {
        actualContent.forEach { file, code ->
            file.createParentDirectories()
            file.writeText(code)
        }
        fail("javap directory '${directory.pathString}' did not exist; Generated")
    }

    val expectedContent = directory.listDirectoryEntries("*.javap.txt").associate { path ->
        path to path.readText().trim()
    }

    val unexpectedActualPaths = actualContent.keys - expectedContent.keys
    if (unexpectedActualPaths.isNotEmpty()) {
        fail("Unexpected class files: $unexpectedActualPaths")
    }

    val missingExpectPaths = expectedContent.keys - actualContent.keys
    if (missingExpectPaths.isNotEmpty()) {
        fail("Missing class files: $missingExpectPaths")
    }

    expectedContent.forEach { (path, code) ->
        val actualCode = actualContent.getValue(path)
        if (code != actualCode) {
            path.resolveSibling(path.nameWithoutExtension + "-actual.txt").writeText(actualCode)
            fail("Javap '${path.toUri()}' does not match")
        }
    }
}
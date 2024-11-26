@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.compose.reload.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.TestScope
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.asChannel
import java.io.Serializable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HotReloadTestFixture(
    val testClassName: String,
    val testMethodName: String,
    val projectDir: ProjectDir,
    val gradleRunner: GradleRunner,
    val orchestration: OrchestrationServer,
    val projectMode: ProjectMode
) : AutoCloseable {

    val logger: Logger = Logging.getLogger("ScreenshotTestFixture")

    val messages = orchestration.asChannel()

    fun sendMessage(message: OrchestrationMessage) {
        orchestration.sendMessage(message).get()
    }

    suspend inline fun <reified T> skipToMessage(
        timeout: Duration = 5.minutes, crossinline filter: (T) -> Boolean = { true }
    ): T {
        val stack = Thread.currentThread().stackTrace
        val dispatcher = Dispatchers.Default.limitedParallelism(1)

        val reminder = daemonTestScope.launch(dispatcher) {
            val sleep = 5.seconds
            var waiting = 0.seconds
            while (true) {
                logger.quiet(
                    "Waiting for message ${T::class.simpleName} ($waiting/$timeout)" +
                            "\n${stack.drop(1).take(5).joinToString("\n") { "  $it" }}"
                )
                delay(sleep)
                waiting += sleep
            }
        }

        return withContext(dispatcher) {
            try {
                withTimeout(timeout) {
                    messages.receiveAsFlow().filterIsInstance<T>().filter(filter).first()
                }
            } finally {
                reminder.cancel()
            }
        }
    }

    lateinit var testScope: TestScope
        private set

    /**
     * Coroutines launched in this scope will not keep the 'runTest' blocking.
     * This scope will be canceled after the 'runTest' finished (e.g., useful for launching 'Daemon Coroutines)
     */
    lateinit var daemonTestScope: CoroutineScope

    fun runTest(timeout: Duration = 15.minutes, test: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest(timeout = timeout) {
            testScope = this
            daemonTestScope = CoroutineScope(currentCoroutineContext() + Job(currentCoroutineContext().job))

            daemonTestScope.launch {
                orchestration.asChannel().consumeAsFlow().collect {
                    logger.quiet("Test: Received message: $it")
                }
            }

            try {
                test()
            } finally {
                daemonTestScope.cancel()
                daemonTestScope.coroutineContext[Job]?.join()
            }
        }
    }

    private val resourcesLock = ReentrantLock()
    private val resources = mutableListOf<AutoCloseable>()

    override fun close() {
        orchestration.sendMessage(OrchestrationMessage.ShutdownRequest()).get()
        orchestration.closeGracefully().get()

        testScope.cancel()
        daemonTestScope.cancel()

        /* Kludge: Windows tests failed to delete the project dir (maybe some files are still in use?) */
        run deleteProjectDir@{
            repeat(10) {
                runCatching { projectDir.path.deleteRecursively() }
                    .onSuccess { return@deleteProjectDir }
            }
        }

        resourcesLock.withLock {
            resources.forEach { resource -> resource.close() }
            resources.clear()
        }
    }
}

fun HotReloadTestFixture.sendTestEvent(payload: Serializable? = null) {
    sendMessage(OrchestrationMessage.TestEvent(payload))
}

suspend fun HotReloadTestFixture.launchDaemonThread(block: () -> Unit): Job {
    val threadResult = CompletableDeferred<Unit>()
    val thread = thread(isDaemon = true) {
        try {
            block()
            threadResult.complete(Unit)
        } catch (_: InterruptedException) {
            threadResult.complete(Unit)
            // Goodbye.
        } catch (t: Throwable) {
            threadResult.completeExceptionally(t)
        }
    }

    currentCoroutineContext().job.invokeOnCompletion {
        thread.interrupt()
    }

    /* We want to forward exceptions from the thread into the parent coroutine scope */
    return daemonTestScope.launch {
        threadResult.await()
    }
}

suspend fun HotReloadTestFixture.launchApplication(
    projectPath: String = ":",
    mainClass: String = "MainKt"
) {
    launchDaemonThread {
        val runTask = when (projectMode) {
            ProjectMode.Kmp -> "jvmRun"
            ProjectMode.Jvm -> "run"
        }

        val additionalArguments = when (projectMode) {
            ProjectMode.Kmp -> arrayOf("-DmainClass=$mainClass")
            ProjectMode.Jvm -> arrayOf()
        }

        val runTaskPath = buildString {
            if (projectPath != ":") {
                append(projectPath)
            }

            append(":")
            append(runTask)
        }

        gradleRunner
            .addedArguments("wrapper", runTaskPath, *additionalArguments)
            .build()
    }
}

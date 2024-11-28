package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.agent.analysis.RuntimeInfo
import org.jetbrains.compose.reload.agent.analysis.plus
import org.jetbrains.compose.reload.agent.analysis.resolveInvalidationKey
import org.jetbrains.compose.reload.agent.analysis.update
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.concurrent.atomic.AtomicReference

private val logger = createLogger()

private val runtimeInfo = AtomicReference<RuntimeInfo?>(null)

private val runtimeInfoRedefinitions = AtomicReference<RuntimeInfo?>(null)

internal fun startComposeGroupInvalidationTransformation(instrumentation: Instrumentation) {

    /*
    Instruct Compose to invalidate groups that have changed, after successful reload.
     */
    ComposeHotReloadAgent.invokeAfterReload { reloadRequestId, error ->
        if (error != null) return@invokeAfterReload

        /*
        Capture and update global runtime info state
         */
        val runtimeInfoRedefinitions = runtimeInfoRedefinitions.getAndSet(null) ?: return@invokeAfterReload
        val (previousRuntime, newRuntime) = runtimeInfo.update { info -> info + runtimeInfoRedefinitions }

        val invalidations = newRuntime?.groups.orEmpty().filter { (group, _) ->
            if (group == null) return@filter false
            val currentInvalidationKey = previousRuntime?.resolveInvalidationKey(group) ?: return@filter false
            val newInvalidationKey = newRuntime?.resolveInvalidationKey(group) ?: return@filter true // defensive.
            currentInvalidationKey != newInvalidationKey
        }

        if (invalidations.isEmpty()) {
            logger.orchestration("All groups retained")
        }

        invalidations.forEach { (group, scopes) ->
            if (group == null) return@forEach

            val methods = newRuntime?.groups[group].orEmpty()
                .map { scope -> scope.methodId }.toSet()
                .joinToString(", ", prefix = "(", postfix = ")") { methodId ->
                    "${methodId.className}.${methodId.methodName}"
                }

            logger.orchestration("Invalidating group '${group.key}' $methods")
            invalidateGroupsWithKey(group)

            /*
            Until 'OptimizeNonSkippingGroup' becomes the default, it is wise to also invalidate at least one level
            of known parents. This is because 'remember {}' blocks are wrapped into their own group, however,
            we would like to invalidate the entire scope where the remembered value was used.
             */
            val parentInvalidations = scopes.mapNotNull { scope -> scope.parentGroup }.toSet()
            if (parentInvalidations.isNotEmpty()) {
                logger.orchestration("Invalidating parent groups '${parentInvalidations.joinToString(", ")}'")
                parentInvalidations.forEach { parentGroup ->
                    invalidateGroupsWithKey(parentGroup)
                }
            }
        }
    }

    /*
    Register the transformer which will be invoked on all byte-code updating the global group information
     */
    instrumentation.addTransformer(ComposeGroupInvalidationKeyTransformer)
}

/*
This transformer is intended to run on all classes, so that runtime information about Compose groups
is recorded and invalidations can be tracked.
 */
internal object ComposeGroupInvalidationKeyTransformer : ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray
    ): ByteArray? {
        try {
            val classInfo = RuntimeInfo(classfileBuffer) ?: return classfileBuffer

            if (classBeingRedefined == null) {
                logger.debug("Parsed 'RuntimeInfo' for '$className'")
            } else {
                logger.debug("Parsed 'RuntimeInfo' for '$className' (redefined)")
            }

            /* If we're redefining a class, then we want to add this to 'pending' */
            (if (classBeingRedefined == null) runtimeInfo else runtimeInfoRedefinitions).updateAndGet { runtimeInfo ->
                runtimeInfo + classInfo
            }
        } catch (t: Throwable) {
            logger.error("Failed parsing 'RuntimeInfo' for '$className'", t)
        }

        return classfileBuffer
    }
}

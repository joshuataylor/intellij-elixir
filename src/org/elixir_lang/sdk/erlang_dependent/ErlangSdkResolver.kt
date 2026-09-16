package org.elixir_lang.sdk.erlang_dependent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.elixir_lang.sdk.elixir.ElixirSdkMutation
import org.elixir_lang.sdk.wsl.wslCompat
import java.util.concurrent.ConcurrentHashMap


/**
 * Interface allows mocking in tests
 */
interface ErlangSdkResolver {
    fun resolveErlangSdkResult(elixirSdk: Sdk, sdkModel: SdkModel? = null): ErlangSdkResult

    /**
     * Lightweight resolver that returns only the SDK or null, discarding failure details.
     * Use [resolveErlangSdkResult] when you need the reason for a missing dependency.
     */
    fun resolveErlangSdk(elixirSdk: Sdk, sdkModel: SdkModel? = null): Sdk? =
        when (val result = resolveErlangSdkResult(elixirSdk, sdkModel)) {
            is ErlangSdkResult.Success -> result.sdk
            is ErlangSdkResult.Missing -> null
        }

    companion object {
        fun getInstance(): ErlangSdkResolver =
            ApplicationManager.getApplication().getService(ErlangSdkResolver::class.java)

        /**
         * Returns the first registered Erlang SDK that has a non-null home path, or null if none
         * are registered. Used as a fallback when no Erlang SDK has been explicitly paired with an
         * Elixir SDK.
         */
        @RequiresReadLock
        fun findAnyRegistered(): Sdk? {
            ThreadingAssertions.assertReadAccess()

            return ProjectJdkTable.getInstance()
                .getSdksOfType(org.elixir_lang.sdk.erlang.Type.instance)
                .firstOrNull { it.homePath != null }
        }
    }
}

/**
 * Default implementation.
 *
 * Resolution priority:
 * 1. `erlangSdkHomePath` (stable; survives SDK renames) - WSL-aware path match in ProjectJdkTable
 * 2. `erlangSdkName` (legacy fallback for configs written before home-path was added) - name match;
 *    [ErlangPairingHeal] commits the resolved path to `erlangSdkHomePath` so future lookups use path
 * 3. Both absent → NOT_CONFIGURED
 */
internal class DefaultErlangSdkResolver : ErlangSdkResolver {
    @RequiresReadLock
    override fun resolveErlangSdkResult(elixirSdk: Sdk, sdkModel: SdkModel?): ErlangSdkResult {
        ThreadingAssertions.assertReadAccess()
        val elixirName = elixirSdk.name
        val additionalData = elixirSdk.elixirAdditionalData
            ?: return ErlangSdkResult.Missing(elixirSdk, MissingErlangSdkReason.NOT_CONFIGURED)

        // Check cache first
        additionalData.getCachedErlangSdk()?.let { cached ->
            if (isValidAndExists(cached, sdkModel)) {
                if (cached.homePath.isNullOrBlank()) {
                    return ErlangSdkResult.Missing(
                        elixirSdk,
                        MissingErlangSdkReason.MISSING_HOME_PATH,
                        cached.name,
                    )
                }
                return ErlangSdkResult.Success(cached)
            }
            logger.debug { "[$elixirName] Cached Erlang SDK '${cached.name}' no longer valid" }
            additionalData.setCachedErlangSdk(null)
        }

        // 1. Path-first lookup (stable; survives renames)
        val configuredHomePath = additionalData.getErlangSdkHomePath()?.takeIf { it.isNotBlank() }
        if (configuredHomePath != null) {
            logger.debug { "[$elixirName] Looking up Erlang SDK by home path: $configuredHomePath" }
            val found = findErlangSdkByHomePath(configuredHomePath, sdkModel)
            if (found != null) {
                logger.debug { "[$elixirName] Found Erlang SDK by home path: ${found.name}" }
                additionalData.setCachedErlangSdk(found)
                if (additionalData.getErlangSdkName() != found.name) {
                    ErlangPairingHeal.schedule(elixirSdk, found)
                }
                if (found.homePath.isNullOrBlank()) {
                    return ErlangSdkResult.Missing(elixirSdk, MissingErlangSdkReason.MISSING_HOME_PATH, found.name)
                }
                return ErlangSdkResult.Success(found)
            }
            logger.debug { "[$elixirName] No Erlang SDK found at home path: $configuredHomePath" }
        }

        // 2. Name-based fallback (legacy configs without erlangSdkHomePath)
        val configuredName = additionalData.getErlangSdkName()?.takeIf { it.isNotBlank() }
            ?: return ErlangSdkResult.Missing(elixirSdk, MissingErlangSdkReason.NOT_CONFIGURED)

        logger.debug { "[$elixirName] Falling back to name lookup: $configuredName" }
        val found = findErlangSdkByName(configuredName, sdkModel)
        if (found != null) {
            logger.debug { "[$elixirName] Found Erlang SDK by name: $configuredName - scheduling a commit of its home path" }
            additionalData.setCachedErlangSdk(found)
            ErlangPairingHeal.schedule(elixirSdk, found)
            if (found.homePath.isNullOrBlank()) {
                return ErlangSdkResult.Missing(elixirSdk, MissingErlangSdkReason.MISSING_HOME_PATH, found.name)
            }
            return ErlangSdkResult.Success(found)
        }

        logger.debug { "[$elixirName] Erlang SDK '$configuredName' not found" }
        val candidate =
            sdkModel?.sdks?.find { it.name == configuredName }
                ?: ProjectJdkTable.getInstance().findJdk(configuredName)
        val reason = when {
            candidate == null -> MissingErlangSdkReason.NOT_FOUND
            !Type.staticIsValidDependency(candidate) -> MissingErlangSdkReason.INVALID_TYPE
            else -> MissingErlangSdkReason.NOT_FOUND
        }

        return ErlangSdkResult.Missing(elixirSdk, reason, configuredName)
    }

    private fun isValidAndExists(sdk: Sdk, sdkModel: SdkModel?): Boolean {
        if (!Type.staticIsValidDependency(sdk)) return false
        val name = sdk.name
        val jdkTable = ProjectJdkTable.getInstance()
        return (sdkModel?.sdks?.any { it.name == name } == true)
            || (jdkTable.findJdk(name) != null)
    }

    @RequiresReadLock
    private fun findErlangSdkByHomePath(homePath: String, sdkModel: SdkModel?): Sdk? {
        ThreadingAssertions.assertReadAccess()
        val fromModel = sdkModel?.sdks?.find { sdk ->
            Type.staticIsValidDependency(sdk) && wslCompat.pathsEqualWslAware(sdk.homePath, homePath)
        }
        if (fromModel != null) return fromModel
        return ProjectJdkTable.getInstance().allJdks.firstOrNull { sdk ->
            Type.staticIsValidDependency(sdk) && wslCompat.pathsEqualWslAware(sdk.homePath, homePath)
        }
    }

    private fun findErlangSdkByName(name: String, sdkModel: SdkModel?): Sdk? {
        val jdkTable = ProjectJdkTable.getInstance()
        return sdkModel?.sdks?.find { it.name == name && Type.staticIsValidDependency(it) }
            ?: jdkTable.findJdk(name)?.takeIf { Type.staticIsValidDependency(it) }
    }

    companion object {
        private val logger = Logger.getInstance(DefaultErlangSdkResolver::class.java)
    }
}

/**
 * Commits a pairing the resolver repaired. The resolver runs under a read lock and must not write the live additional
 * data in place: only a modificator commit is saved, and the next commit replaces the live instance.
 */
internal object ErlangPairingHeal {
    private val pending: MutableSet<Sdk> = ConcurrentHashMap.newKeySet()

    fun schedule(elixirSdk: Sdk, erlangSdk: Sdk) {
        // A pairing to an Erlang SDK with no home stores the same blank home, so the next resolution would repair it
        // again, and every repair is a commit.
        if (erlangSdk.homePath.isNullOrBlank()) return
        if (!pending.add(elixirSdk)) return

        val application = ApplicationManager.getApplication()
        application.invokeLater(
            {
                try {
                    WriteAction.run<RuntimeException> { heal(elixirSdk, erlangSdk) }
                } finally {
                    pending.remove(elixirSdk)
                }
            },
            ModalityState.nonModal(),
            application.disposed,
        )
    }

    /**
     * A settings dialog's editable copies are not in the table, and a commit to them would not persist. A pairing
     * changed since the repair was scheduled, for example by the user, is left as it is.
     */
    @RequiresWriteLock
    private fun heal(elixirSdk: Sdk, erlangSdk: Sdk) {
        ThreadingAssertions.assertWriteAccess()
        val sdks = ProjectJdkTable.getInstance().allJdks
        if (sdks.none { it === elixirSdk } || sdks.none { it === erlangSdk }) return

        val data = elixirSdk.elixirAdditionalData ?: return
        val storedHomePath = data.getErlangSdkHomePath()
        val nameMatches = data.getErlangSdkName() == erlangSdk.name
        val homeMatches = !storedHomePath.isNullOrBlank() &&
            wslCompat.pathsEqualWslAware(storedHomePath, erlangSdk.homePath)
        // Either identifier naming this SDK is the same pairing; both naming it leaves nothing to repair, and neither
        // means the pairing changed after the repair was scheduled.
        if (nameMatches == homeMatches) return

        ElixirSdkMutation.applyDependencySelection(elixirSdk, erlangSdk)
    }
}

enum class MissingErlangSdkReason {
    NOT_CONFIGURED,
    NOT_FOUND,
    INVALID_TYPE,
    MISSING_HOME_PATH
}

sealed class ErlangSdkResult {
    data class Success(val sdk: Sdk) : ErlangSdkResult()
    data class Missing(
        val elixirSdkName: String,
        val reason: MissingErlangSdkReason,
        val erlangSdkName: String? = null,
    ) : ErlangSdkResult() {
        constructor(
            elixirSdk: Sdk,
            reason: MissingErlangSdkReason,
            erlangSdkName: String? = null,
        ) : this(elixirSdk.name, reason, erlangSdkName)
    }
}

package org.elixir_lang.facet

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elixir_lang.facet.sdk.ComboBox
import org.elixir_lang.facet.sdk.Model
import org.elixir_lang.sdk.SdkVersionsFiller
import org.elixir_lang.sdk.SdkVersionsListener
import org.elixir_lang.sdk.elixir.ModuleSdkStatus
import org.elixir_lang.sdk.elixir.summaryHtml
import org.elixir_lang.tool_manager.ToolManagerSdkAnalyser
import org.elixir_lang.util.ElixirCoroutineService
import org.elixir_lang.tool_manager.ToolManagerSdkCheckerService
import javax.swing.JButton
import javax.swing.JComponent

/**
 * The dialog's model with its SDK list captured on the EDT. `ProjectSdksModel` keeps its SDKs in a plain `HashMap`
 * that only the EDT mutates and the read lock does not cover, so reading it from a coroutine while the sibling SDKs
 * page adds or removes one can throw or see a half-updated list.
 */
private class CapturedSdkModel(private val live: SdkModel, private val captured: Array<Sdk>) : SdkModel by live {
    override fun getSdks(): Array<Sdk> = captured

    // Kotlin's `by` forwards only the interface's abstract members, so a Java `default` method would run the
    // interface's own body against this wrapper rather than whatever the live model overrides it with.
    override fun addListener(listener: SdkModel.Listener, parentDisposable: Disposable) =
        live.addListener(listener, parentDisposable)
}

/**
 * Either project or module
 */
abstract class Configurable(val module: Module) : UnnamedConfigurable {
    abstract fun initSdk(): Sdk?
    abstract fun applySdk(sdk: Sdk?)
    private val project get() = module.project

    private var scope = newScope()

    private fun newScope() =
        module.project.service<ElixirCoroutineService>().supervisedChildScope("ElixirFacetSdk")
    private val sdksService by lazy { SdksService.getInstance()!! }
    private val projectSdksModel by lazy { sdksService.getModel() }
    var sdk: Sdk? = null
    private var rootPanel: DialogPanel? = null
    private lateinit var sdkComboBox: ComboBox

    /** Live status line under the selector, mirroring the status-bar widget's per-module state. */
    private lateinit var statusLabel: JBLabel

    override fun createComponent(): JComponent {
        rootPanel?.let { return it }

        // A panel rebuilt after [disposeUIResources] needs a live scope. The old one is cancelled, not just replaced,
        // because as a child of the project's scope it would otherwise outlive the dialog.
        scope.cancel()
        scope = newScope()
        sdkComboBox = ComboBox()
        statusLabel = JBLabel()
        val toolManagerButton = createToolManagerButton()

        val dialogPanel = panel {
            row("Elixir SDK:") {
                cell(sdkComboBox)
                toolManagerButton?.let { cell(it) }
            }
            row { cell(statusLabel) }
        }
        rootPanel = dialogPanel

        sdkComboBox.addActionListener { updateStatusLabel() }
        updateStatusLabel()

        // An added SDK's versions are recorded off the registering thread, so its first classification can report it
        // unusable. `any()` because the label must change while the modal dialog is open; setting it touches no model.
        ApplicationManager.getApplication().messageBus.connect(scope).subscribe(
            SdkVersionsListener.TOPIC,
            SdkVersionsListener { _, _ ->
                ApplicationManager.getApplication()
                    .invokeLater({ updateStatusLabel() }, ModalityState.any())
            },
        )

        return dialogPanel
    }

    /**
     * A "Configure from <tool manager>" button, present only when the tool manager (e.g. mise) has
     * an installed Elixir version assigned to **this** module. Clicking configures only this module,
     * leaving other modules (which may lack a tool-manager config, or be deliberately configured
     * differently) untouched.
     */
    private fun createToolManagerButton(): JButton? {
        val analyser = ToolManagerSdkAnalyser.getInstanceIfRegistered(project) ?: return null
        val versions = analyser.latestAnalysisResult
            ?.tmAssignments
            ?.get(module.name)
            ?.takeIf { it.elixir?.installed == true }
            ?: return null

        return JButton("Configure from ${versions.toolManagerName}").apply {
            addActionListener {
                ToolManagerSdkCheckerService.getInstance(project).configureSdks(mapOf(module.name to versions))
                onModuleConfiguredExternally()
            }
        }
    }

    /**
     * After the tool-manager button configures this module, [org.elixir_lang.tool_manager.ToolManagerSdkChecker.configureSdks] has
     * registered the SDK in the JDK table and set this module's Facet SDK directly (bypassing the
     * dialog's model). Surface it in the dropdown by adding it to the model (which fires `sdkAdded`
     * so the combo picks it up) and selecting it. Deliberately avoids `ProjectSdksModel.reset`, which
     * re-clones the shared model and would invalidate SDK references held by other open SDK views.
     */
    private fun onModuleConfiguredExternally() {
        val newSdk = initSdk() ?: return
        if (projectSdksModel.findSdk(newSdk.name) == null) {
            projectSdksModel.addSdk(newSdk)
        }
        sdkComboBox.selectedItem = projectSdksModel.findSdk(newSdk.name)
        updateStatusLabel()
    }

    /** Off the EDT: classifying takes a read action to resolve the paired Erlang SDK, which a writer can hold up. */
    private fun updateStatusLabel() {
        if (!::statusLabel.isInitialized) return
        val selected = sdkComboBox.selectedItem as? Sdk
        // This dialog's model, so an Erlang SDK added on a sibling page and not yet applied is found.
        val sdkModel = CapturedSdkModel(projectSdksModel, projectSdksModel.sdks)
        // Read once for both the fill and the classification: `SdkEditor` commits to this same `Sdk` in a write
        // action, so two reads could straddle that commit and classify a different home from the one filled.
        val selectedHomePath = selected?.homePath
        // Without this the UI dispatcher falls back to `NON_MODAL`, which queues behind the modal Settings dialog, so
        // the label would only be set once the dialog showing it had closed.
        val modality = ModalityState.current().asContextElement()

        scope.launch {
            // An SDK no open project's modules use has never been read, and "not recorded" classifies it invalid.
            selectedHomePath?.let { SdkVersionsFiller.fillIfUnread(it) }
            val summary = "<html>${ModuleSdkStatus.of(selected, sdkModel, selectedHomePath).summaryHtml()}</html>"

            withContext(Dispatchers.UI + modality) {
                // A slower result for an earlier pick must not overwrite a later one.
                if (::statusLabel.isInitialized && sdkComboBox.selectedItem as? Sdk === selected) {
                    statusLabel.text = summary
                }
            }
        }
    }

    override fun isModified(): Boolean {
        if (!::sdkComboBox.isInitialized) {
            return false
        }

        val existingInitialSdk = initSdk()?.let {
            projectSdksModel.findSdk(it.name)
        }

        return existingInitialSdk != sdkComboBox.selectedItem
    }

    override fun apply() {
        if (::sdkComboBox.isInitialized) {
            applySdk(sdkComboBox.selectedItem as Sdk?)
        }
    }

    override fun reset() {
        if (::sdkComboBox.isInitialized) {
            sdkComboBox.selectedItem = initSdk()?.let { projectSdksModel.findSdk(it.name) }
            updateStatusLabel()
        }
    }

    override fun disposeUIResources() {
        scope.cancel()
        // Otherwise a later [createComponent] hands back a panel whose scope is cancelled and never updates.
        rootPanel = null
        // The combo's Model subscribes to the application-lifetime ProjectSdksModel; unsubscribe
        // so listeners don't accumulate across Settings opens (one Model is created per module per
        // Settings open). ModuleAwareProjectConfigurable.disposeUIResources() propagates here for
        // every instantiated per-module configurable.
        if (::sdkComboBox.isInitialized) {
            (sdkComboBox.model as? Model)?.detach()
        }
    }
}

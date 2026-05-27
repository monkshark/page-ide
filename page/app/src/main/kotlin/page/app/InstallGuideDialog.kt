package page.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import page.lsp.LanguageDefinition
import page.ui.GlassTheme
import java.awt.Desktop
import java.net.URI

@Composable
internal fun InstallGuideDialog(
    definition: LanguageDefinition,
    attempted: List<String>,
    onOpenGuide: (String) -> Unit = { url -> runCatching { Desktop.getDesktop().browse(URI(url)) } },
    onInstalled: () -> Unit = {},
    onDismiss: () -> Unit,
    installer: LspInstaller? = null,
) {
    @Suppress("NAME_SHADOWING")
    val installer = installer ?: remember(definition.id) { LspInstallers.forId(definition.id) }
    val initialOs = remember { InstallGuide.initialOsKey() }
    var selectedOs by remember { mutableStateOf(initialOs) }
    var installProgress by remember { mutableStateOf<LspInstaller.Progress?>(null) }
    var selectedVersion by remember(installer) { mutableStateOf<String?>(installer?.installedVersion() ?: installer?.defaultVersion()) }
    var availableVersions by remember(installer) { mutableStateOf<List<String>>(emptyList()) }
    var versionsLoading by remember(installer) { mutableStateOf(true) }
    var showUpstream by remember(installer) { mutableStateOf(false) }
    var showHeavyConfirm by remember(installer) { mutableStateOf(false) }
    var heavyConfirmAccepted by remember(installer) { mutableStateOf(false) }
    var outputLines by remember(installer) { mutableStateOf<List<String>>(emptyList()) }
    var installJob by remember(installer) { mutableStateOf<Job?>(null) }
    var showCancelConfirm by remember(installer) { mutableStateOf(false) }
    val cancelled = remember(installer) { AtomicBoolean(false) }
    val kls = installer as? KlsLspInstaller
    var installedVersion by remember(installer) { mutableStateOf(installer?.activeVersion()) }
    var installedVersions by remember(installer) {
        mutableStateOf(installer?.installedVersions() ?: emptyList())
    }
    LaunchedEffect(installer, installProgress is LspInstaller.Progress.Done, installProgress is LspInstaller.Progress.Failed, installProgress == null) {
        if (installer == null) return@LaunchedEffect
        val p = installProgress
        if (p == null || p is LspInstaller.Progress.Done || p is LspInstaller.Progress.Failed) {
            installedVersion = installer.activeVersion()
            installedVersions = installer.installedVersions()
        }
    }
    val scope = rememberCoroutineScope()
    val canInAppInstall = installer != null
    val precheck = installer?.precheck
    val installing = installProgress.let { it != null && it !is LspInstaller.Progress.Done && it !is LspInstaller.Progress.Failed }
    val focusRequester = remember { FocusRequester() }
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(installer) {
        if (installer != null) {
            val list = withContext(Dispatchers.IO) { installer.availableVersions() }
            availableVersions = list
            val current = selectedVersion
            if (current == null || (list.isNotEmpty() && current !in list)) {
                selectedVersion = installer.installedVersion()?.takeIf { it in list || list.isEmpty() }
                    ?: installer.defaultVersion()?.takeIf { it in list || list.isEmpty() }
                    ?: list.firstOrNull()
            }
            versionsLoading = false
        } else {
            versionsLoading = false
        }
    }

    fun startInstallInternal() {
        val active = installer ?: return
        if (installing) return
        cancelled.set(false)
        installProgress = LspInstaller.Progress.Downloading(0, -1)
        outputLines = emptyList()
        installJob = scope.launch {
            withContext(Dispatchers.IO) {
                active.install(selectedVersion) { p ->
                    if (cancelled.get()) return@install
                    installProgress = p
                    if (p is LspInstaller.Progress.CommandOutput) {
                        outputLines = (outputLines + p.line).takeLast(2000)
                    }
                }
            }
        }
    }

    fun confirmCancelInstall() {
        cancelled.set(true)
        installJob?.cancel()
        installJob = null
        installProgress = null
        outputLines = emptyList()
        showCancelConfirm = false
        val target = installer?.installDir(selectedVersion)?.toFile() ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { target.deleteRecursively() }
        }
    }

    fun startInstall() {
        val active = installer ?: return
        if (installing) return
        if (active.heavyInstall != null && !heavyConfirmAccepted) {
            showHeavyConfirm = true
            return
        }
        startInstallInternal()
    }

    fun applySelected() {
        val label = selectedVersion ?: return
        val active = installer ?: return
        if (!active.applyVersion(label)) return
        onInstalled()
        onDismiss()
    }

    fun uninstallVersion(version: String) {
        val active = installer ?: return
        val dir = active.installDir(version)
        val wasActive = installedVersion == version
        if (selectedVersion == version) selectedVersion = null
        installedVersions = installedVersions.filterNot { it == version }
        if (wasActive) installedVersion = null
        scope.launch(Dispatchers.IO) {
            if (wasActive) {
                val pointer = dir.parent?.resolve("CURRENT")
                if (pointer != null) runCatching { java.nio.file.Files.deleteIfExists(pointer) }
            }
            runCatching { ArchiveExtractors.deleteRecursively(dir) }
            withContext(Dispatchers.Main) { onInstalled() }
        }
    }

    GlassTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false
                    else when (event.key) {
                        Key.Escape -> {
                            if (showHeavyConfirm) { showHeavyConfirm = false; true }
                            else if (showCancelConfirm) { showCancelConfirm = false; true }
                            else if (!installing) { onDismiss(); true }
                            else true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            if (showHeavyConfirm) {
                                showHeavyConfirm = false
                                heavyConfirmAccepted = true
                                startInstallInternal()
                                true
                            } else if (installing) true
                            else if (canInAppInstall && installProgress == null) { startInstall(); true }
                            else { onDismiss(); true }
                        }
                        else -> false
                    }
                }
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                ) { if (!installing) onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(560.dp)
                    .height(460.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                    ) { },
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    Text(
                        text = "Install ${definition.displayName} LSP",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    val description = when {
                        canInAppInstall && precheck is LspInstaller.Precheck.MissingTool ->
                            "${precheck.message} You can install ${definition.displayName} manually below."
                        canInAppInstall ->
                            "PAGE can download ${definition.displayName} (${selectedVersion ?: "latest"}) and install it locally."
                        else ->
                            "PAGE could not find a language server for ${definition.displayName}. Pick your platform and follow the steps below."
                    }
                    Text(
                        text = description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalTextStyle.current.copy(fontSize = 11.sp),
                    )
                    val heavyEstimate = installer?.heavyInstall
                    if (heavyEstimate != null && installProgress == null && precheck is LspInstaller.Precheck.Ok) {
                        Spacer(Modifier.height(8.dp))
                        HeavyEstimateBanner(estimate = heavyEstimate)
                    }
                    Spacer(Modifier.height(10.dp))
                    val precheckBlocked = precheck is LspInstaller.Precheck.MissingTool
                    val showManualSteps = !canInAppInstall ||
                        precheckBlocked ||
                        installProgress is LspInstaller.Progress.Failed
                    if (showManualSteps) {
                        OsTabRow(selected = selectedOs, onSelect = { selectedOs = it })
                        Spacer(Modifier.height(10.dp))
                    }
                    if (installProgress != null) {
                        val failedMessage = (installProgress as? LspInstaller.Progress.Failed)?.let { f ->
                            f.error.message?.takeIf { it.isNotBlank() } ?: f.error.toString()
                        }
                        OutputLogBox(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            lines = outputLines,
                            failedMessage = failedMessage,
                        )
                    } else Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                    ) {
                        val scroll = rememberScrollState()
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
                            if (canInAppInstall && !precheckBlocked && selectedVersion != null) {
                                SectionLabel("Install location")
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = installLocationOf(installer!!, selectedVersion),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                )
                                Spacer(Modifier.height(10.dp))
                                val groups = installer?.versionGroups(availableVersions)
                                val forkVersions = if (kls != null) {
                                    availableVersions.filter {
                                        KlsLspInstaller.parseLabel(it).second != KlsLspInstaller.UPSTREAM
                                    }
                                } else availableVersions
                                val upstreamVersions = if (kls != null) {
                                    availableVersions.filter {
                                        KlsLspInstaller.parseLabel(it).second == KlsLspInstaller.UPSTREAM
                                    }
                                } else emptyList()
                                if (versionsLoading) {
                                    SectionHeader(label = "Recommended (verified)", expanded = true, toggleable = false)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Loading versions…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = LocalTextStyle.current.copy(fontSize = 10.sp),
                                    )
                                } else if (groups != null && groups.isNotEmpty()) {
                                    GroupedVersionList(
                                        groups = groups,
                                        selectedVersion = selectedVersion,
                                        installedVersions = installedVersions,
                                        activeVersion = installedVersion,
                                        onSelect = { selectedVersion = it },
                                        onDeleteVersion = { v -> uninstallVersion(v) },
                                    )
                                } else if (forkVersions.isEmpty()) {
                                    SectionHeader(label = "Recommended (verified)", expanded = true, toggleable = false)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "No versions available (network or rate-limit)",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = LocalTextStyle.current.copy(fontSize = 10.sp),
                                    )
                                } else {
                                    SectionHeader(label = "Recommended (verified)", expanded = true, toggleable = false)
                                    Spacer(Modifier.height(4.dp))
                                    forkVersions.take(50).forEach { v ->
                                        VersionRow(
                                            version = v,
                                            selected = v == selectedVersion,
                                            installed = v in installedVersions,
                                            active = v == installedVersion,
                                            onClick = { selectedVersion = v },
                                            onDelete = if (v in installedVersions) {{ uninstallVersion(v) }} else null,
                                        )
                                    }
                                }
                                if (kls != null) {
                                    Spacer(Modifier.height(10.dp))
                                    SectionHeader(
                                        label = "More versions (upstream)",
                                        expanded = showUpstream,
                                        toggleable = true,
                                        onClick = { showUpstream = !showUpstream },
                                    )
                                }
                                if (kls != null && showUpstream) {
                                    Spacer(Modifier.height(4.dp))
                                    if (versionsLoading) {
                                        Text(
                                            text = "Loading versions…",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = LocalTextStyle.current.copy(fontSize = 10.sp),
                                        )
                                    } else if (upstreamVersions.isEmpty()) {
                                        Text(
                                            text = "No upstream versions",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = LocalTextStyle.current.copy(fontSize = 10.sp),
                                        )
                                    } else {
                                        upstreamVersions.take(50).forEach { v ->
                                            VersionRow(
                                                version = v,
                                                selected = v == selectedVersion,
                                                installed = v in installedVersions,
                                                active = v == installedVersion,
                                                onClick = { selectedVersion = v },
                                                onDelete = if (v in installedVersions) {{ uninstallVersion(v) }} else null,
                                            )
                                        }
                                    }
                                }
                            }
                            if (canInAppInstall && precheck is LspInstaller.Precheck.MissingTool) {
                                Spacer(Modifier.height(10.dp))
                                SectionLabel("Required tool")
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${precheck.tool} — install from ${precheck.installUrl}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                )
                            }
                            if (showManualSteps) {
                                if (canInAppInstall) Spacer(Modifier.height(10.dp))
                                SectionLabel(if (canInAppInstall) "Manual install (fallback)" else "Install")
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = InstallGuide.installText(definition, selectedOs),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                )
                                Spacer(Modifier.height(10.dp))
                                SectionLabel("Expected on PATH")
                                Spacer(Modifier.height(4.dp))
                                val bins = InstallGuide.expectedBinaries(definition, selectedOs)
                                Text(
                                    text = bins.joinToString("  ·  "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                )
                            }
                            if (attempted.isNotEmpty() && !canInAppInstall) {
                                Spacer(Modifier.height(10.dp))
                                SectionLabel("Tried")
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = InstallGuide.formatAttempted(attempted),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                )
                            }
                            (installProgress as? LspInstaller.Progress.Failed)?.let { failed ->
                                val errMsg = failed.error.message ?: failed.error.toString()
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SectionLabel("Install failed")
                                    var errCopied by remember { mutableStateOf(false) }
                                    val errClipboard = LocalClipboardManager.current
                                    Text(
                                        text = if (errCopied) "Copied" else "Copy",
                                        color = if (errCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = LocalTextStyle.current.copy(fontSize = 10.sp),
                                        modifier = Modifier
                                            .clickable {
                                                errClipboard.setText(AnnotatedString(errMsg))
                                                errCopied = true
                                            }
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = errMsg,
                                    color = MaterialTheme.colorScheme.error,
                                    style = LocalTextStyle.current.copy(fontSize = 11.sp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val statusLine = statusLineFor(installProgress, definition.displayName, selectedVersion) ?: "After install, the LSP restarts automatically."
                    Text(
                        text = statusLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalTextStyle.current.copy(fontSize = 10.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canInAppInstall && !precheckBlocked) {
                            val mode = remember(
                                installProgress,
                                selectedVersion,
                                installedVersion,
                                installedVersions,
                            ) {
                                installButtonMode(
                                    progress = installProgress,
                                    selectedVersion = selectedVersion,
                                    activeVersion = installedVersion,
                                    installedVersions = installedVersions,
                                )
                            }
                            val (label, action) = installActionLabel(
                                progress = installProgress,
                                mode = mode,
                                onInstalled = onInstalled,
                                onDismiss = onDismiss,
                                onStart = ::startInstall,
                                onApply = ::applySelected,
                            )
                            val enableInstall = !installing && !versionsLoading &&
                                selectedVersion != null && mode != InstallButtonMode.AlreadyActive
                            if (installing) {
                                InstallGuideButton(
                                    label = "Cancel",
                                    primary = false,
                                    enabled = true,
                                    onClick = { showCancelConfirm = true },
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            InstallGuideButton(
                                label = label,
                                primary = true,
                                enabled = enableInstall,
                                progress = installProgressFraction(installProgress),
                                onClick = action,
                            )
                        } else {
                            InstallGuideButton(
                                label = "Open install guide",
                                primary = false,
                                enabled = true,
                                onClick = { onOpenGuide(definition.installGuideUrl) },
                            )
                            Spacer(Modifier.width(8.dp))
                            InstallGuideButton(label = "Done", primary = true, enabled = true, onClick = onDismiss)
                        }
                    }
                }
            }
            val activeHeavy = installer?.heavyInstall
            if (showHeavyConfirm && activeHeavy != null) {
                HeavyConfirmOverlay(
                    displayName = definition.displayName,
                    estimate = activeHeavy,
                    onCancel = { showHeavyConfirm = false },
                    onOpenGuide = { onOpenGuide(definition.installGuideUrl) },
                    onContinue = {
                        showHeavyConfirm = false
                        heavyConfirmAccepted = true
                        startInstallInternal()
                    },
                )
            }
            if (showCancelConfirm) {
                CancelConfirmOverlay(
                    displayName = definition.displayName,
                    onDismiss = { showCancelConfirm = false },
                    onConfirm = { confirmCancelInstall() },
                )
            }
        }
    }
}

@Composable
private fun CancelConfirmOverlay(
    displayName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(360.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null,
                ) { },
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "Cancel $displayName install",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Cancel the install? All files downloaded so far will be deleted.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = LocalTextStyle.current.copy(fontSize = 11.sp),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InstallGuideButton(
                        label = "Keep installing",
                        primary = false,
                        enabled = true,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(8.dp))
                    InstallGuideButton(
                        label = "Cancel install",
                        primary = true,
                        enabled = true,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

private fun installLocationOf(installer: LspInstaller, version: String?): String {
    val v = version ?: installer.defaultVersion() ?: "latest"
    return LspInstaller.lspHome().resolve(installer.languageId).resolve(v).toString()
}

private fun installProgressFraction(progress: LspInstaller.Progress?): Float = when (val p = progress) {
    null -> 0f
    is LspInstaller.Progress.Downloading -> if (p.total > 0) (p.bytesRead.toFloat() / p.total).coerceIn(0f, 1f) else INDETERMINATE_PROGRESS
    is LspInstaller.Progress.Extracting -> INDETERMINATE_PROGRESS
    is LspInstaller.Progress.CommandOutput -> INDETERMINATE_PROGRESS
    is LspInstaller.Progress.Done -> 0f
    is LspInstaller.Progress.Failed -> 0f
}

internal const val INDETERMINATE_PROGRESS: Float = -1f

private fun statusLineFor(progress: LspInstaller.Progress?, name: String, version: String?): String? = when (val p = progress) {
    null -> null
    is LspInstaller.Progress.Downloading -> {
        val label = if (!version.isNullOrBlank()) "$name $version" else name
        val mb = p.bytesRead / 1024.0 / 1024.0
        if (p.total > 0) {
            val totalMb = p.total / 1024.0 / 1024.0
            val pct = ((p.bytesRead * 100.0) / p.total).toInt().coerceIn(0, 100)
            String.format("Downloading $label… %.1f / %.1f MB (%d%%)", mb, totalMb, pct)
        } else {
            String.format("Downloading $label… %.1f MB", mb)
        }
    }
    is LspInstaller.Progress.Extracting -> p.message
    is LspInstaller.Progress.CommandOutput -> p.line.take(120)
    is LspInstaller.Progress.Done -> "Installed to ${p.executable.parent?.parent ?: p.executable}"
    is LspInstaller.Progress.Failed -> "Install failed — see details above"
}

internal enum class InstallButtonMode { Install, Apply, AlreadyActive }

internal fun installButtonMode(
    progress: LspInstaller.Progress?,
    selectedVersion: String?,
    activeVersion: String?,
    installedVersions: List<String>,
): InstallButtonMode {
    if (progress != null) return InstallButtonMode.Install
    if (selectedVersion == null) return InstallButtonMode.Install
    if (selectedVersion == activeVersion) return InstallButtonMode.AlreadyActive
    if (selectedVersion in installedVersions) return InstallButtonMode.Apply
    return InstallButtonMode.Install
}

private fun installActionLabel(
    progress: LspInstaller.Progress?,
    mode: InstallButtonMode,
    onInstalled: () -> Unit,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onApply: () -> Unit,
): Pair<String, () -> Unit> = when (progress) {
    null -> when (mode) {
        InstallButtonMode.AlreadyActive -> "Already installed" to {}
        InstallButtonMode.Apply -> "Apply & restart LSP" to onApply
        InstallButtonMode.Install -> "Install" to onStart
    }
    is LspInstaller.Progress.Downloading -> "Downloading…" to {}
    is LspInstaller.Progress.Extracting -> "Extracting…" to {}
    is LspInstaller.Progress.CommandOutput -> "Working…" to {}
    is LspInstaller.Progress.Done -> "Restart LSP" to { onInstalled(); onDismiss() }
    is LspInstaller.Progress.Failed -> "Retry install" to onStart
}

@Composable
private fun OsTabRow(selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for ((index, key) in InstallGuide.OS_KEYS.withIndex()) {
            if (index > 0) Spacer(Modifier.width(8.dp))
            OsTab(label = InstallGuide.osLabel(key), active = key == selected, onClick = { onSelect(key) })
        }
    }
}

@Composable
private fun OsTab(label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surface
    val fg = if (active) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    val borderAlpha = if (active) 0.7f else 0.4f
    Box(
        modifier = Modifier
            .height(26.dp)
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(text = label, color = fg)
    }
}

@Composable
private fun GroupedVersionList(
    groups: List<LspInstaller.VersionGroup>,
    selectedVersion: String?,
    installedVersions: List<String>,
    activeVersion: String?,
    onSelect: (String) -> Unit,
    onDeleteVersion: ((String) -> Unit)? = null,
) {
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }
    for (group in groups) {
        val isExpanded = group.label in expandedGroups
        val rec = group.recommended
        val isRecSelected = rec == selectedVersion
        val isRecActive = rec == activeVersion
        val isRecInstalled = rec in installedVersions
        val rowBg = if (isRecSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
        val recColor = when {
            isRecActive -> MaterialTheme.colorScheme.primary
            isRecInstalled -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(rowBg)
                .clickable { onSelect(rec) }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val arrow = if (isExpanded) "▼" else "▶"
                Text(
                    text = arrow,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            expandedGroups = if (isExpanded) expandedGroups - group.label else expandedGroups + group.label
                        }
                        .padding(end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = LocalTextStyle.current.copy(
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
                Text(
                    text = rec,
                    color = recColor,
                    style = LocalTextStyle.current.copy(
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = group.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = LocalTextStyle.current.copy(fontSize = 10.sp),
                )
                if (isRecActive) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "· current",
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        style = LocalTextStyle.current.copy(fontSize = 10.sp),
                    )
                } else if (isRecInstalled) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "· installed",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalTextStyle.current.copy(fontSize = 10.sp),
                    )
                }
            }
        }
        if (isExpanded) {
            group.versions.drop(1).forEach { v ->
                VersionRow(
                    version = v,
                    selected = v == selectedVersion,
                    installed = v in installedVersions,
                    active = v == activeVersion,
                    onClick = { onSelect(v) },
                    onDelete = if (v in installedVersions && onDeleteVersion != null) {{ onDeleteVersion(v) }} else null,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun VersionRow(
    version: String,
    selected: Boolean,
    installed: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    badge: String? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val bg = when {
        confirmDelete -> Color(0xFFf85149).copy(alpha = 0.08f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val versionColor = when {
        active -> MaterialTheme.colorScheme.primary
        installed -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(bg)
            .then(if (!confirmDelete) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (confirmDelete) {
                val confirmStyle = LocalTextStyle.current.copy(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                )
                Text(text = "Delete $version?", color = Color(0xFFf85149), style = confirmStyle)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Yes",
                    color = Color(0xFFf85149),
                    style = confirmStyle,
                    modifier = Modifier.clickable { confirmDelete = false; onDelete?.invoke() }.padding(horizontal = 6.dp),
                )
                Text(
                    text = "No",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = confirmStyle,
                    modifier = Modifier.clickable { confirmDelete = false }.padding(horizontal = 6.dp),
                )
            } else {
                Text(
                    text = version,
                    color = versionColor,
                    style = LocalTextStyle.current.copy(
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
                val suffix = when {
                    active -> "· current"
                    installed -> "· installed"
                    badge != null -> "· $badge"
                    else -> null
                }
                if (suffix != null) {
                    Spacer(Modifier.width(8.dp))
                    val suffixColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = suffix,
                        color = suffixColor,
                        style = LocalTextStyle.current.copy(
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        ),
                    )
                }
                if (installed && onDelete != null) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .height(22.dp)
                            .width(22.dp)
                            .clickable { confirmDelete = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "×",
                            color = Color(0xFFf85149).copy(alpha = 0.6f),
                            style = LocalTextStyle.current.copy(
                                fontSize = 12.sp,
                                lineHeight = 12.sp,
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    expanded: Boolean,
    toggleable: Boolean,
    onClick: () -> Unit = {},
) {
    val chevron = if (expanded) "▼" else "▶"
    val rowModifier = Modifier
        .fillMaxWidth()
        .height(20.dp)
        .let { if (toggleable) it.clickable(onClick = onClick) else it }
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        if (toggleable) {
            Text(
                text = chevron,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LocalTextStyle.current.copy(
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = LocalTextStyle.current.copy(
                fontSize = 10.sp,
                lineHeight = 10.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = LocalTextStyle.current.copy(fontSize = 10.sp),
    )
}

@Composable
private fun InstallGuideButton(
    label: String,
    primary: Boolean,
    enabled: Boolean = true,
    progress: Float = 0f,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    val primaryColor = MaterialTheme.colorScheme.primary
    val indeterminate = progress < 0f
    val active = primary && (indeterminate || progress > 0f)
    val bg = if (primary) {
        if (active) primaryColor.copy(alpha = 0.25f * alpha) else primaryColor.copy(alpha = alpha)
    } else MaterialTheme.colorScheme.surface
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = alpha)
    else MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val sizing = if (primary) Modifier.width(170.dp).height(28.dp)
    else Modifier.height(28.dp)
    Box(
        modifier = sizing
            .clipToBounds()
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f * alpha))
            .let { if (enabled) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            if (indeterminate) {
                val transition = rememberInfiniteTransition(label = "installButtonIndeterminate")
                val phase by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "phase",
                )
                val barFraction = 0.55f
                BoxWithConstraints(modifier = Modifier.matchParentSize().clipToBounds()) {
                    val barWidth = maxWidth * barFraction
                    val travel = maxWidth + barWidth
                    val shimmer = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to primaryColor.copy(alpha = 0.85f * alpha),
                        1f to Color.Transparent,
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = -barWidth + travel * phase)
                            .fillMaxHeight()
                            .width(barWidth)
                            .background(shimmer),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(primaryColor.copy(alpha = alpha))
                        .align(Alignment.CenterStart),
                )
            }
        }
        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
            ButtonLabel(text = label, color = fg)
        }
    }
}

@Composable
private fun ButtonLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = LocalTextStyle.current.copy(
            fontSize = 11.sp,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )
}

@Composable
private fun HeavyEstimateBanner(estimate: LspInstaller.HeavyInstallEstimate) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column {
            Text(
                text = "${estimate.sizeEstimate} · ${estimate.durationEstimate}",
                color = MaterialTheme.colorScheme.primary,
                style = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = estimate.notes,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LocalTextStyle.current.copy(fontSize = 10.sp),
            )
        }
    }
}


@Composable
private fun OutputLogBox(modifier: Modifier = Modifier, lines: List<String>, failedMessage: String? = null) {
    val scroll = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(lines.size) {
        runCatching { scroll.scrollTo(scroll.maxValue) }
    }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1200)
            copied = false
        }
    }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (failedMessage != null) {
                val errorScroll = rememberScrollState()
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.foundation.LocalContextMenuRepresentation provides page.ui.CompactContextMenuRepresentation,
                ) {
                    androidx.compose.foundation.ContextMenuArea(
                        items = {
                            listOf(
                                androidx.compose.foundation.ContextMenuItem("Copy error") {
                                    clipboard.setText(AnnotatedString(failedMessage))
                                },
                            )
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                .verticalScroll(errorScroll)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Install failed: $failedMessage",
                                color = MaterialTheme.colorScheme.error,
                                style = LocalTextStyle.current.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (lines.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalTextStyle.current.copy(fontSize = 10.sp),
                        modifier = Modifier
                            .clickable {
                                clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                                copied = true
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            SelectionContainer(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
                    for (line in lines) {
                        Text(
                            text = line,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = LocalTextStyle.current.copy(
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeavyConfirmOverlay(
    displayName: String,
    estimate: LspInstaller.HeavyInstallEstimate,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
            ) { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(440.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null,
                ) { },
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "Install $displayName",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${estimate.sizeEstimate} · ${estimate.durationEstimate}",
                    color = MaterialTheme.colorScheme.primary,
                    style = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = estimate.notes,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = LocalTextStyle.current.copy(fontSize = 11.sp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Closing this dialog while installing keeps the background task running.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = LocalTextStyle.current.copy(fontSize = 10.sp),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InstallGuideButton(
                        label = "Cancel",
                        primary = false,
                        enabled = true,
                        onClick = onCancel,
                    )
                    Spacer(Modifier.width(8.dp))
                    InstallGuideButton(
                        label = "Manager guide",
                        primary = false,
                        enabled = true,
                        onClick = onOpenGuide,
                    )
                    Spacer(Modifier.width(8.dp))
                    InstallGuideButton(
                        label = "Continue install",
                        primary = true,
                        enabled = true,
                        onClick = onContinue,
                    )
                }
            }
        }
    }
}

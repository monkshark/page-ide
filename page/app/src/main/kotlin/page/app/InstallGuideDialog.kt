package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    installer: LspInstaller? = LspInstallers.forId(definition.id),
) {
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
    var outputExpanded by remember(installer) { mutableStateOf(false) }
    val kls = installer as? KlsLspInstaller
    val installedVersion = remember(installer, installProgress) { installer?.installedVersion() }
    val installedVersions = remember(installer, installProgress) {
        kls?.installedVersions() ?: listOfNotNull(installer?.installedVersion())
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
            versionsLoading = false
        } else {
            versionsLoading = false
        }
    }

    fun startInstallInternal() {
        val active = installer ?: return
        if (installing) return
        installProgress = LspInstaller.Progress.Downloading(0, -1)
        outputLines = emptyList()
        scope.launch {
            withContext(Dispatchers.IO) {
                active.install(selectedVersion) { p ->
                    installProgress = p
                    if (p is LspInstaller.Progress.CommandOutput) {
                        outputLines = (outputLines + p.line).takeLast(200)
                    }
                    if (p is LspInstaller.Progress.Failed) {
                        outputExpanded = true
                    }
                }
            }
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
        val k = kls ?: return
        k.applyVersion(label)
        onInstalled()
        onDismiss()
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                    ) {
                        val scroll = rememberScrollState()
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
                            if (canInAppInstall && !precheckBlocked) {
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
                                val forkVersions = availableVersions.filter {
                                    KlsLspInstaller.parseLabel(it).second != KlsLspInstaller.UPSTREAM
                                }
                                val upstreamVersions = availableVersions.filter {
                                    KlsLspInstaller.parseLabel(it).second == KlsLspInstaller.UPSTREAM
                                }
                                SectionHeader(label = "Recommended (verified)", expanded = true, toggleable = false)
                                Spacer(Modifier.height(4.dp))
                                if (versionsLoading) {
                                    Text(
                                        text = "Loading versions…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = LocalTextStyle.current.copy(fontSize = 10.sp),
                                    )
                                } else if (forkVersions.isEmpty()) {
                                    Text(
                                        text = "No versions available (network or rate-limit)",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = LocalTextStyle.current.copy(fontSize = 10.sp),
                                    )
                                } else {
                                    forkVersions.take(20).forEach { v ->
                                        VersionRow(
                                            version = v,
                                            selected = v == selectedVersion,
                                            installed = v in installedVersions,
                                            active = v == installedVersion,
                                            onClick = { selectedVersion = v },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                SectionHeader(
                                    label = "More versions (upstream)",
                                    expanded = showUpstream,
                                    toggleable = true,
                                    onClick = { showUpstream = !showUpstream },
                                )
                                if (showUpstream) {
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
                                        upstreamVersions.take(20).forEach { v ->
                                            VersionRow(
                                                version = v,
                                                selected = v == selectedVersion,
                                                installed = v in installedVersions,
                                                active = v == installedVersion,
                                                onClick = { selectedVersion = v },
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
                                Spacer(Modifier.height(10.dp))
                                SectionLabel("Install failed")
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = failed.error.message ?: failed.error.toString(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = LocalTextStyle.current.copy(fontSize = 11.sp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val statusLine = statusLineFor(installProgress, definition.displayName) ?: "After install, the LSP restarts automatically."
                    Text(
                        text = statusLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalTextStyle.current.copy(fontSize = 10.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (outputLines.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        OutputToggle(
                            expanded = outputExpanded,
                            lineCount = outputLines.size,
                            onClick = { outputExpanded = !outputExpanded },
                        )
                        if (outputExpanded) {
                            Spacer(Modifier.height(4.dp))
                            OutputLogBox(lines = outputLines)
                        }
                    }
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
                            val enableInstall = !installing && mode != InstallButtonMode.AlreadyActive
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
        }
    }
}

private fun installLocationOf(installer: LspInstaller, version: String?): String {
    val v = version ?: installer.defaultVersion() ?: "latest"
    return LspInstaller.lspHome().resolve(installer.languageId).resolve(v).toString()
}

private fun installProgressFraction(progress: LspInstaller.Progress?): Float = when (val p = progress) {
    null -> 0f
    is LspInstaller.Progress.Downloading -> if (p.total > 0) (p.bytesRead.toFloat() / p.total).coerceIn(0f, 1f) else 0.05f
    is LspInstaller.Progress.Extracting -> 1f
    is LspInstaller.Progress.CommandOutput -> 0.5f
    is LspInstaller.Progress.Done -> 0f
    is LspInstaller.Progress.Failed -> 0f
}

private fun statusLineFor(progress: LspInstaller.Progress?, name: String): String? = when (val p = progress) {
    null -> null
    is LspInstaller.Progress.Downloading -> {
        val mb = p.bytesRead / 1024.0 / 1024.0
        if (p.total > 0) {
            val totalMb = p.total / 1024.0 / 1024.0
            val pct = ((p.bytesRead * 100.0) / p.total).toInt().coerceIn(0, 100)
            String.format("Downloading $name… %.1f / %.1f MB (%d%%)", mb, totalMb, pct)
        } else {
            String.format("Downloading $name… %.1f MB", mb)
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
private fun VersionRow(
    version: String,
    selected: Boolean,
    installed: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
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
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
    val bg = if (primary) {
        if (progress > 0f) primaryColor.copy(alpha = 0.25f * alpha) else primaryColor.copy(alpha = alpha)
    } else MaterialTheme.colorScheme.surface
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = alpha)
    else MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val sizing = if (primary) Modifier.width(170.dp).height(28.dp)
    else Modifier.height(28.dp)
    Box(
        modifier = sizing
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f * alpha))
            .let { if (enabled) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (primary && progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(primaryColor.copy(alpha = alpha))
                    .align(Alignment.CenterStart),
            )
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
private fun OutputToggle(expanded: Boolean, lineCount: Int, onClick: () -> Unit) {
    val chevron = if (expanded) "▼" else "▶"
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chevron,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = LocalTextStyle.current.copy(fontSize = 9.sp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (expanded) "출력 숨기기" else "자세히 보기 ($lineCount 줄)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = LocalTextStyle.current.copy(fontSize = 10.sp),
        )
    }
}

@Composable
private fun OutputLogBox(lines: List<String>) {
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size) {
        runCatching { scroll.scrollTo(scroll.maxValue) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
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
                    text = "$displayName 설치 확인",
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
                    text = "설치 도중에 다이얼로그를 닫아도 백그라운드 작업이 계속 진행될 수 있어요.",
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
                        label = "취소",
                        primary = false,
                        enabled = true,
                        onClick = onCancel,
                    )
                    Spacer(Modifier.width(8.dp))
                    InstallGuideButton(
                        label = "매니저 가이드",
                        primary = false,
                        enabled = true,
                        onClick = onOpenGuide,
                    )
                    Spacer(Modifier.width(8.dp))
                    InstallGuideButton(
                        label = "계속 설치",
                        primary = true,
                        enabled = true,
                        onClick = onContinue,
                    )
                }
            }
        }
    }
}

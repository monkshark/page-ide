package page.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import page.app.dartSiblingLspStatus
import page.app.lspStatusLineText
import page.app.state.EditorWorkspaceState
import page.editor.FileKind
import page.editor.FileKinds
import page.editor.TextBuffer
import page.language.LspController
import page.language.LspRouter
import page.lsp.DiagnosticSeverity
import page.runtime.InstallProgressRegistry
import page.runtime.PubSyncRegistry
import page.ui.CompactDropdown
import page.ui.Glass

@Composable
internal fun GlobalStatusBar(
    editor: EditorWorkspaceState,
    lspRouter: LspRouter,
    todoCount: Int,
    runtimeVersions: Map<String, String>,
    runtimeSources: Map<String, String>,
    runtimeBuildFileVersions: Map<String, String>,
    onProblemsToggle: () -> Unit,
    onTodoToggle: () -> Unit,
    onRuntimeClick: ((String) -> Unit)?,
    neighbours: page.atlas.graph.FileNeighbours? = null,
    structureInMargins: Boolean = false,
    groupOf: (java.nio.file.Path) -> String? = { null },
    onOpenFile: (java.nio.file.Path) -> Unit = {},
    panels: StatusPanels? = null,
    modifier: Modifier = Modifier,
) {
    val pane = editor.focused()
    val active = pane.book.active
    val kind = active?.let { FileKinds.classify(it.path) }
    val showCursor = active != null && kind != FileKind.IMAGE

    val value = pane.editorValue
    val buffer = remember(value.text) { TextBuffer(value.text) }
    val caret = buffer.lineColOf(value.selection.start.coerceIn(0, buffer.length))

    val activeCtrl = active?.path?.let { lspRouter.existingControllerFor(it) }
    val diagnostics = if (showCursor) activeCtrl?.diagnosticsFor(active!!.path).orEmpty() else emptyList()
    val errorCount = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warningCount = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }

    val lspStatus = if (active != null) lspStatusLineText(lspRouter, active.path) else null
    val lspStatusText = lspStatus?.first
    val siblingLsp = if (active != null) dartSiblingLspStatus(lspRouter, active.path) else null

    val activeExt = remember(active?.path) {
        active?.path?.fileName?.toString()?.lowercase()?.substringAfterLast('.', "") ?: ""
    }
    val runtimeInfo = remember(activeExt, runtimeVersions, runtimeSources, runtimeBuildFileVersions) {
        runtimeInfoFor(activeExt, runtimeVersions, runtimeSources, runtimeBuildFileVersions)
    }

    val ctrlActivities = activeCtrl?.activities?.values
        ?.sortedBy { it.startedAtMs }
        ?.toList().orEmpty()
    val globalStarting = lspRouter.startingActivities
    val installActivities = InstallProgressRegistry.entries.values.map { e ->
        val frac = (e.progress as? page.runtime.LspInstaller.Progress.Downloading)
            ?.takeIf { it.total > 0 }
            ?.let { (it.bytesRead.toFloat() / it.total.toFloat()).coerceIn(0f, 1f) }
        LspController.Activity(
            kind = "install",
            label = "${e.displayName} (installing)",
            startedAtMs = e.startedAtMs,
            progress = frac,
            installerId = e.installerId,
        )
    }
    val removalActivities = InstallProgressRegistry.removals.values.map { r ->
        LspController.Activity(
            kind = "remove",
            label = "${r.phase} ${r.version}",
            startedAtMs = r.startedAtMs,
            progress = r.fraction,
            installerId = r.installerId,
        )
    }
    val pubActivities = PubSyncRegistry.entries.values.map { e ->
        LspController.Activity(kind = "pub", label = e.label, startedAtMs = e.startedAtMs)
    }
    val lspActivities = (globalStarting + ctrlActivities + installActivities + removalActivities + pubActivities)
        .distinctBy { it.kind + it.label }
        .sortedBy { it.startedAtMs }

    Surface(
        modifier = modifier.fillMaxWidth().height(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (panels != null) PanelToggles(panels, onProblemsToggle)
            if (showCursor) {
                StatusItem("Ln ${caret.line + 1}, Col ${caret.col + 1}")
                StatusItem("${buffer.lineCount} lines")
                StatusItem("${buffer.length} chars")
                if (errorCount > 0) DiagnosticBadge(
                    count = errorCount,
                    color = Glass.colors.error,
                    label = "errors",
                    onClick = onProblemsToggle,
                )
                if (warningCount > 0) DiagnosticBadge(
                    count = warningCount,
                    color = Glass.colors.warn,
                    label = "warnings",
                    onClick = onProblemsToggle,
                )
                if (neighbours != null && !structureInMargins) StructureChips(
                    fileName = active?.path?.fileName?.toString() ?: "",
                    neighbours = neighbours,
                    groupOf = groupOf,
                    onOpenFile = onOpenFile,
                )
            }
            TodoStatusBadge(
                count = todoCount,
                color = Glass.colors.muted,
                onClick = onTodoToggle,
            )
            val showActivities = lspActivities.isNotEmpty()
            val showLifecycle = !lspStatusText.isNullOrBlank()
            val showRuntime = showCursor && runtimeInfo != null
            if (showActivities || showLifecycle || siblingLsp != null || showRuntime) {
                Box(modifier = Modifier.weight(1f))
                if (showActivities) {
                    LspActivitiesItem(
                        activities = lspActivities,
                        onActivityClick = { act -> act.installerId?.let { onRuntimeClick?.invoke(it) } },
                    )
                }
                if (showLifecycle) {
                    val lspId = lspStatus!!.second
                    LspLifecycleItem(
                        text = lspStatusText!!,
                        onClick = {
                            val ctrl = activeCtrl
                            if (ctrl != null) ctrl.openInstallGuide() else onRuntimeClick?.invoke(lspId)
                        },
                    )
                }
                if (siblingLsp != null) {
                    val (siblingText, siblingId) = siblingLsp
                    LspLifecycleItem(
                        text = siblingText,
                        onClick = onRuntimeClick?.let { click -> { click(siblingId) } },
                    )
                }
                if (showRuntime) {
                    val (label, id, tooltip) = runtimeInfo!!
                    RuntimeVersionItem(
                        label = label,
                        tooltip = tooltip?.let { "from $it" },
                        onClick = { onRuntimeClick?.invoke(id) },
                    )
                }
            }
        }
    }
}

internal fun isOlderThan(installed: String, required: String): Boolean {
    fun parts(v: String) = v.split('.').map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
    val have = parts(installed)
    val want = parts(required)
    for (i in 0 until maxOf(have.size, want.size)) {
        val a = have.getOrElse(i) { 0 }
        val b = want.getOrElse(i) { 0 }
        if (a != b) return a < b
    }
    return false
}

internal fun runtimeInfoFor(
    activeExt: String,
    runtimeVersions: Map<String, String>,
    runtimeSources: Map<String, String>,
    runtimeBuildFileVersions: Map<String, String>,
): Triple<String, String, String?>? {
    fun build(name: String, key: String, id: String): Triple<String, String, String?> {
        val ver = runtimeVersions[key]
        val bfVer = runtimeBuildFileVersions[key]
        val src = runtimeSources[key]
        if (ver == null) {
            val tooltip = if (bfVer != null) "Project requires $bfVer ($src) — click to install" else "Click to install"
            return Triple("$name (not installed)", id, tooltip)
        }
        val tooOld = bfVer != null && isOlderThan(ver, bfVer)
        val label = if (tooOld) "$name $ver ⚠" else "$name $ver"
        val tooltip = when {
            tooOld -> "Project requires $bfVer ($src), using $ver"
            src != null -> "from $src"
            else -> null
        }
        return Triple(label, id, tooltip)
    }
    return when (activeExt) {
        "java" -> build("JDK", "java", "jdk")
        "js", "mjs", "cjs", "ts" -> build("Node", "js", "node")
        "py" -> build("Python", "py", "python-runtime")
        "go" -> build("Go", "go", "go-sdk")
        "c", "cpp", "cc", "cxx", "h", "hpp" -> build("Clang", "cpp", "cpp-toolchain")
        "rs" -> build("Rust", "rs", "rust-runtime")
        "cs" -> build(".NET", "cs", "dotnet-runtime")
        else -> null
    }
}

internal enum class LspDot { READY, WAITING, PROBLEM }

internal fun lspDotFor(text: String): LspDot = when {
    listOf("failed", "missing", "not installed").any { it in text } -> LspDot.PROBLEM
    listOf("starting", "idle", "not started").any { it in text } -> LspDot.WAITING
    else -> LspDot.READY
}

@Composable
private fun LspLifecycleItem(text: String, onClick: (() -> Unit)? = null) {
    val colors = Glass.colors
    val color = if (onClick != null) colors.text else colors.muted
    val mod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(verticalAlignment = Alignment.CenterVertically, modifier = mod) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when (lspDotFor(text)) {
                        LspDot.READY -> colors.success
                        LspDot.WAITING -> colors.faint
                        LspDot.PROBLEM -> colors.danger
                    },
                ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuntimeVersionItem(label: String, tooltip: String? = null, onClick: (() -> Unit)? = null) {
    val colors = Glass.colors
    val mod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    if (tooltip != null) {
        TooltipArea(
            tooltip = {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                ) {
                    Text(
                        text = tooltip,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            },
        ) {
            RuntimePill(label = label, modifier = mod)
        }
    } else {
        RuntimePill(label = label, modifier = mod)
    }
}

@Composable
private fun RuntimePill(label: String, modifier: Modifier = Modifier) {
    val colors = Glass.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Glass.radius.sm))
            .background(colors.primarySoft)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.primary)
    }
}

@Composable
private fun LspActivitiesItem(
    activities: List<LspController.Activity>,
    onActivityClick: ((LspController.Activity) -> Unit)? = null,
) {
    if (activities.isEmpty()) return
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activities.size) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    var expanded by remember { mutableStateOf(false) }
    val first = activities.first()
    val firstElapsed = ((nowMs - first.startedAtMs) / 1000L).coerceAtLeast(0L).toInt()
    val canExpand = activities.size >= 2
    val firstClickable = first.installerId != null && onActivityClick != null
    val rowMod = Modifier.then(
        when {
            canExpand -> Modifier.clickable { expanded = !expanded }
            firstClickable -> Modifier.clickable { onActivityClick!!(first) }
            else -> Modifier
        },
    )
    Box {
        Row(
            modifier = rowMod,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActivityProgressBar(first.progress)
            Text(
                text = if (firstElapsed > 0) "LSP · ${first.label} (${firstElapsed}s)"
                else "LSP · ${first.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canExpand) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "+${activities.size - 1}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        CompactDropdown(
            expanded = canExpand && expanded,
            onDismissRequest = { expanded = false },
            minWidth = 220.dp,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (a in activities) {
                        val secs = ((nowMs - a.startedAtMs) / 1000L).coerceAtLeast(0L).toInt()
                        val clickable = a.installerId != null && onActivityClick != null
                        Row(
                            modifier = Modifier.then(
                                if (clickable) Modifier.clickable {
                                    expanded = false
                                    onActivityClick!!(a)
                                } else Modifier,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActivityProgressBar(a.progress)
                            Text(
                                text = if (secs > 0) "${a.label} (${secs}s)" else a.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityProgressBar(progress: Float?) {
    val barMod = Modifier.width(72.dp).height(3.dp)
    if (progress != null) {
        val animated by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(durationMillis = 300),
            label = "installProgress",
        )
        LinearProgressIndicator(
            progress = { animated },
            modifier = barMod,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    } else {
        LinearProgressIndicator(
            modifier = barMod,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun DiagnosticBadge(
    count: Int,
    color: Color,
    label: String,
    onClick: (() -> Unit)? = null,
) {
    val rowMod = Modifier.then(
        if (onClick != null) Modifier.clickable { onClick() } else Modifier,
    )
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodoStatusBadge(
    count: Int,
    color: Color,
    onClick: (() -> Unit)? = null,
) {
    val rowMod = Modifier.then(
        if (onClick != null) Modifier.clickable { onClick() } else Modifier,
    )
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )
        Text(
            text = if (count > 0) "TODO $count" else "TODO",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StructureChips(
    fileName: String,
    neighbours: page.atlas.graph.FileNeighbours,
    groupOf: (java.nio.file.Path) -> String?,
    onOpenFile: (java.nio.file.Path) -> Unit,
) {
    var open by remember(fileName) { mutableStateOf<StructureView?>(null) }
    val countText = neighbours.usedBy.size.toString()
    Box {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (neighbours.cycle.isNotEmpty()) {
                StructureChip(
                    color = Glass.colors.warn,
                    label = "In a loop",
                    tooltip = "This file is part of a dependency loop. Click to see the loop.",
                    onClick = { open = if (open == StructureView.CYCLE) null else StructureView.CYCLE },
                )
            }
            if (neighbours.usedBy.isNotEmpty()) {
                StructureChip(
                    color = null,
                    label = "Used by $countText",
                    tooltip = "$countText files use this file. Click to see which.",
                    onClick = { open = if (open == StructureView.USED_BY || open == StructureView.USES) null else StructureView.USED_BY },
                )
            }
        }
        val view = open
        if (view != null) {
            StructurePopover(
                view = view,
                fileName = fileName,
                neighbours = neighbours,
                groupOf = groupOf,
                onViewChange = { open = it },
                onOpen = { open = null; onOpenFile(it) },
                onDismiss = { open = null },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StructureChip(
    color: Color?,
    label: String,
    tooltip: String,
    onClick: (() -> Unit)?,
) {
    val rowMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    val textColor = if (color == null) {
        if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
            ) {
                Text(
                    text = tooltip,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
    ) {
        Row(
            modifier = rowMod,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (color != null) {
                Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
            )
        }
    }
}

@Composable
private fun StatusItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal data class StatusPanels(
    val problemsOpen: Boolean,
    val problemsCount: Int,
    val terminalOpen: Boolean,
    val onTerminalToggle: () -> Unit,
    val outputOpen: Boolean,
    val onOutputToggle: () -> Unit,
)

@Composable
private fun PanelToggles(panels: StatusPanels, onProblemsToggle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        PanelToggle("Problems", panels.problemsOpen, onProblemsToggle, count = panels.problemsCount)
        PanelToggle("Terminal", panels.terminalOpen, panels.onTerminalToggle)
        PanelToggle("Output", panels.outputOpen, panels.onOutputToggle)
    }
}

@Composable
private fun PanelToggle(label: String, open: Boolean, onClick: () -> Unit, count: Int = 0) {
    val colors = Glass.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (open) colors.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag("panel-toggle-${label.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (count > 0) Box(Modifier.size(6.dp).clip(CircleShape).background(colors.warn))
        Text(label, color = if (open) colors.text else colors.muted, fontSize = 12.sp)
        if (count > 0) Text(count.toString(), color = if (open) colors.text else colors.muted, fontSize = 12.sp, fontFamily = page.ui.EditorFontFamily)
    }
}

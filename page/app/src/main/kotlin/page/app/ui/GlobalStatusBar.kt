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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import page.app.InstallProgressRegistry
import page.app.lspStatusLineText
import page.app.state.EditorWorkspaceState
import page.editor.FileKind
import page.editor.FileKinds
import page.editor.TextBuffer
import page.language.LspController
import page.language.LspRouter
import page.lsp.DiagnosticSeverity
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
    modifier: Modifier = Modifier,
) {
    val pane = editor.focused()
    val active = pane.book.active
    val kind = active?.let { FileKinds.classify(it.path) }
    val showCursor = active != null && kind != FileKind.IMAGE

    val value = pane.editorValue
    val buffer = remember(value.text) { TextBuffer(value.text) }
    val caret = buffer.lineColOf(value.selection.start.coerceIn(0, buffer.length))

    val activeCtrl = active?.path?.let { lspRouter.controllerFor(it) }
    val diagnostics = if (showCursor) activeCtrl?.diagnosticsFor(active!!.path).orEmpty() else emptyList()
    val errorCount = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warningCount = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }

    val lspStatusText = if (active != null) lspStatusLineText(lspRouter, active.path) else null

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
    val lspActivities = (globalStarting + ctrlActivities + installActivities)
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
            }
            TodoStatusBadge(
                count = todoCount,
                color = MaterialTheme.colorScheme.secondary,
                onClick = onTodoToggle,
            )
            val showActivities = lspActivities.isNotEmpty()
            val showLifecycle = !lspStatusText.isNullOrBlank()
            val showRuntime = showCursor && runtimeInfo != null
            if (showActivities || showLifecycle || showRuntime) {
                Box(modifier = Modifier.weight(1f))
                if (showActivities) {
                    LspActivitiesItem(
                        activities = lspActivities,
                        onActivityClick = { act -> act.installerId?.let { onRuntimeClick?.invoke(it) } },
                    )
                }
                if (showLifecycle) {
                    LspLifecycleItem(text = lspStatusText!!, onClick = { activeCtrl?.openInstallGuide() })
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

internal fun runtimeInfoFor(
    activeExt: String,
    runtimeVersions: Map<String, String>,
    runtimeSources: Map<String, String>,
    runtimeBuildFileVersions: Map<String, String>,
): Triple<String, String, String?>? {
    fun build(name: String, key: String, id: String): Triple<String, String, String?> {
        val ver = runtimeVersions[key] ?: "?"
        val bfVer = runtimeBuildFileVersions[key]
        val src = runtimeSources[key]
        val mismatch = bfVer != null && ver != "?" && !ver.startsWith(bfVer)
        val label = if (mismatch) "$name $ver ⚠" else "$name $ver"
        val tooltip = when {
            mismatch -> "Project requires $bfVer ($src), using $ver"
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

@Composable
private fun LspLifecycleItem(text: String, onClick: (() -> Unit)? = null) {
    val baseColor = MaterialTheme.colorScheme.onSurfaceVariant
    val color = if (onClick != null) MaterialTheme.colorScheme.primary else baseColor
    val mod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(verticalAlignment = Alignment.CenterVertically, modifier = mod) {
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
    val color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
            Text(text = label, modifier = mod, style = MaterialTheme.typography.labelSmall, color = color)
        }
    } else {
        Text(text = label, modifier = mod, style = MaterialTheme.typography.labelSmall, color = color)
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
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
private fun StatusItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

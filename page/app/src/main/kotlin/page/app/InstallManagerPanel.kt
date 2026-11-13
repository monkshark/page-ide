package page.app

import page.runtime.*
import page.workspace.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import page.ui.Glass
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import page.lsp.LanguageDefinition
import page.lsp.LanguageRegistry

enum class ManagerCategory(val label: String) {
    RUNTIME("Runtimes"),
    LSP("Language Servers"),
}

data class ManagerEntry(
    val id: String,
    val displayName: String,
    val category: ManagerCategory,
    val binaries: List<String> = emptyList(),
    val windowsBinaries: List<String> = emptyList(),
)

private fun ManagerEntry.pathBinaries(): List<String> {
    if (id == "flutter") return listOf("flutter", "flutter.bat", "dart", "dart.exe")
    val win = LspInstaller.isWindows()
    return if (category == ManagerCategory.RUNTIME) SystemInstallDetector.runtimeNames(id)
    else if (win) windowsBinaries.ifEmpty { binaries } else binaries
}

private fun ManagerEntry.systemPresent(): Boolean {
    val names = pathBinaries()
    return names.isNotEmpty() && SystemInstallDetector.findOnPath(names) != null
}

private fun ManagerEntry.systemDetail(): SystemInstall? =
    if (category == ManagerCategory.RUNTIME) SystemInstallDetector.forRuntime(id)
    else pathBinaries().takeIf { it.isNotEmpty() }?.let { SystemInstallDetector.forLsp(it) }

@Composable
internal fun InstallManagerPanel(
    initialSelection: String? = null,
    onClose: () -> Unit,
    onInstallRequested: (String) -> Unit,
    onVersionChanged: () -> Unit = {},
    onBeforeDelete: suspend (lspId: String) -> Unit = {},
    onAfterDelete: suspend (lspId: String) -> Unit = {},
    removalScope: kotlinx.coroutines.CoroutineScope? = null,
    modifier: Modifier = Modifier,
) {
    val entries = remember { buildManagerEntries() }
    val installRevision = InstallState.revision
    var selectedId by remember { mutableStateOf(initialSelection ?: entries.firstOrNull()?.id) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Row(modifier = modifier.fillMaxSize().background(Glass.colors.background)
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onClose(); true
            } else false
        }
    ) {
        ManagerSidebar(
            entries = entries,
            selectedId = selectedId,
            onSelect = { selectedId = it },
            onClose = onClose,
            modifier = Modifier.width(180.dp).fillMaxHeight(),
        )
        Box(
            modifier = Modifier.width(1.dp).fillMaxHeight()
                .background(Glass.colors.separator)
        )
        val entry = entries.firstOrNull { it.id == selectedId }
        if (entry != null) {
            ManagerDetailPane(
                entry = entry,
                onInstallRequested = onInstallRequested,
                onVersionChanged = onVersionChanged,
                onBeforeDelete = onBeforeDelete,
                onAfterDelete = onAfterDelete,
                removalScope = removalScope,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a tool", color = Glass.colors.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ManagerSidebar(
    entries: List<ManagerEntry>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val installRevision = InstallState.revision
    var systemPresent by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(entries, installRevision) {
        systemPresent = withContext(Dispatchers.IO) {
            entries.filter { it.systemPresent() }.map { it.id }.toSet()
        }
    }
    Column(modifier = modifier.background(Glass.colors.surfaceL2)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Install Manager",
                color = Glass.colors.text,
                style = LocalTextStyle.current.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 13.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
            Spacer(Modifier.weight(1f))
            page.app.ui.PanelCloseButton(onClose = onClose)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Glass.colors.separator))
        SidebarSearch(query = query, onChange = { query = it })
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.verticalScroll(scrollState).weight(1f).padding(vertical = 4.dp)) {
            for (category in ManagerCategory.entries) {
                val categoryEntries = entries.filter {
                    it.category == category &&
                        (query.isBlank() || it.displayName.contains(query, ignoreCase = true))
                }
                if (categoryEntries.isEmpty()) continue
                Text(
                    text = category.label,
                    color = Glass.colors.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                for (entry in categoryEntries) {
                    val isSelected = entry.id == selectedId
                    val installer = remember(entry.id) { LspInstallers.forId(entry.id) }
                    val installed = remember(entry.id, installRevision) { installer?.isInstalled() == true }
                    val accent = Glass.colors.primary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(Glass.radius.sm))
                            .background(if (isSelected) Glass.colors.primarySoft else Color.Transparent)
                            .drawBehind {
                                if (isSelected) {
                                    val h = size.height
                                    drawRoundRect(
                                        color = accent,
                                        topLeft = Offset(0f, h * 0.22f),
                                        size = Size(2.5.dp.toPx(), h * 0.56f),
                                        cornerRadius = CornerRadius(2f, 2f),
                                    )
                                }
                            }
                            .clickable { onSelect(entry.id) }
                            .padding(start = 9.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        page.app.ui.ToolIcon(entry.id, entry.displayName, 15.dp)
                        Text(
                            text = entry.displayName,
                            color = if (isSelected) Glass.colors.primary else Glass.colors.text,
                            style = LocalTextStyle.current.copy(
                                fontSize = 12.sp,
                                lineHeight = 12.sp,
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both,
                                ),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (installed) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Glass.colors.success))
                        } else if (entry.id in systemPresent) {
                            SystemBadge()
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ManagerDetailPane(
    entry: ManagerEntry,
    onInstallRequested: (String) -> Unit,
    onVersionChanged: () -> Unit = {},
    onBeforeDelete: suspend (lspId: String) -> Unit = {},
    onAfterDelete: suspend (lspId: String) -> Unit = {},
    removalScope: kotlinx.coroutines.CoroutineScope? = null,
    modifier: Modifier = Modifier,
) {
    val installer = remember(entry.id) { LspInstallers.forId(entry.id) }
    val installRevision = InstallState.revision
    var installedVersions by remember(entry.id, installRevision) {
        mutableStateOf(installer?.installedVersions() ?: emptyList())
    }
    var activeVersion by remember(entry.id, installRevision) { mutableStateOf(installer?.activeVersion()) }
    var availableVersions by remember(entry.id) { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember(entry.id) { mutableStateOf(true) }
    var confirmDeleteVersion by remember(entry.id) { mutableStateOf<String?>(null) }

    var sysDetail by remember(entry.id) { mutableStateOf<SystemInstall?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(entry.id) {
        loading = true
        val list = withContext(Dispatchers.IO) { installer?.availableVersions() ?: emptyList() }
        availableVersions = list
        loading = false
    }

    LaunchedEffect(entry.id) {
        sysDetail = null
        val fastPath = withContext(Dispatchers.IO) {
            entry.pathBinaries().takeIf { it.isNotEmpty() }?.let { SystemInstallDetector.findOnPath(it) }
        }
        if (fastPath != null) sysDetail = SystemInstall(fastPath, null)
        val full = withContext(Dispatchers.IO) { entry.systemDetail() }
        if (full != null) sysDetail = full
    }

    var deleteFailure by remember(entry.id) { mutableStateOf<Pair<String, String>?>(null) }

    fun refreshVersions() {
        installedVersions = installer?.installedVersions() ?: emptyList()
        activeVersion = installer?.activeVersion()
    }

    fun deleteVersion(version: String) {
        val inst = installer ?: return
        val wasActive = activeVersion == version
        deleteFailure = null
        InstallProgressRegistry.startRemoval(entry.id, version)
        val work = removalScope ?: scope
        work.launch(Dispatchers.IO) {
            if (wasActive) {
                InstallProgressRegistry.removalPhase(entry.id, version, "Stopping")
                val stop = work.launch(Dispatchers.IO) { runCatching { onBeforeDelete(entry.id) } }
                val startedAt = System.currentTimeMillis()
                while (stop.isActive) {
                    val waited = System.currentTimeMillis() - startedAt
                    if (waited >= SERVER_STOP_TIMEOUT_MS) break
                    InstallProgressRegistry.updateRemoval(
                        entry.id,
                        version,
                        stopFraction(waited, SERVER_STOP_TIMEOUT_MS),
                    )
                    delay(STOP_TICK_MS)
                }
            }
            InstallProgressRegistry.removalPhase(entry.id, version, "Removing")
            val failure = try {
                runCatching {
                    inst.uninstall(version) { removed, total ->
                        InstallProgressRegistry.updateRemoval(
                            entry.id,
                            version,
                            sweepFraction(removed, total, afterStop = wasActive),
                        )
                    }
                }.exceptionOrNull()
            } finally {
                if (wasActive) runCatching { onAfterDelete(entry.id) }
            }
            withContext(Dispatchers.Main) {
                InstallProgressRegistry.finishRemoval(entry.id, version)
                deleteFailure = failure?.let { version to (it.message ?: "Removal failed.") }
                refreshVersions()
                InstallState.changed()
                onVersionChanged()
            }
        }
    }

    val centeredStyle = LocalTextStyle.current.copy(
        fontSize = 12.sp,
        lineHeight = 12.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .widthIn(max = 560.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Glass.radius.sm))
                    .background(Glass.colors.surfaceL3),
                contentAlignment = Alignment.Center,
            ) { page.app.ui.ToolIcon(entry.id, entry.displayName, 19.dp) }
            Text(
                text = entry.displayName,
                color = Glass.colors.text,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusPill(installed = installedVersions.isNotEmpty(), version = activeVersion, onSystem = sysDetail != null)
        }
        toolDescription(entry)?.let { desc ->
            Spacer(Modifier.height(9.dp))
            Text(text = desc, color = Glass.colors.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }

        if (installedVersions.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Installed versions",
                color = Glass.colors.muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            val cardShape = RoundedCornerShape(Glass.radius.sm)
            Column(
                modifier = Modifier
                    .clip(cardShape)
                    .background(Glass.colors.surfaceL2)
                    .border(1.dp, Glass.colors.separator, cardShape),
            ) {
                installedVersions.forEachIndexed { idx, v ->
                    if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Glass.colors.separator))
                    val isCurrent = v == activeVersion
                    val removal = InstallProgressRegistry.removalOf(entry.id, v)
                    val isDeleting = removal != null
                    val deleteProgress = removal?.fraction ?: 0f
                    val isConfirming = confirmDeleteVersion == v && !isDeleting
                    val failedMessage = deleteFailure?.takeIf { it.first == v }?.second
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(if (isConfirming) Glass.colors.danger.copy(alpha = 0.08f) else Color.Transparent)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isDeleting) {
                            Text(
                                text = v,
                                color = Glass.colors.muted,
                                style = centeredStyle.copy(fontFamily = FontFamily.Monospace),
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(Glass.radius.xs))
                                    .background(Glass.colors.surfaceL1),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(deleteProgress.coerceIn(0f, 1f))
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(Glass.radius.xs))
                                        .background(Glass.colors.danger),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = removalLabel(removal?.phase, deleteProgress),
                                color = Glass.colors.muted,
                                style = centeredStyle.copy(fontSize = 11.sp),
                            )
                        } else if (failedMessage != null) {
                            Text(
                                text = v,
                                color = Glass.colors.muted,
                                style = centeredStyle.copy(fontFamily = FontFamily.Monospace),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = failedMessage,
                                color = Glass.colors.danger,
                                style = centeredStyle.copy(fontSize = 11.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Dismiss",
                                color = Glass.colors.muted,
                                style = centeredStyle.copy(fontSize = 11.sp),
                                modifier = Modifier.clickable { deleteFailure = null }.padding(horizontal = 6.dp),
                            )
                        } else if (isConfirming) {
                            Text(text = "Delete $v?", color = Glass.colors.danger, style = centeredStyle)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "Yes", color = Glass.colors.danger, style = centeredStyle,
                                modifier = Modifier.clickable { confirmDeleteVersion = null; deleteVersion(v) }.padding(horizontal = 8.dp),
                            )
                            Text(
                                text = "No", color = Glass.colors.muted, style = centeredStyle,
                                modifier = Modifier.clickable { confirmDeleteVersion = null }.padding(horizontal = 8.dp),
                            )
                        } else {
                            Text(
                                text = v,
                                color = if (isCurrent) Glass.colors.primary else Glass.colors.text,
                                style = centeredStyle.copy(fontFamily = FontFamily.Monospace),
                            )
                            if (isCurrent) {
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    text = "current",
                                    color = Glass.colors.primary,
                                    style = centeredStyle.copy(fontSize = 10.sp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Glass.radius.xs))
                                        .background(Glass.colors.primarySoft)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (!isCurrent) {
                                Text(
                                    text = "Apply",
                                    color = Glass.colors.primary,
                                    style = centeredStyle.copy(fontSize = 11.sp),
                                    modifier = Modifier.clickable {
                                        installer?.applyVersion(v)
                                        refreshVersions()
                                        InstallState.changed()
                                        onVersionChanged()
                                    }.padding(horizontal = 6.dp),
                                )
                            }
                            Box(
                                modifier = Modifier.height(24.dp).width(24.dp)
                                    .clip(RoundedCornerShape(Glass.radius.xs))
                                    .clickable { confirmDeleteVersion = v },
                                contentAlignment = Alignment.Center,
                            ) {
                                page.ui.CloseMark(tint = Glass.colors.danger.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }

        sysDetail?.let { info ->
            Spacer(Modifier.height(12.dp))
            SystemNote(info)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Add version",
            color = Glass.colors.muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        DetailPrimaryButton(label = "Install new version…") { onInstallRequested(entry.id) }
    }
}

@Composable
private fun StatusPill(installed: Boolean, version: String?, onSystem: Boolean) {
    val shape = RoundedCornerShape(50)
    val (text, color) = when {
        installed -> ("Installed" + (version?.let { " · $it" } ?: "")) to Glass.colors.success
        onSystem -> "On system" to Glass.colors.muted
        else -> return
    }
    Text(
        text = text,
        color = color,
        style = LocalTextStyle.current.copy(
            fontSize = 10.5.sp,
            lineHeight = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun DetailPrimaryButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(31.dp)
            .clip(RoundedCornerShape(Glass.radius.sm))
            .background(Glass.colors.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Glass.colors.onPrimary,
            style = LocalTextStyle.current.copy(
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

private fun toolDescription(entry: ManagerEntry): String? = when (entry.id) {
    "jdk" -> "Eclipse Temurin — prebuilt OpenJDK runtime. Runs Java and the JDT language server."
    "node" -> "Node.js runtime — runs JavaScript/TypeScript tooling and language servers."
    "python-runtime" -> "Python interpreter — runs Python and Python-based language servers."
    "go-sdk" -> "Go SDK — the Go compiler and toolchain."
    "rust-runtime" -> "Rust toolchain — the Rust compiler (rustc) and cargo."
    "cpp-toolchain" -> "LLVM/Clang — the Clang C/C++ compiler and tools."
    "mingw-toolchain" -> "MinGW-w64 (UCRT64) — GCC C/C++ compiler with bundled headers."
    "dotnet-runtime" -> ".NET SDK — the C# compiler and runtime."
    "windows-sdk" -> "Windows SDK (MSVC + xwin) — headers and libraries for native builds."
    else -> when (entry.category) {
        ManagerCategory.LSP -> "Language server for ${entry.displayName}."
        ManagerCategory.RUNTIME -> null
    }
}

@Composable
private fun SidebarSearch(query: String, onChange: (String) -> Unit) {
    val colors = Glass.colors
    val shape = RoundedCornerShape(Glass.radius.sm)
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(28.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outline, shape)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val w = this.size.width
            val r = w * 0.32f
            val c = Offset(w * 0.42f, w * 0.42f)
            val sw = w * 0.12f
            drawCircle(colors.faint, r, c, style = Stroke(sw))
            drawLine(colors.faint, Offset(c.x + r * 0.7f, c.y + r * 0.7f), Offset(w * 0.92f, w * 0.92f), sw, StrokeCap.Round)
        }
        Spacer(Modifier.width(7.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                textStyle = TextStyle(color = colors.text, fontSize = 11.5.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                Text("Search tools…", color = colors.faint, fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
private fun SystemBadge() {
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .border(1.5.dp, Glass.colors.muted, CircleShape),
    )
}

@Composable
private fun SystemNote(info: SystemInstall) {
    val tight = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(7.dp).clip(CircleShape).border(1.5.dp, Glass.colors.muted, CircleShape),
        )
        Text(
            text = "Also on system" + (info.version?.let { " · $it" } ?: ""),
            color = Glass.colors.muted,
            style = LocalTextStyle.current.copy(
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeightStyle = tight,
            ),
        )
        Text(
            text = info.path.toString(),
            color = Glass.colors.muted,
            style = LocalTextStyle.current.copy(
                fontSize = 10.5.sp,
                lineHeight = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                lineHeightStyle = tight,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val RUNTIME_IDS = listOf("jdk", "node", "python-runtime", "go-sdk", "cpp-toolchain", "mingw-toolchain", "rust-runtime", "dotnet-runtime", "windows-sdk")
private val RUNTIME_NAMES = mapOf(
    "jdk" to "Eclipse Temurin JDK",
    "node" to "Node.js",
    "python-runtime" to "Python",
    "go-sdk" to "Go SDK",
    "cpp-toolchain" to "LLVM/Clang",
    "mingw-toolchain" to "MinGW-w64 (UCRT64)",
    "rust-runtime" to "Rust Toolchain",
    "dotnet-runtime" to ".NET SDK",
    "windows-sdk" to "Windows SDK (MSVC, xwin)",
)

private fun buildManagerEntries(): List<ManagerEntry> {
    val entries = mutableListOf<ManagerEntry>()
    for (id in RUNTIME_IDS) {
        entries += ManagerEntry(
            id = id,
            displayName = RUNTIME_NAMES[id] ?: id,
            category = ManagerCategory.RUNTIME,
        )
    }
    val lspDefs = LanguageRegistry.all()
    for (def in lspDefs) {
        if (def.id in RUNTIME_IDS) continue
        entries += ManagerEntry(
            id = def.id,
            displayName = def.displayName,
            category = ManagerCategory.LSP,
            binaries = def.lspBinaries,
            windowsBinaries = def.lspWindowsBinaries,
        )
    }
    return entries
}

private const val SERVER_STOP_TIMEOUT_MS = 8_000L
private const val STOP_TICK_MS = 100L

internal const val STOP_SHARE = 0.1f

internal fun stopFraction(waitedMs: Long, timeoutMs: Long): Float {
    if (timeoutMs <= 0L) return STOP_SHARE
    return (STOP_SHARE * waitedMs / timeoutMs).coerceIn(0f, STOP_SHARE)
}

internal fun sweepFraction(removed: Int, total: Int, afterStop: Boolean): Float {
    val base = if (afterStop) STOP_SHARE else 0f
    val swept = if (total > 0) removed.toFloat() / total else 0f
    return (base + (1f - base) * swept.coerceIn(0f, 1f)).coerceIn(0f, 1f)
}

internal fun removalLabel(phase: String?, fraction: Float): String {
    val name = phase ?: "Removing"
    if (fraction <= 0f) return "$name…"
    return "$name… ${(fraction.coerceIn(0f, 1f) * 100).toInt()}%"
}

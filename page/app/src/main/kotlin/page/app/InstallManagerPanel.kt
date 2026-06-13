package page.app

import page.runtime.*
import page.workspace.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
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
)

@Composable
internal fun InstallManagerPanel(
    initialSelection: String? = null,
    onClose: () -> Unit,
    onInstallRequested: (String) -> Unit,
    onVersionChanged: () -> Unit = {},
    onBeforeDelete: suspend (lspId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val entries = remember { buildManagerEntries() }
    var selectedId by remember { mutableStateOf(initialSelection ?: entries.firstOrNull()?.id) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
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
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
        val entry = entries.firstOrNull { it.id == selectedId }
        if (entry != null) {
            ManagerDetailPane(
                entry = entry,
                onInstallRequested = onInstallRequested,
                onVersionChanged = onVersionChanged,
                onBeforeDelete = onBeforeDelete,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a tool", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 13.sp)
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
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Install Manager",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "×",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 14.sp,
                modifier = Modifier.clickable { onClose() }.padding(4.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.verticalScroll(scrollState).weight(1f).padding(vertical = 4.dp)) {
            for (category in ManagerCategory.entries) {
                val categoryEntries = entries.filter { it.category == category }
                if (categoryEntries.isEmpty()) continue
                Text(
                    text = category.label.uppercase(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                for (entry in categoryEntries) {
                    val isSelected = entry.id == selectedId
                    val bg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                    val installer = remember(entry.id) { LspInstallers.forId(entry.id) }
                    val installed = remember(entry.id) { installer?.isInstalled() == true }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(entry.id) }
                            .background(bg)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.displayName,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (installed) {
                            Text(text = "●", color = Glass.colors.success, fontSize = 8.sp)
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
    modifier: Modifier = Modifier,
) {
    val installer = remember(entry.id) { LspInstallers.forId(entry.id) }
    var installedVersions by remember(entry.id) { mutableStateOf(installer?.installedVersions() ?: emptyList()) }
    var activeVersion by remember(entry.id) { mutableStateOf(installer?.activeVersion()) }
    var availableVersions by remember(entry.id) { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember(entry.id) { mutableStateOf(true) }
    var confirmDeleteVersion by remember(entry.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(entry.id) {
        loading = true
        val list = withContext(Dispatchers.IO) { installer?.availableVersions() ?: emptyList() }
        availableVersions = list
        loading = false
    }

    fun refreshVersions() {
        installedVersions = installer?.installedVersions() ?: emptyList()
        activeVersion = installer?.activeVersion()
    }

    fun deleteVersion(version: String) {
        val inst = installer ?: return
        val dir = inst.installDir(version)
        val wasActive = activeVersion == version
        scope.launch(Dispatchers.IO) {
            runCatching { onBeforeDelete(entry.id) }
            if (wasActive) {
                val pointer = dir.parent?.resolve("CURRENT")
                if (pointer != null) runCatching { java.nio.file.Files.deleteIfExists(pointer) }
            }
            runCatching { ArchiveExtractors.deleteRecursively(dir) }
            withContext(Dispatchers.Main) {
                refreshVersions()
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

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = entry.displayName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))

        if (installedVersions.isNotEmpty()) {
            Text(
                text = "INSTALLED",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            val scroll = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scroll)) {
                for (v in installedVersions) {
                    val isCurrent = v == activeVersion
                    val isConfirming = confirmDeleteVersion == v
                    val bg = if (isConfirming) Glass.colors.danger.copy(alpha = 0.08f)
                    else if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(bg)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isConfirming) {
                            Text(text = "Delete $v?", color = Glass.colors.danger, style = centeredStyle)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "Yes", color = Glass.colors.danger, style = centeredStyle,
                                modifier = Modifier.clickable { confirmDeleteVersion = null; deleteVersion(v) }.padding(horizontal = 8.dp),
                            )
                            Text(
                                text = "No", color = MaterialTheme.colorScheme.onSurfaceVariant, style = centeredStyle,
                                modifier = Modifier.clickable { confirmDeleteVersion = null }.padding(horizontal = 8.dp),
                            )
                        } else {
                            Text(
                                text = v,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = centeredStyle.copy(fontFamily = FontFamily.Monospace),
                            )
                            if (isCurrent) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "current",
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    style = centeredStyle.copy(fontSize = 10.sp),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (!isCurrent) {
                                Text(
                                    text = "Apply",
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    style = centeredStyle.copy(fontSize = 11.sp),
                                    modifier = Modifier.clickable {
                                        installer?.applyVersion(v)
                                        refreshVersions()
                                        onVersionChanged()
                                    }.padding(horizontal = 6.dp),
                                )
                            }
                            Box(
                                modifier = Modifier.height(28.dp).width(28.dp)
                                    .clickable { confirmDeleteVersion = v },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "×",
                                    color = Glass.colors.danger.copy(alpha = 0.6f),
                                    style = centeredStyle.copy(fontSize = 14.sp, lineHeight = 14.sp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "INSTALL NEW VERSION",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier
                .clickable { onInstallRequested(entry.id) }
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ) {
            Text(
                text = "Open installer",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
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
        )
    }
    return entries
}

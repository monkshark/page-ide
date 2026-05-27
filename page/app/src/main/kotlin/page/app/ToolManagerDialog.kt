package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.lsp.LanguageDefinition
import page.lsp.LanguageRegistry
import page.ui.GlassTheme

data class ToolEntry(
    val id: String,
    val displayName: String,
    val category: ToolCategory,
    val definition: LanguageDefinition,
    val installed: Boolean,
)

enum class ToolCategory(val label: String) {
    LANGUAGE_SERVER("Language Servers"),
    RUNTIME("Runtimes"),
}

@Composable
internal fun ToolManagerDialog(
    initialSelection: String? = null,
    onDismiss: () -> Unit,
    onInstalled: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    val entries = remember { buildToolEntries() }
    var selectedId by remember { mutableStateOf(initialSelection ?: entries.firstOrNull()?.id) }
    var openInstallDialogFor by remember { mutableStateOf<ToolEntry?>(null) }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val activeEntry = openInstallDialogFor
    if (activeEntry != null) {
        InstallGuideDialog(
            definition = activeEntry.definition,
            attempted = emptyList(),
            onDismiss = { openInstallDialogFor = null },
            onInstalled = onInstalled,
            installer = LspInstallers.forId(activeEntry.id),
        )
        return
    }

    GlassTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss(); true
                    } else false
                }
                .clickable(interactionSource = scrimInteraction, indication = null) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(560.dp)
                    .height(460.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    .clickable(interactionSource = cardInteraction, indication = null) { },
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    Text(
                        text = "Tool Manager",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.weight(1f)) {
                        ToolSidebar(
                            entries = entries,
                            selectedId = selectedId,
                            onSelect = { selectedId = it },
                            modifier = Modifier.width(170.dp).fillMaxHeight(),
                        )
                        Box(
                            modifier = Modifier.width(1.dp).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        )
                        ToolDetailPane(
                            entry = entries.firstOrNull { it.id == selectedId },
                            onInstall = { entry -> openInstallDialogFor = entry },
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolSidebar(
    entries: List<ToolEntry>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier.verticalScroll(scrollState)) {
        for (category in ToolCategory.entries) {
            val categoryEntries = entries.filter { it.category == category }
            if (categoryEntries.isEmpty()) continue
            Text(
                text = category.label.uppercase(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            for (entry in categoryEntries) {
                val isSelected = entry.id == selectedId
                val bg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(entry.id) }
                        .background(bg)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.displayName,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (entry.installed) {
                        Text(text = "●", color = Color(0xFF2EA043), fontSize = 8.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ToolDetailPane(
    entry: ToolEntry?,
    onInstall: (ToolEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entry == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Select a tool", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 13.sp)
        }
        return
    }
    val installer = remember(entry.id) { LspInstallers.forId(entry.id) }
    val version = remember(entry.id) { installer?.activeVersion() ?: installer?.defaultVersion() }
    Column(modifier = modifier.padding(top = 4.dp)) {
        Text(
            text = entry.displayName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        if (entry.installed && version != null) {
            Text(
                text = "Installed: $version",
                color = Color(0xFF2EA043),
                fontSize = 12.sp,
            )
        } else {
            Text(
                text = "Not installed",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.category.label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(16.dp))
        val buttonLabel = if (entry.installed) "Manage" else "Install"
        Surface(
            modifier = Modifier
                .clickable { onInstall(entry) }
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        ) {
            Text(
                text = buttonLabel,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

private val RUNTIME_IDS = setOf("jdk", "node", "python-runtime", "go-sdk", "cpp-toolchain", "rust-runtime", "dotnet-runtime")

private fun buildToolEntries(): List<ToolEntry> {
    val entries = mutableListOf<ToolEntry>()
    val lspDefs = LanguageRegistry.all()
    for (def in lspDefs) {
        if (def.id in RUNTIME_IDS) continue
        val installer = runCatching { LspInstallers.forId(def.id) }.getOrNull()
        entries += ToolEntry(
            id = def.id,
            displayName = def.displayName,
            category = ToolCategory.LANGUAGE_SERVER,
            definition = def,
            installed = installer?.isInstalled() == true,
        )
    }
    val runtimeDefs = listOf(
        "jdk" to ("Eclipse Temurin JDK" to "https://adoptium.net/"),
        "node" to ("Node.js" to "https://nodejs.org/"),
        "python-runtime" to ("Python" to "https://python.org/"),
        "go-sdk" to ("Go SDK" to "https://go.dev/"),
        "cpp-toolchain" to ("LLVM/Clang Toolchain" to "https://llvm.org/"),
        "rust-runtime" to ("Rust Toolchain" to "https://rustup.rs/"),
        "dotnet-runtime" to (".NET SDK" to "https://dotnet.microsoft.com/download"),
    )
    for ((id, pair) in runtimeDefs) {
        val (name, url) = pair
        val installer = runCatching { LspInstallers.forId(id) }.getOrNull()
        entries += ToolEntry(
            id = id,
            displayName = name,
            category = ToolCategory.RUNTIME,
            definition = LanguageDefinition(id, name, emptyList(), emptyList(), emptyList(), url, emptyMap(), null),
            installed = installer?.isInstalled() == true,
        )
    }
    return entries
}

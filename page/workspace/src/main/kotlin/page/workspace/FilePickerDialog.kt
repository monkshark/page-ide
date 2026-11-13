package page.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import page.ui.GlassTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import page.ui.Glass
import java.nio.file.Path

data class PickRequest(
    val mode: PickMode,
    val startIn: Path? = null,
    val suggestedName: String = "",
    val recents: List<Path> = emptyList(),
    val onPick: (Path) -> Unit,
)

private fun TextStyle.centered(): TextStyle = copy(
    lineHeight = fontSize,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

@Composable
fun FilePickerDialog(
    request: PickRequest,
    palette: page.ui.GlassPalette,
    onDismiss: () -> Unit,
) {
    val mode = request.mode

    var current by remember(request) { mutableStateOf(FilePickerModel.startingDirectory(request.startIn)) }
    var selected by remember(request) { mutableStateOf<PickerEntry?>(null) }
    var query by remember(request) { mutableStateOf("") }
    var name by remember(request) { mutableStateOf(request.suggestedName) }
    var overwriting by remember(request) { mutableStateOf<Path?>(null) }
    var showHidden by remember(request) { mutableStateOf(false) }

    val listings = remember(request) { mutableStateMapOf<Path, PickerListing>() }
    val trail = remember(current) { FilePickerModel.trail(current) }
    val columns = remember(trail) { FilePickerModel.columns(trail) }

    LaunchedEffect(columns) {
        for (column in columns) {
            if (listings.containsKey(column)) continue
            val result = withContext(Dispatchers.IO) { FilePickerModel.list(column) }
            listings[column] = result
        }
    }
    LaunchedEffect(current) {
        selected = null
        query = ""
    }

    val listing = listings[current]
    val entries = (listing as? PickerListing.Ready)?.entries.orEmpty()
    val shown = remember(entries, query, mode, showHidden) {
        FilePickerModel.visible(entries, query, mode, showHidden)
    }
    val nameProblem = if (FilePickerModel.needsName(mode)) FilePickerModel.nameError(name) else null
    val confirmable = FilePickerModel.canConfirm(mode, current, selected, name) && listing is PickerListing.Ready

    fun confirm() {
        if (!confirmable) return
        val target = FilePickerModel.target(mode, current, selected, name) ?: return
        val clash = FilePickerModel.overwriteTarget(mode, current, name)
        if (clash != null && overwriting == null) {
            overwriting = clash
            return
        }
        onDismiss()
        request.onPick(target)
    }

    fun enter(entry: PickerEntry) {
        if (entry.isDirectory) current = entry.path else confirm()
    }

    fun goUp() {
        FilePickerModel.parentOf(current)?.let { current = it }
    }

    val focus = remember(request) { FocusRequester() }
    LaunchedEffect(request) { runCatching { focus.requestFocus() } }

    val state = rememberDialogState(
        position = WindowPosition.Aligned(Alignment.Center),
        width = 860.dp,
        height = 600.dp,
    )
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = FilePickerModel.title(mode),
        resizable = true,
        undecorated = true,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(window) { page.ui.WindowCorners.round(window) }
        GlassTheme(palette) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Glass.colors.surfaceL2)
                .border(1.dp, Glass.colors.outline)
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            if (overwriting != null) overwriting = null else onDismiss()
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            val pick = selected
                            if (pick != null && pick.isDirectory && !FilePickerModel.needsName(mode)) enter(pick)
                            else confirm()
                            true
                        }
                        Key.Backspace -> {
                            if (!FilePickerModel.needsName(mode)) { goUp(); true } else false
                        }
                        Key.DirectionDown -> {
                            val idx = shown.indexOf(selected)
                            selected = shown.getOrNull((idx + 1).coerceAtMost(shown.lastIndex))
                            true
                        }
                        Key.DirectionUp -> {
                            val idx = shown.indexOf(selected)
                            selected = if (idx <= 0) shown.firstOrNull() else shown[idx - 1]
                            true
                        }
                        else -> false
                    }
                },
        ) {
            WindowDraggableArea { PickerHeader(mode, onDismiss) }
            PickerCrumbs(
                current = current,
                query = query,
                showHidden = showHidden,
                hiddenCount = FilePickerModel.hiddenCount(entries),
                onNavigate = { current = it },
                onQuery = { query = it },
                onToggleHidden = { showHidden = !showHidden },
                onUp = ::goUp,
            )
            if (FilePickerModel.needsName(mode)) {
                PickerNameField(name, nameProblem) { name = it }
            }
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                PickerRail(current, request.recents) { current = it }
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Glass.colors.separator))
                columns.forEachIndexed { index, column ->
                    if (index > 0) {
                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Glass.colors.separator))
                    }
                    val isCurrent = column == current
                    val columnEntries = FilePickerModel.visible(
                        (listings[column] as? PickerListing.Ready)?.entries.orEmpty(),
                        "",
                        mode,
                        showHidden,
                    )
                    PickerColumn(
                        title = FilePickerModel.columnLabel(column),
                        listing = listings[column],
                        entries = if (isCurrent) shown else columnEntries,
                        openChild = FilePickerModel.childOnTrail(trail, column),
                        selected = if (isCurrent) selected else null,
                        mode = mode,
                        emptyNote = if (isCurrent && query.isNotBlank()) "No match for “$query”" else "Nothing here",
                        isCurrent = isCurrent,
                        onSelect = { entry -> current = column; selected = entry },
                        onEnter = { entry -> if (entry.isDirectory) current = entry.path else { current = column; selected = entry; confirm() } },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            PickerFooter(
                mode = mode,
                path = FilePickerModel.target(mode, current, selected, name)?.toString() ?: current.toString(),
                confirmable = confirmable,
                overwriting = overwriting,
                onCancel = onDismiss,
                onConfirm = ::confirm,
                onKeepEditing = { overwriting = null },
            )
        }
        }
    }
}

@Composable
private fun PickerHeader(mode: PickMode, onClose: () -> Unit) {
    val colors = Glass.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = FilePickerModel.title(mode),
            style = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold).centered(),
            color = colors.text,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Glass.radius.xs))
                .background(colors.primarySoft)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = FilePickerModel.modeTag(mode),
                style = LocalTextStyle.current.copy(fontSize = 11.sp).centered(),
                color = colors.primary,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight())
        PickerCloseButton(onClose)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Glass.colors.separator))
}

@Composable
private fun PickerCloseButton(onClose: () -> Unit) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .width(42.dp)
            .fillMaxHeight()
            .background(if (hovered) Color(0xFFE81123) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(11.dp)) {
            val glyph = if (hovered) Color.White else colors.muted
            drawLine(glyph, Offset(0f, 0f), Offset(size.width, size.height), 1.4f, StrokeCap.Round)
            drawLine(glyph, Offset(size.width, 0f), Offset(0f, size.height), 1.4f, StrokeCap.Round)
        }
    }
}

@Composable
private fun PickerCrumbs(
    current: Path,
    query: String,
    showHidden: Boolean,
    hiddenCount: Int,
    onNavigate: (Path) -> Unit,
    onQuery: (String) -> Unit,
    onToggleHidden: () -> Unit,
    onUp: () -> Unit,
) {
    val colors = Glass.colors
    val crumbs = remember(current) { FilePickerModel.crumbs(current) }
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(Glass.radius.xs))
                .clickable { onUp() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "↑",
                style = LocalTextStyle.current.copy(fontSize = 12.sp).centered(),
                color = colors.muted,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            crumbs.forEachIndexed { index, crumb ->
                if (index > 0) {
                    Text(text = "›", style = LocalTextStyle.current.copy(fontSize = 11.sp).centered(), color = colors.faint)
                }
                val here = index == crumbs.lastIndex
                Text(
                    text = crumb.label,
                    style = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace).centered(),
                    color = if (here) colors.text else colors.muted,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Glass.radius.xs))
                        .background(if (here) colors.surfaceL3 else Color.Transparent)
                        .clickable { onNavigate(crumb.path) }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        HiddenToggle(on = showHidden, count = hiddenCount, onClick = onToggleHidden)
        Spacer(Modifier.width(6.dp))
        PickerField(
            value = query,
            placeholder = "Filter by name",
            onValue = onQuery,
            width = 180.dp,
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Glass.colors.separator))
}

@Composable
private fun HiddenToggle(on: Boolean, count: Int, onClick: () -> Unit) {
    val colors = Glass.colors
    if (count == 0 && !on) return
    val tip = if (on) "Hide the $count config folders" else "Show $count hidden folders"
    page.ui.GlassTooltip(text = tip) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(Glass.radius.xs))
                .background(if (on) colors.primarySoft else Color.Transparent)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (on) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = null,
                tint = if (on) colors.primary else colors.faint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun PickerNameField(name: String, problem: String?, onName: (String) -> Unit) {
    val colors = Glass.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Name",
            style = LocalTextStyle.current.copy(fontSize = 11.sp).centered(),
            color = colors.muted,
            modifier = Modifier.width(42.dp),
        )
        PickerField(value = name, placeholder = "Untitled", onValue = onName, width = 260.dp, focused = true)
        if (problem != null) {
            Text(
                text = problem,
                style = LocalTextStyle.current.copy(fontSize = 11.sp).centered(),
                color = colors.danger,
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Glass.colors.separator))
}

@Composable
private fun PickerField(
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
    width: androidx.compose.ui.unit.Dp,
    focused: Boolean = false,
) {
    val colors = Glass.colors
    val shape = RoundedCornerShape(Glass.radius.xs)
    Box(
        modifier = Modifier
            .width(width)
            .height(24.dp)
            .clip(shape)
            .background(colors.surfaceL1)
            .border(1.dp, if (focused) colors.primary else colors.outline, shape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = LocalTextStyle.current.copy(fontSize = 11.sp).centered(),
                color = colors.faint,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 11.sp,
                color = colors.text,
                fontFamily = FontFamily.Monospace,
            ).centered(),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PickerRail(current: Path, recents: List<Path>, onNavigate: (Path) -> Unit) {
    val roots = remember { FilePickerModel.roots() }
    val home = remember { FilePickerModel.homeDirectory() }
    Column(
        modifier = Modifier.width(150.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
    ) {
        if (recents.isNotEmpty()) {
            RailLabel("Recent")
            recents.take(5).forEach { project ->
                RailItem(project.fileName?.toString() ?: project.toString(), project, current, onNavigate)
            }
            Spacer(Modifier.height(10.dp))
        }
        RailLabel("Pinned")
        RailItem("Home", home, current, onNavigate)
        RailItem("Desktop", home.resolve("Desktop"), current, onNavigate)
        RailItem("Documents", home.resolve("Documents"), current, onNavigate)
        Spacer(Modifier.height(10.dp))
        RailLabel("Drives")
        roots.forEach { root ->
            RailItem(root.toString().trimEnd('\\', '/'), root, current, onNavigate, drive = true)
        }
    }
}

@Composable
private fun RailLabel(text: String) {
    Text(
        text = text,
        style = LocalTextStyle.current.copy(fontSize = 12.sp).centered(),
        color = Glass.colors.faint,
        modifier = Modifier.padding(start = 35.dp, end = 12.dp, bottom = 5.dp),
    )
}

@Composable
private fun RailItem(
    label: String,
    path: Path,
    current: Path,
    onNavigate: (Path) -> Unit,
    drive: Boolean = false,
) {
    val colors = Glass.colors
    val on = runCatching { path.toAbsolutePath().normalize() == current }.getOrDefault(false)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(if (on) colors.primarySoft else Color.Transparent)
            .clickable { onNavigate(path) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (drive) DriveGlyph() else FileTypeIcon(path = path, isDirectory = true, size = 15.dp)
        Text(
            text = label,
            style = LocalTextStyle.current.copy(fontSize = 12.sp).centered(),
            color = if (on) colors.text else colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DriveGlyph() {
    val colors = Glass.colors
    Column(
        modifier = Modifier.size(15.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(colors.faint),
            )
        }
    }
}

@Composable
private fun PickerColumn(
    title: String,
    listing: PickerListing?,
    entries: List<PickerEntry>,
    openChild: Path?,
    selected: PickerEntry?,
    mode: PickMode,
    emptyNote: String,
    isCurrent: Boolean,
    onSelect: (PickerEntry) -> Unit,
    onEnter: (PickerEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Glass.colors
    Column(modifier = modifier.background(if (isCurrent) colors.surfaceL2 else colors.surfaceL1)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(24.dp).padding(start = 35.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = LocalTextStyle.current.copy(fontSize = 12.sp).centered(),
                color = colors.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Box(modifier = Modifier.weight(1f))
            if (listing is PickerListing.Ready) {
                Text(
                    text = entries.size.toString(),
                    style = LocalTextStyle.current
                        .copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace).centered(),
                    color = colors.faint,
                )
            }
        }
        when {
            listing == null -> PickerMessage("Reading…")
            listing is PickerListing.Denied -> PickerMessage("Cannot open", listing.reason)
            entries.isEmpty() -> PickerMessage(emptyNote)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
                state = rememberLazyListState(),
            ) {
                itemsIndexed(entries, key = { _, e -> e.path.toString() }) { _, entry ->
                    val dimmed = FilePickerModel.picksDirectories(mode) && !entry.isDirectory
                    val onTrail = entry.path == openChild
                    val highlighted = entry == selected || onTrail
                    val interaction = remember(entry.path) { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()
                    val rowShape = RoundedCornerShape(Glass.radius.xs)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .clip(rowShape)
                            .background(
                                when {
                                    highlighted -> colors.primarySoft
                                    hovered && !dimmed -> colors.surfaceL3
                                    else -> Color.Transparent
                                },
                            )
                            .then(if (onTrail) Modifier.drawBehind {
                                drawRect(colors.primary, size = Size(2f * density, size.height))
                            } else Modifier)
                            .hoverable(interaction)
                            .then(if (dimmed) Modifier else Modifier.clickable { onSelect(entry); onEnter(entry) })
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FileTypeIcon(path = entry.path, isDirectory = entry.isDirectory, size = 15.dp)
                        Text(
                            text = entry.name,
                            style = LocalTextStyle.current.copy(fontSize = 12.sp).centered(),
                            color = if (dimmed) colors.faint else colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier.width(46.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            if (entry.isDirectory) {
                                Text(
                                    text = "›",
                                    style = LocalTextStyle.current.copy(fontSize = 11.sp).centered(),
                                    color = if (onTrail) colors.primary else colors.faint,
                                )
                            } else {
                                Text(
                                    text = FilePickerModel.formatSize(entry.sizeBytes),
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                    ).centered(),
                                    color = colors.faint,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerMessage(title: String, detail: String? = null) {
    val colors = Glass.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = LocalTextStyle.current.copy(fontSize = 12.sp).centered(),
            color = colors.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = detail,
                style = LocalTextStyle.current.copy(fontSize = 11.sp).centered(),
                color = colors.faint,
            )
        }
    }
}

@Composable
private fun PickerFooter(
    mode: PickMode,
    path: String,
    confirmable: Boolean,
    overwriting: Path?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    val colors = Glass.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (overwriting != null) "Already exists" else "Selected",
                style = LocalTextStyle.current.copy(fontSize = 12.sp).centered(),
                color = if (overwriting != null) colors.warn else colors.faint,
            )
            Text(
                text = overwriting?.toString() ?: path,
                style = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace).centered(),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (overwriting != null) {
            PickerButton("Keep editing", primary = false, enabled = true, onClick = onKeepEditing)
            PickerButton("Replace", primary = true, enabled = true, onClick = onConfirm)
        } else {
            PickerButton("Cancel", primary = false, enabled = true, onClick = onCancel)
            PickerButton(FilePickerModel.confirmLabel(mode), primary = true, enabled = confirmable, onClick = onConfirm)
        }
    }
}

@Composable
private fun PickerButton(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = Glass.colors
    val shape = RoundedCornerShape(Glass.radius.md)
    val background = when {
        !enabled -> colors.surfaceL3
        primary -> colors.primary
        else -> colors.surfaceL3
    }
    val foreground = when {
        !enabled -> colors.faint
        primary -> colors.onPrimary
        else -> colors.text
    }
    Box(
        modifier = Modifier
            .width(124.dp)
            .height(30.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, if (primary && enabled) colors.primary else colors.outline, shape)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LocalTextStyle.current.copy(
                fontSize = 12.sp,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
            ).centered(),
            color = foreground,
        )
    }
}

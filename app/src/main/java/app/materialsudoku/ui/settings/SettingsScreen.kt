package app.materialsudoku.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.MaterialShapes
import app.materialsudoku.AppModel
import app.materialsudoku.BuildConfig
import app.materialsudoku.data.Haptics
import app.materialsudoku.data.Input
import app.materialsudoku.data.Motion
import app.materialsudoku.data.Settings
import app.materialsudoku.game.BAND_NAMES
import app.materialsudoku.game.Geo
import app.materialsudoku.ui.home.DIFFICULTY_TECH
import app.materialsudoku.ui.kit.LocalBuzz
import app.materialsudoku.ui.kit.LocalSprings
import app.materialsudoku.ui.kit.Morphs
import app.materialsudoku.ui.kit.ShapeGlyph
import app.materialsudoku.ui.kit.Shp
import app.materialsudoku.ui.kit.SquishIcon
import app.materialsudoku.ui.kit.Sym
import app.materialsudoku.ui.kit.drawShape
import app.materialsudoku.ui.kit.ts
import app.materialsudoku.ui.play.DIFFICULTY_NAMES
import app.materialsudoku.ui.theme.Seed
import app.materialsudoku.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Settings, variant A of board 11 (chosen 2026-10-27): one grouped page in game order. Every row
 * says what it does in plain words, with an example where one helps (the user found the first
 * wording unreadable). Pick-one choices are Home's tiles; on/off is a switch.
 */
@Composable
fun SettingsScreen(model: AppModel, s: Settings) {
    val cs = MaterialTheme.colorScheme
    val buzz = LocalBuzz.current
    val scroll = rememberScrollState()
    val set: ((Settings) -> Settings) -> Unit = { f -> model.updateSettings(f) }
    val inGame = model.current
    val collapsed = scroll.value > 120

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Surface(color = if (collapsed) cs.surfaceContainer else cs.surface) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SquishIcon("arrow_back", "Back", { model.screen = model.settingsFrom })
                Spacer(Modifier.width(8.dp))
                Text("Settings", Modifier.graphicsLayer { alpha = if (collapsed) 1f else 0f }, style = ts(22f, 600, cs.onSurface))
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 48.dp)) {
            Text("Settings", Modifier.padding(start = 20.dp, top = 4.dp, bottom = 12.dp), style = ts(45f, 500, cs.onSurface))

            Section("Difficulties on Home")
            Pad { DifficultyChecks(s) { k -> set { it.copy(difficulties = it.difficulties xor (1 shl k)) } } }
            Pad(top = 4.dp) { ImpossibleTile(s.impossible) { on -> set { it.copy(impossible = on) } } }
            Info("info", "Tap one to show or hide it on Home. Hold one to see the techniques it needs.")

            Section("Mistakes")
            Pad {
                TileRow(
                    listOf(
                        Choice("Flag them", "A wrong number turns red at once and costs a life", heartsIcon = true),
                        Choice("Don’t flag them", "Wrong numbers stay until you spot them. No lives", icon = "shield"),
                    ),
                    if (s.flagMistakes) 0 else 1, height = 112.dp,
                ) { k -> set { it.copy(flagMistakes = k == 0) } }
            }
            Pad(top = 4.dp) {
                Box(Modifier.graphicsLayer { alpha = if (s.flagMistakes) 1f else 0.38f }) {
                    TileRow(listOf(Choice("3 lives"), Choice("1 life")), if (s.lives == 1) 1 else 0, height = 60.dp, enabled = s.flagMistakes) { k ->
                        set { it.copy(lives = if (k == 0) 3 else 1) }
                    }
                }
            }
            Info(
                "lock",
                (if (s.flagMistakes) "Lose your last life and the game is over. " else "With mistakes unflagged, a wrong number can make the puzzle unsolvable. ") +
                    if (inGame != null) "The game you’re in the middle of keeps its old rules." else "",
            )

            Section("Help while you play")
            BandCard(s)
            Spacer(Modifier.height(8.dp))
            HighlightCard(s.highlight) { v -> set { it.copy(highlight = v) } }
            Spacer(Modifier.height(8.dp))
            Group {
                SwitchRow("apps", "Fill-in-notes button", "Adds a button that writes every possible number into every empty cell. Using it counts like a hint", s.fillNotesButton) { v -> set { it.copy(fillNotesButton = v) } }
                SwitchRow("ink_eraser", "Tidy notes automatically", "Place a 5 and any 5s in your notes in the same row, column and box are erased", s.autoRemoveNotes) { v -> set { it.copy(autoRemoveNotes = v) } }
                GroupRow {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Lead("done_all"); Spacer(Modifier.width(14.dp))
                            Column { Text("Finish the easy end", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface); Text("Once every empty cell has only one number that fits, fill them all in", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                        }
                        Segmented(listOf("Show a button", "Do it for me"), if (s.autoCompleteAuto) 1 else 0) { k -> set { it.copy(autoCompleteAuto = k == 1) } }
                    }
                }
                SwitchRow("lightbulb", "Hints", "A hint button in Play. Each tap tells you more: where to look, the technique, the cell, then the answer", s.hints) { v -> set { it.copy(hints = v) } }
                SwitchRow("flag", "Point out an easy cell", "Always marks one cell you can solve right now. Counts as a hint", s.easyCellMarker) { v -> set { it.copy(easyCellMarker = v) } }
            }

            Section("Entering numbers")
            Pad {
                TileRow(
                    listOf(
                        Choice("Cell first", "Tap a cell, then a number"),
                        Choice("Number first", "Tap a number, then every cell it goes in"),
                        Choice("Notes first", "Tap a number to note it, hold to place it"),
                    ),
                    s.input.ordinal, height = 118.dp,
                ) { k -> set { it.copy(input = Input.entries[k]) } }
            }
            Spacer(Modifier.height(8.dp))
            Group {
                SwitchRow("timer", "Show the timer", "You can also tap it during a game to hide it. Your time is recorded either way", s.showTimer) { v -> set { it.copy(showTimer = v) } }
                SwitchRow("flag", "Halfway message", "Once a game, when half the empty cells are filled: your pace if you're ahead, otherwise a word of encouragement", s.halfwayMessage) { v -> set { it.copy(halfwayMessage = v) } }
            }

            Section("Look")
            Label("Theme")
            Pad { Segmented(listOf("Like phone", "Light", "Dark"), s.theme.ordinal, icons = listOf("brightness_auto", "light_mode", "dark_mode")) { k -> set { it.copy(theme = ThemeMode.entries[k]) } } }
            Spacer(Modifier.height(8.dp))
            Group { SwitchRow("contrast", "Pure black", "In dark theme, black instead of dark grey", s.amoled) { v -> set { it.copy(amoled = v) } } }
            Label("Colour")
            Swatches(s.seed) { seed -> buzz.toggle(); set { it.copy(seed = seed) } }
            Label("Numbers on the board")
            Pad { TileRow(listOf(Choice("1 2 3", "Western Arabic"), Choice("\u200E١ ٢ ٣", "Eastern Arabic")), if (s.arabicNumerals) 1 else 0, height = 76.dp) { k -> set { it.copy(arabicNumerals = k == 1) } } }
            Spacer(Modifier.height(8.dp))
            Group {
                SwitchRow("animation", "Reduce motion", "Simple fades instead of bounces and morphs. Matches your phone until you change it", s.motion == Motion.REDUCED) { v ->
                    set { it.copy(motion = if (v) Motion.REDUCED else Motion.FULL) }
                }
            }

            Label("App name on your phone")
            Pad { Segmented(listOf("Sudoku", "数独"), if (s.kanjiName) 1 else 0) { k -> set { it.copy(kanjiName = k == 1) } } }
            Info("info", "The name under the icon. Switching it can take the icon off your home screen; it stays in All apps.")

            Section("Vibration", "Tap one to try it")
            Pad {
                TileRow(
                    listOf(Choice("Off", "No vibration", icon = "vibration"), Choice("Some", "Only mistakes and wins", icon = "vibration"), Choice("All", "Every tap", icon = "vibration")),
                    s.haptics.ordinal, height = 112.dp,
                ) { k -> buzz.sample(Haptics.entries[k]); set { it.copy(haptics = Haptics.entries[k]) } }
            }

            Section("Your data")
            ExportRows(model)

            Section("About")
            Group {
                GroupRow { RowBody("info", "Material Sudoku ${BuildConfig.VERSION_NAME}", "Puzzle maker v1 · difficulty rater v1") {} }
                GroupRow { RowBody("license", "Open-source licences", "Google Sans Flex and Noto Sans JP (OFL 1.1), Material Symbols (Apache 2.0)", chevron = false) {} }
            }
        }
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun Section(t: String, aside: String? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(t, Modifier.weight(1f), style = ts(14f, 650, cs.primary))
        if (aside != null) Text(aside, style = ts(12.5f, 500, cs.onSurfaceVariant))
    }
}

@Composable
private fun Label(t: String) = Text(t, Modifier.padding(start = 22.dp, top = 14.dp, bottom = 6.dp), style = ts(12f, 560, MaterialTheme.colorScheme.onSurfaceVariant))

@Composable
private fun Pad(top: Dp = 0.dp, content: @Composable () -> Unit) = Box(Modifier.padding(start = 16.dp, end = 16.dp, top = top)) { content() }

@Composable
private fun Info(icon: String, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(Sym[icon], null, Modifier.size(16.dp).padding(top = 1.dp), tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = ts(12.5f, 450, cs.onSurfaceVariant, lineHeight = 17f))
    }
}

/** M3E grouped list: 24 dp outer corners, 4 dp inner, 2 dp gaps. */
@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

@Composable
private fun GroupRow(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun Lead(icon: String) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        ShapeGlyph(MaterialShapes.Cookie4Sided, 40.dp, cs.secondaryContainer)
        Icon(Sym[icon], null, Modifier.size(22.dp), tint = cs.onSecondaryContainer)
    }
}

@Composable
private fun RowBody(icon: String, title: String, sub: String?, chevron: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Lead(icon); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        if (chevron) Icon(Sym["chevron_right"], null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(icon: String, title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val buzz = LocalBuzz.current
    GroupRow {
        Box(Modifier.semantics { role = Role.Switch; stateDescription = if (on) "On" else "Off" }) {
            RowBody(icon, title, sub, trailing = {
                Switch(
                    checked = on, onCheckedChange = { buzz.toggle(); onChange(it) },
                    thumbContent = if (on) ({ Icon(Sym["check"], null, Modifier.size(SwitchDefaults.IconSize)) }) else null,
                )
            }) { buzz.toggle(); onChange(!on) }
        }
    }
}

private data class Choice(val name: String, val sub: String? = null, val icon: String? = null, val heartsIcon: Boolean = false)

/** Home's tiles: connected corners at rest, springing to full corners and secondary when chosen. */
@Composable
private fun TileRow(items: List<Choice>, sel: Int, height: Dp, enabled: Boolean = true, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val buzz = LocalBuzz.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { k, c ->
            val on = k == sel
            val t by animateFloatAsState(if (on) 1f else 0f, springs.fast(), label = "t")
            val col by animateFloatAsState(if (on) 1f else 0f, springs.effects(), label = "c")
            val outer = 28f; val inner = 8f
            val first = k == 0; val last = k == items.lastIndex
            fun r(edge: Boolean) = ((if (edge) outer else inner) + ((height.value / 2).coerceAtMost(36f) - (if (edge) outer else inner)) * t).dp
            val fg = lerp(cs.onSurface, cs.onSecondary, col)
            Surface(
                color = lerp(cs.surfaceContainerHighest, cs.secondary, col), contentColor = fg,
                shape = RoundedCornerShape(r(first), r(last), r(last), r(first)),
                modifier = Modifier.weight(1f).height(height)
                    .semantics { role = Role.RadioButton; selected = on; contentDescription = c.name }
                    .clickable(enabled = enabled) { buzz.toggle(); onPick(k) },
            ) {
                Column(Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (c.heartsIcon) app.materialsudoku.ui.kit.Hearts(3, 3, 22.dp, color = fg)
                    else if (c.icon != null) Icon(Sym[c.icon], null, Modifier.size(26.dp))
                    if (c.heartsIcon || c.icon != null) Spacer(Modifier.height(6.dp))
                    Text(c.name, style = ts(16f, if (on) 720 else 580), maxLines = 1)
                    if (c.sub != null) Text(c.sub, style = ts(12.5f, 440, if (on) fg.copy(alpha = 0.8f) else cs.onSurfaceVariant, lineHeight = 16f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

/** ButtonGroup of toggleableItem()s, connected [M3E-NEW]; Role.RadioButton by hand. */
@Composable
private fun Segmented(labels: List<String>, sel: Int, icons: List<String>? = null, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val buzz = LocalBuzz.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { k, l ->
            val on = k == sel
            val first = k == 0; val last = k == labels.lastIndex
            val o = if (on) 24.dp else 24.dp; val i = if (on) 24.dp else 6.dp
            Surface(
                color = if (on) cs.secondary else cs.surfaceContainerHighest, contentColor = if (on) cs.onSecondary else cs.onSurface,
                shape = RoundedCornerShape(if (first) o else i, if (last) o else i, if (last) o else i, if (first) o else i),
                modifier = Modifier.weight(1f).height(48.dp).semantics { role = Role.RadioButton; selected = on }.clickable { buzz.toggle(); onPick(k) },
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (icons != null) { Icon(Sym[icons[k]], null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                    Text(l, style = ts(14.5f, if (on) 720 else 560), maxLines = 1)
                }
            }
        }
    }
}

/** Six difficulty tiles, multi-select with a check badge; at least one stays on (the last refuses and shakes). */
@Composable
private fun DifficultyChecks(s: Settings, onToggle: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val buzz = LocalBuzz.current
    val scope = rememberCoroutineScope()
    val shakes = remember { List(6) { Animatable(0f) } }
    var tip by remember { mutableStateOf(-1) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in 0..2) {
                val k = r * 3 + c
                val on = s.difficultyOn(k)
                val count = (0..5).count { s.difficultyOn(it) }
                Surface(
                    color = if (on) cs.secondaryContainer else Color.Transparent, contentColor = if (on) cs.onSecondaryContainer else cs.onSurface,
                    shape = RoundedCornerShape(if (on) 34.dp else 8.dp).let { if (!on) RoundedCornerShape(if (k == 0) 28.dp else 8.dp, if (k == 2) 28.dp else 8.dp, if (k == 5) 28.dp else 8.dp, if (k == 3) 28.dp else 8.dp) else it },
                    border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
                    modifier = Modifier.weight(1f).height(88.dp)
                        .graphicsLayer { translationX = shakes[k].value }
                        .semantics { role = Role.Checkbox; stateDescription = if (on) "Shown on Home" else "Hidden" }
                        .pointerInput(on, count) {
                            detectTapGestures(
                                onTap = {
                                    if (on && count == 1) {
                                        buzz.lowTick(0.8f)
                                        scope.launch { for (x in listOf(-8f, 7f, -5f, 3f, 0f)) shakes[k].animateTo(x, tween(45, easing = LinearEasing)) }
                                    } else { buzz.difficulty(k); onToggle(k) }
                                },
                                onLongPress = { tip = if (tip == k) -1 else k },
                            )
                        },
                ) {
                    Box {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            ShapeGlyph(Shp.difficulty[k], 34.dp, if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(DIFFICULTY_NAMES[k], style = ts(16f, if (on) 700 else 560))
                        }
                        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp).drawBehind {
                            if (on) drawCircle(cs.onSecondaryContainer) else drawCircle(cs.outline, style = Stroke(1.5.dp.toPx()))
                        }, contentAlignment = Alignment.Center) {
                            if (on) Icon(Sym["check"], null, Modifier.size(14.dp), tint = cs.secondaryContainer)
                        }
                    }
                }
            }
        }
        if (tip >= 0) Text("${DIFFICULTY_NAMES[tip]}: ${DIFFICULTY_TECH[tip]}", Modifier.padding(start = 6.dp, top = 6.dp), style = ts(12.5f, 500, cs.onSurfaceVariant, lineHeight = 17f))
    }
}

/** Impossible is guarded by a hold, not a dialog (Home's no-dialogs rule). One tap turns it off again. */
@Composable
private fun ImpossibleTile(on: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val buzz = LocalBuzz.current
    val scope = rememberCoroutineScope()
    val ring = remember { Animatable(0f) }
    Surface(
        color = if (on) cs.errorContainer else Color.Transparent, contentColor = if (on) cs.onErrorContainer else cs.onSurface,
        shape = RoundedCornerShape(28.dp), border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(72.dp).semantics { role = Role.Switch; stateDescription = if (on) "On" else "Off. Hold to unlock" },
    ) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeGlyph(Shp.difficulty[6], 34.dp, if (on) cs.onErrorContainer else cs.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Impossible", style = ts(16f, 650))
                Text(if (on) "On, after Extreme on Home. Tap the lock to turn it off." else "The hardest known puzzles. Hints can’t help. Hold the lock to unlock.", style = ts(12.5f, 450, lineHeight = 16f), maxLines = 2)
            }
            Box(
                Modifier.size(56.dp).pointerInput(on) {
                    detectTapGestures(
                        onTap = { if (on) { buzz.toggle(); onChange(false) } },
                        onPress = {
                            if (on) return@detectTapGestures
                            val job = scope.launch {
                                ring.snapTo(0f); ring.animateTo(1f, tween(1200, easing = LinearEasing))
                                buzz.heartCrack(); onChange(true)
                            }
                            tryAwaitRelease()
                            if (ring.value < 1f) { job.cancel(); scope.launch { ring.animateTo(0f, tween(200)) } }
                        },
                    )
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(if (on) cs.onErrorContainer.copy(alpha = 0.12f) else cs.surfaceContainerHigh)
                    if (ring.value > 0f && !on) drawArc(cs.error, -90f, 360f * ring.value, false, style = Stroke(3.dp.toPx()))
                }
                Icon(Sym[if (on) "lock_open" else "lock"], null, Modifier.size(22.dp))
            }
        }
    }
}

/** Which records the current mix counts for, and why, in words. */
@Composable
private fun BandCard(s: Settings) {
    val cs = MaterialTheme.colorScheme
    val b = s.band
    val why = if (b == 1) "Blocked highlighting does part of the scanning" else "nothing here does the thinking for you"
    Surface(color = cs.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym["emoji_events"], null, Modifier.size(18.dp), tint = cs.onSurface)
                Spacer(Modifier.width(8.dp))
                Text("Which records this counts for", style = ts(14f, 600, cs.onSurface))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val shapes = listOf(MaterialShapes.Square, MaterialShapes.Cookie4Sided)
                for (k in 0..1) {
                    val on = k == b
                    Surface(
                        color = if (on) cs.secondary else cs.surfaceContainerHighest, contentColor = if (on) cs.onSecondary else cs.onSurfaceVariant,
                        shape = RoundedCornerShape(if (on || k == 0) 22.dp else 6.dp, if (on || k == 1) 22.dp else 6.dp, if (on || k == 1) 22.dp else 6.dp, if (on || k == 0) 22.dp else 6.dp),
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            ShapeGlyph(shapes[k], 14.dp, if (on) cs.onSecondary else cs.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(BAND_NAMES[k], style = ts(13.5f, if (on) 760 else 560), maxLines = 1)
                        }
                    }
                }
            }
            Text(
                "${BAND_NAMES[b]}, because $why. Best times are kept apart for each, so a game with more help never beats one with less. A game where you use a hint or fill in all notes goes to its own assisted records.",
                Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp), style = ts(12.5f, 450, cs.onSurfaceVariant, lineHeight = 17f),
            )
        }
    }
}

private val HL_NAMES = listOf("Off", "Digit", "Lines", "Notes", "Blocked")
private val HL_CAPTIONS = listOf(
    "Nothing lights up but the cell you selected.",
    "Every copy of the selected number lights up.",
    "Also the row, column and box of the selected cell.",
    "Also that number wherever it is in your notes.",
    "Also every empty cell where that number can’t go.",
)

/** Highlighting, shown on a small board that re-lights as the slider moves. */
@Composable
private fun HighlightCard(level: Int, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val buzz = LocalBuzz.current
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth()) {
                Text("Highlighting", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                Text("What lights up when you select a cell", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            SampleBoard(level, 20.dp)
            Spacer(Modifier.height(14.dp))
            StopSlider(level, 5) { if (it != level) { buzz.lowTick(0.5f + 0.1f * it); onChange(it) } }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                HL_NAMES.forEachIndexed { k, n ->
                    Text(n, Modifier.weight(1f), style = ts(12f, if (k == level) 720 else 500, if (k == level) cs.onSurface else cs.onSurfaceVariant), textAlign = when (k) { 0 -> androidx.compose.ui.text.style.TextAlign.Start; 4 -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Center })
                }
            }
            Text(HL_CAPTIONS[level], Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

/** Slider(steps) [M3E-RESTYLED]: 16 dp track, a bar handle, stop indicators. Tap or drag. */
@Composable
private fun StopSlider(value: Int, n: Int, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val pos by animateFloatAsState(value.toFloat(), springs.fast(), label = "pos")
    Canvas(
        Modifier.fillMaxWidth().height(44.dp)
            .pointerInput(n) {
                fun at(x: Float) = ((x - 8.dp.toPx()) / (size.width - 16.dp.toPx()) * (n - 1)).let { kotlin.math.round(it).toInt().coerceIn(0, n - 1) }
                detectTapGestures { onChange(at(it.x)) }
            }
            .pointerInput(n) {
                fun at(x: Float) = ((x - 8.dp.toPx()) / (size.width - 16.dp.toPx()) * (n - 1)).let { kotlin.math.round(it).toInt().coerceIn(0, n - 1) }
                detectDragGestures { ch, _ -> onChange(at(ch.position.x)) }
            },
    ) {
        val h = 16.dp.toPx(); val cy = size.height / 2; val pad = 8.dp.toPx()
        val x = pad + pos / (n - 1) * (size.width - 2 * pad)
        val gap = 6.dp.toPx()
        if (x - gap > 0) drawRoundRect(cs.primary, Offset(0f, cy - h / 2), Size(x - gap, h), CornerRadius(h / 2))
        if (x + gap < size.width) drawRoundRect(cs.secondaryContainer, Offset(x + gap, cy - h / 2), Size(size.width - x - gap, h), CornerRadius(h / 2))
        for (k in 0 until n) {
            val sx = pad + k.toFloat() / (n - 1) * (size.width - 2 * pad)
            if (kotlin.math.abs(sx - x) > gap) drawCircle(if (sx < x) cs.onPrimary else cs.onSecondaryContainer, 2.dp.toPx(), Offset(sx, cy))
        }
        drawRoundRect(cs.primary, Offset(x - 2.dp.toPx(), cy - 22.dp.toPx()), Size(4.dp.toPx(), 44.dp.toPx()), CornerRadius(2.dp.toPx()))
    }
}

/** A fixed sample position with r5c5 selected and 3 highlighted, drawn at preview scale. */
private const val SAMPLE = "000390002500040091000000470000400030000032000059060700000000003640007800100000900"
private const val SAMPLE_SOL = "471398652582746391936215478218479536764532189359861724897154263643927815125683947"
private val SAMPLE_PLACED = intArrayOf(1, 2, 7, 11, 15, 24, 31, 37, 42, 44, 48)

@Composable
private fun SampleBoard(level: Int, cell: Dp) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val values = remember { IntArray(81) { SAMPLE[it] - '0' }.also { v -> SAMPLE_PLACED.forEach { v[it] = SAMPLE_SOL[it] - '0' } } }
    val sel = 40; val hd = 3
    val gap = 1.5.dp; val gut = 5.dp
    val total = cell * 9 + gap * 6 + gut * 2
    Canvas(Modifier.size(total)) {
        val c = cell.toPx(); val gp = gap.toPx(); val gt = gut.toPx()
        fun p(k: Int) = k * (c + gp) + (k / 3) * (gt - gp)
        val peers = Geo.peers[sel].toSet()
        for (i in 0 until 81) {
            val o = Offset(p(Geo.col(i)), p(Geo.row(i)))
            val v = values[i]
            val given = SAMPLE[i] != '0'
            val base = if (given) cs.surfaceContainerHighest else cs.surfaceContainerLow
            val isHl = level >= 1 && v == hd
            val blocked = level >= 4 && v == 0 && Geo.peers[i].any { values[it] == hd }
            when {
                i == sel -> drawShape(Morphs.of(Shp.selected, Shp.selected), 0f, cs.tertiaryContainer, Size(c * 1.2f, c * 1.2f), o - Offset(c * 0.1f, c * 0.1f))
                isHl -> drawShape(Morphs.of(Shp.highlighted, Shp.highlighted), 0f, cs.primary, Size(c, c), o)
                else -> drawRoundRect(
                    when { blocked -> cs.surfaceContainerLowest; level >= 2 && i in peers -> lerp(base, cs.primary, 0.16f); else -> base },
                    o, Size(c, c), CornerRadius(c * 0.2f),
                )
            }
            if (v != 0) {
                val l = measurer.measure("$v", ts(c * 0.6f / density / fontScale, if (given) 700 else 520, if (isHl) cs.onPrimary else if (given) cs.onSurface else cs.tertiary, rond = if (given) 0 else 100))
                drawText(l, topLeft = Offset(o.x + (c - l.size.width) / 2, o.y + (c - l.size.height) / 2))
            } else if (i != sel) {
                // Notes: real candidates, the highlighted one lit at "Notes" and above.
                var m = 0x3FE
                for (q in Geo.peers[i]) if (values[q] != 0) m = m and (1 shl values[q]).inv()
                if (i % 3 == 0 || i % 7 == 0) for (d in 1..9) if ((m shr d) and 1 == 1) {
                    val nx = o.x + c * ((d - 1) % 3 + 0.5f) / 3f; val ny = o.y + c * ((d - 1) / 3 + 0.5f) / 3f
                    if (d == hd && level >= 3) drawCircle(cs.primary, c * 0.15f, Offset(nx, ny))
                    else drawCircle(cs.onSurfaceVariant.copy(alpha = 0.6f), c * 0.045f, Offset(nx, ny))
                }
            }
        }
    }
}

/** Five colour sources, each its own MaterialShape; Wallpaper = dynamic colour. */
@Composable
private fun Swatches(sel: Seed?, onPick: (Seed?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val items: List<Triple<Seed?, String, RoundedPolygon>> = listOf(
        Triple(null, "Wallpaper", MaterialShapes.Circle), Triple(Seed.AMBER, "Amber", MaterialShapes.Cookie9Sided),
        Triple(Seed.TEAL, "Teal", MaterialShapes.Clover4Leaf), Triple(Seed.VIOLET, "Violet", MaterialShapes.Flower), Triple(Seed.ROSE, "Rose", MaterialShapes.Puffy),
    )
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            items.forEach { (seed, name, shape) ->
                val on = seed == sel
                val scheme = seed?.let { if (dark) it.dark else it.light }
                Column(Modifier.clickable { onPick(seed) }.semantics { role = Role.RadioButton; selected = on; contentDescription = name }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        ShapeGlyph(shape, 56.dp, scheme?.primaryContainer ?: cs.primaryContainer)
                        if (seed == null) ShapeGlyph(shape, 28.dp, cs.tertiaryContainer)
                        if (on) Box(Modifier.size(28.dp).drawBehind { drawCircle(scheme?.onPrimaryContainer ?: cs.onPrimaryContainer) }, contentAlignment = Alignment.Center) {
                            Icon(Sym["check"], null, Modifier.size(18.dp), tint = scheme?.primaryContainer ?: cs.primaryContainer)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(name, style = ts(12f, if (on) 720 else 520, if (on) cs.onSurface else cs.onSurfaceVariant))
                }
            }
        }
    }
}


/** CSV of games and the raw event logs, written through the Storage Access Framework. */
@Composable
private fun ExportRows(model: AppModel) {
    val ctx = LocalContext.current
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write(model.exportCsv().toByteArray()) }
    }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write(model.exportJson().toByteArray()) }
    }
    val n = model.history.size + (if (model.current != null) 1 else 0)
    Group {
        GroupRow { RowBody("download", "Export your games", "All $n games as a spreadsheet file (CSV)", chevron = true) { csv.launch("sudoku-games.csv") } }
        GroupRow { RowBody("history", "Export every move", "Each move of each game with its timestamp, for your own analysis", chevron = true) { json.launch("sudoku-moves.json") } }
    }
}

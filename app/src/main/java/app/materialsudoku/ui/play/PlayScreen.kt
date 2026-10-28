package app.materialsudoku.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import app.materialsudoku.data.Input
import app.materialsudoku.data.Settings
import app.materialsudoku.game.Fx
import app.materialsudoku.game.Game
import app.materialsudoku.game.Mode
import app.materialsudoku.game.Status
import app.materialsudoku.ui.kit.Hearts
import app.materialsudoku.ui.kit.LocalBuzz
import app.materialsudoku.ui.kit.LocalSprings
import app.materialsudoku.ui.kit.Morphs
import app.materialsudoku.ui.kit.ShapeGlyph
import app.materialsudoku.ui.kit.Shp
import app.materialsudoku.ui.kit.SquishIcon
import app.materialsudoku.ui.kit.Sym
import app.materialsudoku.ui.kit.drawShape
import app.materialsudoku.ui.theme.flex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val DIFFICULTY_NAMES = listOf("Easy", "Medium", "Hard", "Expert", "Master", "Extreme", "Impossible")
val LENGTH_NAMES = listOf("Quick", "Normal", "Long")

@Composable
fun PlayScreen(
    game: Game,
    settings: Settings,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onHint: () -> Unit,
    onFinished: () -> Unit,
    halfwayLine: (timerShown: Boolean) -> Pair<String, String?> = { "Halfway there" to null },
    introActive: Boolean = false,
    revealed: Boolean = true,
    introKind: Int = 0,
    onBoardGeo: (GridGeo) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val buzz = LocalBuzz.current
    val springs = LocalSprings.current
    val scope = rememberCoroutineScope()
    val flash = remember { Animatable(0f) }
    var timerHidden by rememberSaveable { mutableStateOf(!settings.showTimer) }

    // ── The clock: game time only advances while the screen is resumed and the game unpaused, and
    // not before the Home → Play morph has fully landed, plus 100 ms (user, 2026-10-27). ──────────
    var ready by remember { mutableStateOf(!introActive) }
    LaunchedEffect(introActive) { if (!introActive && !ready) { delay(100); ready = true } }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(game, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameMillis { it }
            while (true) {
                val now = withFrameMillis { it }
                if (ready) game.tick(now - last)
                last = now
            }
        }
    }
    // Header and keypad arrive around the morph: they slide in while the board is still in flight.
    val chrome = remember { Animatable(if (introActive) 0f else 1f) }
    LaunchedEffect(Unit) { if (chrome.value < 1f) { delay(180); chrome.animateTo(1f, springs.slow()) } }
    // F2: leaving the app pauses and blurs the board.
    LaunchedEffect(game, owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) game.pause(true) }
        owner.lifecycle.addObserver(obs)
    }

    // ── End of game: let the last animation land, then hand over to Results ─────────────────────
    LaunchedEffect(game) {
        game.fx.collect { fx ->
            when (fx) {
                Fx.Won -> { delay(900); onFinished() }
                Fx.Lost -> { delay(1500); onFinished() }
                else -> {}
            }
        }
    }
    LaunchedEffect(game, settings.input) { game.numberFirst = settings.input == Input.NUMBER }
    // Aid settings may change mid-run: the run keeps the most assisted band it reached (SOLVE-CONDITIONS).
    LaunchedEffect(settings.band) { game.raiseBand(settings.band) }
    // "Point out an easy cell": the engine's cheapest step on the board you actually have.
    LaunchedEffect(game.values, settings.easyCellMarker) {
        if (!settings.easyCellMarker || game.status != Status.PLAYING) { game.easyCell = -1; return@LaunchedEffect }
        game.markAssisted()
        val step = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { app.materialsudoku.engine.Hints.next(game.values, null, game.deal.solution) }
        game.easyCell = step?.placements?.firstOrNull()?.first ?: -1
    }
    // B4: the easy end fills one cell at a time, so you watch it travel across the board.
    val canFinish = game.autoCompleteAvailable
    LaunchedEffect(canFinish, settings.autoCompleteAuto) {
        if (canFinish && settings.autoCompleteAuto && !game.autoFinishing) { delay(450); game.autoFinishing = true }
    }
    LaunchedEffect(game.autoFinishing) {
        if (!game.autoFinishing) return@LaunchedEffect
        while (true) {
            if (!game.autoCompleteStep()) break
            delay(280)
        }
        game.autoFinishing = false
    }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val boardW = minOf(w - 15.dp, 440.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // bar 1: back · difficulty · (fill notes) · pause · settings
                Row(
                    Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)
                        .graphicsLayer { alpha = chrome.value; translationY = -24.dp.toPx() * (1f - chrome.value) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SquishIcon("arrow_back", "Back to Home", onBack)
                    Spacer(Modifier.width(6.dp))
                    // The chip may grow (halfway) into the free space; the icons stay pinned right.
                    Box(Modifier.weight(1f).padding(end = 4.dp), contentAlignment = Alignment.CenterStart) {
                        LevelChip(game, settings, timerHidden, halfwayLine, Modifier)
                    }
                    if (settings.fillNotesButton) SquishIcon("apps", "Fill in all notes", { buzz.toggle(); game.fillNotes() }, iconSize = 22.dp)
                    SquishIcon(if (game.paused) "play_arrow" else "pause", if (game.paused) "Resume" else "Pause", {
                        buzz.toggle(); game.pause(!game.paused)
                    })
                    SquishIcon("tune", "Settings", onSettings, iconSize = 22.dp)
                }
                // bar 2: lives · timer
                Row(
                    Modifier.fillMaxWidth().height(62.dp).padding(start = 18.dp, end = 12.dp)
                        .graphicsLayer { alpha = chrome.value; translationY = -16.dp.toPx() * (1f - chrome.value) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (game.rules.flagMistakes) Lives(game) else Spacer(Modifier.width(1.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SquishIcon(if (timerHidden) "visibility_off" else "visibility", if (timerHidden) "Show timer" else "Hide timer", {
                            buzz.toggle(); timerHidden = !timerHidden
                        }, iconSize = 22.dp)
                        TimerText(game, timerHidden)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(contentAlignment = Alignment.Center) {
                    Board(
                        game, settings, boardW,
                        onFlash = { scope.launch { flash.snapTo(1f); flash.animateTo(0f, tween(520)) } },
                        onLongPress = { i ->
                            game.select(i)
                        },
                        revealed = revealed, revealKind = introKind, onGeo = onBoardGeo,
                        locked = !ready,
                    )
                    if (game.paused) PausedOverlay { buzz.toggle(); game.pause(false) }
                    if (game.status == Status.LOST) LostMark()
                }
                // One fixed strip between board and pad holds the hint's words or Finish, so the
                // keys never move under a finger.
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                    if (game.hint != null) HintStrip(game)
                    else if (!settings.autoCompleteAuto && canFinish && !game.autoFinishing) FinishButton { _ ->
                        buzz.press()
                        game.autoFinishing = true
                    }
                }
                Pad(game, settings, Modifier.weight(1f).graphicsLayer { alpha = chrome.value; translationY = 48.dp.toPx() * (1f - chrome.value) }, onHint)
            }
        }
        // The screen edge flashes error on a mistake (board 1).
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.drawBehind {
                if (flash.value > 0f) {
                    val e = cs.error
                    drawRect(Brush.radialGradient(listOf(Color.Transparent, e.copy(alpha = 0.55f)), center = center, radius = size.maxDimension * 0.75f))
                    drawRect(e, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                }
            },
        )
    }
}

/**
 * Difficulty shape + name + three length bars (board 1's chip). At halfway it becomes the
 * encouragement: it fills with your tertiary, the shape spins half a turn, and two lines replace the
 * name for 2.6 s, then it settles back. Nothing covers the board or the keys.
 */
@Composable
private fun LevelChip(game: Game, settings: Settings, timerHidden: Boolean, line: (Boolean) -> Pair<String, String?>, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val buzz = LocalBuzz.current
    var message by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val spin = remember { Animatable(0f) }
    LaunchedEffect(game, settings.halfwayMessage) {
        game.fx.collect { fx ->
            if (fx is Fx.Halfway && settings.halfwayMessage) {
                message = line(!timerHidden)
                buzz.halfway()
                launch { spin.snapTo(0f); spin.animateTo(180f, springs.slow()) }
                delay(2600)
                message = null
            }
        }
    }
    val lit by animateFloatAsState(if (message != null) 1f else 0f, springs.effects(), label = "lit")
    val difficulty = game.deal.difficulty
    Surface(
        color = lerp(cs.surfaceContainer, cs.tertiaryContainer, lit), contentColor = lerp(cs.onSurface, cs.onTertiaryContainer, lit),
        shape = RoundedCornerShape(50), modifier = modifier.animateContentSize(springs.spatial()),
    ) {
        Row(Modifier.height(40.dp).padding(start = 6.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeGlyph(Shp.difficulty[difficulty], 28.dp, lerp(cs.secondary, cs.onTertiaryContainer, lit), Modifier.graphicsLayer { rotationZ = spin.value })
            Spacer(Modifier.width(8.dp))
            val m = message
            if (m == null) {
                Text(DIFFICULTY_NAMES[difficulty], maxLines = 1, style = TextStyle(fontFamily = flex(15f, 650), fontSize = 15.sp))
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (k in 0..2) Box(
                        Modifier.size(5.dp, (4 + k * 3).dp)
                            .drawBehind { drawRoundRect(if (k <= game.deal.length) cs.tertiary else cs.outlineVariant, cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx())) },
                    )
                }
            } else if (m.second == null) {
                Text(m.first, maxLines = 1, style = TextStyle(fontFamily = flex(15f, 700), fontSize = 15.sp))
            } else {
                Column(verticalArrangement = Arrangement.Center) {
                    Text(m.first, maxLines = 1, style = TextStyle(fontFamily = flex(11f, 600), fontSize = 11.sp, lineHeight = 13.sp))
                    Text(m.second!!, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = TextStyle(fontFamily = flex(14f, 700), fontSize = 14.sp, lineHeight = 17.sp, fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}

/**
 * Lives as MaterialShapes.Heart. A lost heart splits along a crack and its halves fall away
 * (board 1), then an outline is left where it was.
 */
@Composable
private fun Lives(game: Game) {
    val cs = MaterialTheme.colorScheme
    val n = game.rules.lives
    val breaks = remember { List(n) { Animatable(0f) } }
    LaunchedEffect(game) {
        game.fx.collect { fx ->
            if (fx is Fx.Mistake && fx.livesLeft in 0 until n) {
                val b = breaks[fx.livesLeft]
                b.snapTo(0f); b.animateTo(1f, tween(520))
            }
        }
    }
    val path = remember { androidx.compose.ui.graphics.Path() }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in 0 until n) {
            val alive = k < game.lives
            val b = breaks[k].value
            Canvas(Modifier.size(26.dp)) {
                val m = Morphs.of(Shp.heart, Shp.heart)
                app.materialsudoku.ui.kit.morphPath(m, 0f, size, path)
                if (alive) { drawPath(path, cs.error); return@Canvas }
                drawPath(path, cs.outlineVariant, style = androidx.compose.ui.graphics.drawscope.Stroke(size.width * 0.09f))
                if (b in 0.001f..0.999f) {
                    // Two halves, split down a jagged crack, tumbling apart.
                    val w = size.width; val h = size.height
                    val crack = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0.52f * w, 0f); lineTo(0.44f * w, 0.28f * h); lineTo(0.58f * w, 0.5f * h); lineTo(0.42f * w, 0.72f * h); lineTo(0.5f * w, h)
                    }
                    val leftClip = androidx.compose.ui.graphics.Path().apply { addPath(crack); lineTo(0f, h); lineTo(0f, 0f); close() }
                    val rightClip = androidx.compose.ui.graphics.Path().apply { addPath(crack); lineTo(w, h); lineTo(w, 0f); close() }
                    val a = (1f - b).coerceIn(0f, 1f)
                    val dx = 9.dp.toPx() * b; val dy = 16.dp.toPx() * b * b
                    for ((clip, dir) in listOf(leftClip to -1f, rightClip to 1f)) {
                        withTransform({
                            translate(dir * dx, dy)
                            rotate(dir * 40f * b, Offset(w / 2, h / 2))
                            clipPath(clip)
                        }) { drawPath(path, cs.error.copy(alpha = a)) }
                    }
                }
            }
        }
    }
}

/** mm:ss in display weight, hundredths smaller and quieter; tabular figures (report 04 §5). */
@Composable
private fun TimerText(game: Game, hidden: Boolean) {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val wide by animateFloatAsState(if (hidden) 0f else 1f, springs.spatial(), label = "t")
    val ms = game.elapsed
    val cs100 = ms / 10
    val s = cs100 / 100
    val mm = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    val hh = "." + (cs100 % 100).toString().padStart(2, '0')
    Row(
        Modifier.graphicsLayer { alpha = wide; scaleX = 0.6f + 0.4f * wide; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
            .widthIn(max = (140 * wide).dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(mm, maxLines = 1, softWrap = false, style = TextStyle(fontFamily = flex(40f, 380, wdth = 92), fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum", color = cs.onSurface))
        Text(hh, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 1.dp, bottom = 4.dp), style = TextStyle(fontFamily = flex(22f, 480, wdth = 92), fontSize = 22.sp, lineHeight = 22.sp, fontFeatureSettings = "tnum", color = cs.onSurfaceVariant))
    }
}

@Composable
private fun PausedOverlay(onResume: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onResume, color = cs.primary, contentColor = cs.onPrimary, shape = Shp.highlighted.let { app.materialsudoku.ui.kit.MorphShape(Morphs.of(it, it), 0f) },
        modifier = Modifier.size(112.dp).semantics { contentDescription = "Resume" },
    ) { Box(contentAlignment = Alignment.Center) { Icon(Sym["play_arrow"], null, Modifier.size(48.dp)) } }
}

/** Run lost: the board sinks and one big error tile is left (board 1). */
@Composable
private fun LostMark() {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val s = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(500); s.animateTo(1f, springs.spatial()) }
    Box(Modifier.size(112.dp).graphicsLayer { scaleX = s.value; scaleY = s.value }, contentAlignment = Alignment.Center) {
        ShapeGlyph(Shp.highlighted, 112.dp, cs.error)
        Icon(Sym["close"], null, Modifier.size(44.dp), tint = cs.onError)
    }
}

/** The hint's words: where to look, then the technique, then the cell. */
@Composable
private fun HintStrip(game: Game) {
    val cs = MaterialTheme.colorScheme
    val h = game.hint
    AnimatedVisibility(h != null, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
        val text = when (h?.stage) {
            1 -> "Look at this ${unitName(h.unit)}"
            2 -> "Try: ${h.technique}"
            3 -> "This cell: ${h.technique.lowercase()}"
            else -> ""
        }
        Surface(color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, shape = RoundedCornerShape(50)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym["lightbulb"], null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun unitName(u: Int?) = when { u == null -> "area"; u < 9 -> "row"; u < 18 -> "column"; else -> "box" }

// ── The pad: tool rail · a box of nine keys · tool rail ────────────────────────────────────────

@Composable
private fun Pad(game: Game, settings: Settings, modifier: Modifier, onHint: () -> Unit) {
    val buzz = LocalBuzz.current
    BoxWithConstraints(modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 22.dp), contentAlignment = Alignment.Center) {
        val byW = (maxWidth - 104.dp - 16.dp - 20.dp) / 3
        val byH = (maxHeight - 16.dp) / 3
        val key = minOf(byW, byH, 92.dp)
        val railH = key * 3 + 16.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Rail(
                    railH,
                    listOf(
                        RailItem("undo", "Undo", false) { game.undo() },
                        RailItem("ink_eraser", "Erase", false) { game.erase() },
                        RailItem("lightbulb", "Hint", false, enabled = settings.hints) { onHint() },
                    ),
                )
                Keys(game, settings, key)
                Rail(
                    railH,
                    listOf(
                        RailItem("ink_pen", "Pen", game.mode == Mode.PEN) { if (game.mode != Mode.PEN) { buzz.mode(); game.mode = Mode.PEN } },
                        RailItem("apps", "Notes", game.mode == Mode.NOTES) { if (game.mode != Mode.NOTES) { buzz.mode(); game.mode = Mode.NOTES } },
                    ),
                    toggles = true,
                )
            }
        }
    }
}

@Composable
private fun FinishButton(onClick: (kotlinx.coroutines.CoroutineScope) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    Surface(onClick = { onClick(scope) }, color = cs.tertiary, contentColor = cs.onTertiary, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym["done_all"], null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Finish", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private class RailItem(val icon: String, val label: String, val on: Boolean, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * Connected-button shapes [M3E-NEW] stacked in a Column: outer corners 26, inner 10; a checked
 * toggle tweens to fully round and takes the player's tertiary. ButtonGroup ships no semantics,
 * so roles are set by hand.
 */
@Composable
private fun Rail(height: Dp, items: List<RailItem>, toggles: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val h = (height - 3.dp * (items.size - 1)) / items.size
    Column(Modifier.width(52.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { k, it ->
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            val first = k == 0; val last = k == items.lastIndex
            val top by animateDpAsState(if (it.on || pressed) 26.dp else if (first) 26.dp else 10.dp, springs.fast(), label = "t")
            val bottom by animateDpAsState(if (it.on || pressed) 26.dp else if (last) 26.dp else 10.dp, springs.fast(), label = "b")
            val sy by animateFloatAsState(if (pressed) 0.94f else 1f, springs.fast(), label = "s")
            val bg = if (it.on) cs.tertiary else cs.secondaryContainer
            val fg = if (it.on) cs.onTertiary else cs.onSecondaryContainer
            Surface(
                color = bg, contentColor = fg,
                shape = RoundedCornerShape(top, top, bottom, bottom),
                modifier = Modifier
                    .size(52.dp, h)
                    .graphicsLayer { scaleY = sy; alpha = if (it.enabled) 1f else 0.38f }
                    .semantics {
                        contentDescription = it.label
                        role = if (toggles) Role.Tab else Role.Button
                        if (toggles) selected = it.on
                    }
                    .clickable(src, null, enabled = it.enabled, onClick = it.onClick),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Sym[it.icon], null, Modifier.size(24.dp)) } }
        }
    }
}

/** Nine keys laid out exactly like a box, so a digit sits where it would sit in a box. */
@Composable
private fun Keys(game: Game, settings: Settings, key: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in 0..2) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in 0..2) Key(game, settings, r * 3 + c + 1, key)
        }
    }
}

@Composable
private fun Key(game: Game, settings: Settings, d: Int, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val buzz = LocalBuzz.current
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 1f else 0f, if (pressed) springs.hitIn() else springs.hitOut(), label = "p")
    val hl = game.highlight == d && settings.highlight >= 1 || game.armed == d
    val hlP by animateFloatAsState(if (hl) 1f else 0f, if (hl) springs.hitIn() else springs.hitOut(), label = "h")
    val notesMode = (game.mode == Mode.NOTES) != (settings.input == Input.NOTES)
    val fly by animateFloatAsState(if (notesMode) 1f else 0f, springs.hitIn(), label = "f")
    val left = game.remaining(d)
    val done = left <= 0
    val bad = remember { Animatable(0f) }
    LaunchedEffect(game) {
        game.fx.collect { if (it is Fx.Mistake && it.digit == d) { bad.snapTo(1f); bad.animateTo(0f, tween(500)) } }
    }
    val numeral = if (settings.arabicNumerals) "٠١٢٣٤٥٦٧٨٩"[d].toString() else d.toString()
    val path = remember { androidx.compose.ui.graphics.Path() }
    Box(
        Modifier
            .size(size)
            .graphicsLayer { val s = 1f - 0.12f * press; scaleX = s; scaleY = s; alpha = if (done) 0.35f else 1f }
            .semantics { contentDescription = "$d, $left left"; role = Role.Button }
            .pointerInput(settings.input, game) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        when (settings.input) {
                            Input.CELL -> game.press(d)
                            Input.NUMBER -> { buzz.select(); game.arm(d) }
                            Input.NOTES -> game.input(d, if (game.mode == Mode.NOTES) Mode.PEN else Mode.NOTES)
                        }
                    },
                    onLongPress = {
                        if (settings.input == Input.NOTES) game.input(d, game.mode)
                        else if (settings.input == Input.CELL) game.input(d, if (game.mode == Mode.NOTES) Mode.PEN else Mode.NOTES)
                    },
                )
            }
            .drawBehind {
                // Square at rest, rounding toward a circle under the finger; the lit digit's key shares the board's Cookie.
                val base = lerp(lerp(cs.surfaceContainerHigh, cs.primary, hlP), cs.errorContainer, bad.value)
                val morph = if (press > 0.01f) Morphs.of(if (hl) Shp.highlighted else Shp.square, Shp.circle) else Morphs.of(Shp.square, Shp.highlighted)
                drawShape(morph, if (press > 0.01f) press else hlP, base, path = path)
            },
        contentAlignment = Alignment.Center,
    ) {
        val fg = lerp(lerp(cs.onSurface, cs.onPrimary, hlP), cs.onErrorContainer, bad.value)
        val nx = (((d - 1) % 3) - 1) * 24f * fly
        val ny = (((d - 1) / 3) - 1) * 24f * fly + 5f * fly
        val k = size.value / 88f
        Text(
            numeral,
            modifier = Modifier.offset((nx * k).dp, ((-5f + ny) * k).dp).graphicsLayer { val s = 1f - 0.5f * fly; scaleX = s; scaleY = s },
            style = TextStyle(fontFamily = flex(38f * k, 520), fontSize = (38f * k).sp, lineHeight = (38f * k).sp, color = fg),
        )
        Text(
            if (left > 0) left.toString() else "",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (11f * k).dp).graphicsLayer { alpha = (1f - fly) * if (hl) 0.8f else 1f },
            style = TextStyle(fontFamily = flex(12f, 650), fontSize = 12.sp, lineHeight = 12.sp, fontFeatureSettings = "tnum", color = if (hl) fg else cs.onSurfaceVariant),
        )
    }
}


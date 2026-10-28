package app.materialsudoku.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.graphics.shapes.RoundedPolygon
import app.materialsudoku.AppModel
import app.materialsudoku.R
import app.materialsudoku.Screen
import app.materialsudoku.data.Settings
import app.materialsudoku.game.Game
import app.materialsudoku.game.Geo
import app.materialsudoku.ui.kit.Hearts
import app.materialsudoku.ui.kit.LocalBuzz
import app.materialsudoku.ui.kit.LocalSprings
import app.materialsudoku.ui.kit.Morphs
import app.materialsudoku.ui.kit.ShapeGlyph
import app.materialsudoku.ui.kit.Shp
import app.materialsudoku.ui.kit.SquishIcon
import app.materialsudoku.ui.kit.Sym
import app.materialsudoku.ui.kit.morphPath
import app.materialsudoku.ui.play.DIFFICULTY_NAMES
import app.materialsudoku.ui.play.LENGTH_NAMES
import app.materialsudoku.ui.theme.flex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TitleFont = FontFamily(Font(R.font.noto_sans_jp_title, FontWeight.ExtraBold))

val DIFFICULTY_TECH = listOf(
    "Scanning only: a number that has one place left in its box, row or column.",
    "Adds cells with one number left (naked singles), and pointing and claiming.",
    "Adds pairs, triples and quads, naked and hidden.",
    "Adds one-number patterns: X-Wing, Skyscraper, Kite, Empty Rectangle, Swordfish.",
    "Adds multi-number patterns: XY-Wing, XYZ-Wing, W-Wing, unique rectangles, BUG+1.",
    "Adds chains: X-Chain, XY-Chain, alternating inference chains.",
    "The hardest puzzles known. Hints can't help.",
)

/**
 * Home, locked as pair T1 ↔ C1 of board 10. One box at the top holds 数独 with nothing in
 * progress, and the Continue card with a game running; nothing below it ever moves. Then the
 * Difficulty and Length pickers, then a big Start that *is* the chosen difficulty's shape.
 */
@Composable
fun HomeScreen(model: AppModel, settings: Settings) {
    val cs = MaterialTheme.colorScheme
    val buzz = LocalBuzz.current
    val running = model.current
    val enabled = (0..6).filter { settings.difficultyOn(it) }.ifEmpty { listOf(0) }
    var d by remember { mutableIntStateOf(settings.lastDifficulty.takeIf { it in enabled } ?: enabled.first()) }
    var l by remember { mutableIntStateOf(settings.lastLength) }
    val inProgress = running != null
    LaunchedEffect(d, l) { model.prefetch(d, l) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            SquishIcon("bar_chart", "Stats", { buzz.select(); model.screen = Screen.STATS }, container = cs.surfaceContainerHigh, content = cs.onSurface, iconSize = 22.dp)
            Spacer(Modifier.weight(1f))
            SquishIcon("settings", "Settings", { buzz.select(); model.openSettings(Screen.HOME) }, container = cs.surfaceContainerHigh, content = cs.onSurface, iconSize = 22.dp)
        }
        // The slot: one fixed box, two tenants (board 10).
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(236.dp)) {
            AnimatedContent(
                targetState = running,
                transitionSpec = { (fadeIn(tween(220, 40)) + scaleIn(initialScale = 0.9f)).togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.94f)) },
                contentKey = { it != null },
                label = "slot",
            ) { g ->
                if (g == null) Title() else ContinueCard(g) { intro -> buzz.press(); model.continueGame(intro) }
            }
        }
        Spacer(Modifier.height(22.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Label("Difficulty")
            DifficultyTiles(enabled, d) { k -> d = k; buzz.difficulty(k); model.updateSettings { it.copy(lastDifficulty = k) } }
            Spacer(Modifier.height(14.dp))
            Label("Length")
            LengthTiles(l) { k -> l = k; buzz.length(k); model.updateSettings { it.copy(lastLength = k) } }
        }
        // The big shape takes what height is left: 222 dp at most, and its echo (1.26×) must clear the pickers.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(bottom = 22.dp), contentAlignment = Alignment.Center) {
            val size = minOf(222.dp, (maxHeight - 12.dp) / 1.3f)
            BigStart(d, l, inProgress, model.starting, size) { rect -> buzz.start(); model.start(d, l, app.materialsudoku.ui.play.Intro.Start(rect, d, subdued = inProgress)) }
        }
    }
}

@Composable
private fun Label(t: String) {
    Text(
        t, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        style = TextStyle(fontFamily = flex(14f, 600), fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
}

/** 数独 in heavy Noto Sans JP, 162 dp, centred on its ink (CJK ink sits low in the line box). */
@Composable
private fun Title() {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.snapTo(0f); delay(140); rise.animateTo(1f, springs.slow()) }
    Box(Modifier.fillMaxSize().semantics { heading(); contentDescription = "Sudoku" }, contentAlignment = Alignment.Center) {
        Text(
            "数独",
            maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = (-6).dp).graphicsLayer {
                translationY = (1f - rise.value) * 18.dp.toPx()
                val s = 0.94f + 0.06f * rise.value
                scaleX = s; scaleY = s
            },
            // Fixed dp, not sp: an editorial title, not body text.
            style = TextStyle(fontFamily = TitleFont, fontWeight = FontWeight.ExtraBold, fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { 162.dp.toSp() }, lineHeight = with(androidx.compose.ui.platform.LocalDensity.current) { 162.dp.toSp() }, letterSpacing = (-0.05).em, color = cs.onSurface),
        )
    }
}

/** C1: obscured board left; difficulty, length, time and hearts in a column; Continue at its foot. */
@Composable
private fun ContinueCard(g: Game, onClick: (app.materialsudoku.ui.play.Intro) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val density = androidx.compose.ui.platform.LocalDensity.current
    var cardRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var thumbAt by remember { mutableStateOf(Offset.Zero) }
    fun intro() = with(density) {
        app.materialsudoku.ui.play.Intro.Continue(
            cardRect, 40.dp.toPx(),
            app.materialsudoku.ui.play.GridGeo(thumbAt + Offset(8.dp.toPx(), 8.dp.toPx()), 19.dp.toPx(), 1.6.dp.toPx(), 5.dp.toPx()),
        )
    }
    val springs = LocalSprings.current
    var pressed by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (pressed) 1f else 0f, springs.fast(), label = "p")
    val desc = "Continue, ${DIFFICULTY_NAMES[g.deal.difficulty]}, ${LENGTH_NAMES[g.deal.length]} length, ${timeLabel(g.elapsed)}" +
        if (g.rules.flagMistakes) ", ${g.lives} of ${g.rules.lives} lives" else ""
    Surface(
        color = cs.primaryContainer, contentColor = cs.onPrimaryContainer,
        shape = RoundedCornerShape((40 + 12 * p).dp),
        modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { cardRect = it.boundsInRoot() }
            .graphicsLayer { val s = 1f - 0.025f * p; scaleX = s; scaleY = s }
            .semantics { role = Role.Button; contentDescription = desc; heading() }
            .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick(intro()) }) },
    ) {
        Row(Modifier.padding(start = 13.dp, top = 13.dp, bottom = 13.dp, end = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Thumb(g, cell = 19.dp, gap = 1.6.dp, gut = 5.dp, pad = 8.dp, radius = 26.dp, modifier = Modifier.onGloballyPositioned { thumbAt = it.boundsInRoot().topLeft })
            Column(Modifier.weight(1f).fillMaxHeight().padding(top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeGlyph(Shp.difficulty[g.deal.difficulty], 22.dp, cs.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(DIFFICULTY_NAMES[g.deal.difficulty], maxLines = 1, style = TextStyle(fontFamily = flex(26f, 680), fontSize = 26.sp, lineHeight = 30.sp))
                }
                Text("${LENGTH_NAMES[g.deal.length]} length", modifier = Modifier.graphicsLayer { alpha = 0.8f }, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Column(Modifier.padding(start = 2.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Sym["timer"], null, Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(timeLabel(g.elapsed), style = TextStyle(fontFamily = flex(30f, 600), fontSize = 30.sp, lineHeight = 30.sp, fontFeatureSettings = "tnum"))
                    }
                    if (g.rules.flagMistakes) Hearts(g.lives, g.rules.lives, 22.dp, spent = cs.onPrimaryContainer.copy(alpha = 0.55f))
                }
                // Inverted from the card: filled primary sits too close to primaryContainer in a dark scheme.
                Surface(color = cs.onPrimaryContainer, contentColor = cs.primaryContainer, shape = RoundedCornerShape((28 - 12 * p).dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Sym["play_fill"], null, Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Continue", style = TextStyle(fontFamily = flex(16f, 650), fontSize = 16.sp))
                    }
                }
            }
        }
    }
}

fun timeLabel(ms: Long): String { val s = ms / 1000; return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }

/** The obscured board (board 7): Play's surface as plate, each tile its digit's ink role, no digits. */
@Composable
fun Thumb(g: Game, cell: Dp, gap: Dp, gut: Dp, pad: Dp, radius: Dp, selectedShown: Boolean = true, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val size = pad * 2 + cell * 9 + gap * 6 + gut * 2
    val path = remember { Path() }
    Canvas(modifier.size(size)) {
        drawRoundRect(cs.surface, cornerRadius = CornerRadius(radius.toPx()))
        val c = cell.toPx(); val gp = gap.toPx(); val gt = gut.toPx(); val pd = pad.toPx()
        fun p(k: Int) = pd + k * (c + gp) + (k / 3) * (gt - gp)
        for (i in 0 until 81) {
            if (selectedShown && i == g.selected) continue
            val color = when {
                g.isGiven(i) -> cs.onSurfaceVariant
                g.values[i] != 0 -> cs.tertiary
                else -> cs.surfaceContainerHighest
            }
            drawRoundRect(color, Offset(p(Geo.col(i)), p(Geo.row(i))), Size(c, c), CornerRadius(c * 0.18f))
            if (g.values[i] == 0 && g.notes[i] != 0) {
                // A noted cell shows a faint 3×3 of dots: its notes, unread.
                for (k in 0 until 9) if ((g.notes[i] shr (k + 1)) and 1 == 1) drawCircle(
                    cs.onSurfaceVariant, c * 0.05f,
                    Offset(p(Geo.col(i)) + c * (k % 3 + 0.5f) / 3f, p(Geo.row(i)) + c * (k / 3 + 0.5f) / 3f),
                )
            }
        }
        if (selectedShown && g.selected >= 0) {
            val s = c * 1.5f
            val i = g.selected
            morphPath(Morphs.of(Shp.selected, Shp.selected), 0f, Size(s, s), path)
            path.translate(Offset(p(Geo.col(i)) - (s - c) / 2, p(Geo.row(i)) - (s - c) / 2 - 1f))
            drawPath(path, cs.tertiaryContainer)
        }
    }
}

/** Connected corners (28 outer / 8 inner) for a 3-column grid of n tiles. */
private fun corners(k: Int, n: Int, outer: Dp, inner: Dp): RoundedCornerShape {
    val rows = (n + 2) / 3; val r = k / 3; val c = k % 3
    val rowN = minOf(3, n - r * 3); val topN = minOf(3, n)
    val tl = r == 0 && c == 0; val tr = r == 0 && c == topN - 1
    val bl = r == rows - 1 && c == 0; val br = r == rows - 1 && c == rowN - 1
    return RoundedCornerShape(if (tl) outer else inner, if (tr) outer else inner, if (br) outer else inner, if (bl) outer else inner)
}

@Composable
private fun DifficultyTiles(enabled: List<Int>, sel: Int, onPick: (Int) -> Unit) {
    var tip by remember { mutableIntStateOf(-1) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        enabled.chunked(3).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { c, k ->
                    val idx = r * 3 + c
                    Box(Modifier.weight(1f)) {
                        PickTile(
                            on = k == sel, height = 92.dp, rest = corners(idx, enabled.size, 28.dp, 8.dp), full = 46.dp,
                            onClick = { onPick(k) }, onLongPress = { tip = k },
                            label = DIFFICULTY_NAMES[k], shape = Shp.difficulty[k],
                        )
                        if (tip == k) TechTip(k) { tip = -1 }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TechTip(k: Int, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(k) { delay(2600); onDismiss() }
    Popup(alignment = Alignment.TopCenter, offset = androidx.compose.ui.unit.IntOffset(0, -260), onDismissRequest = onDismiss, properties = PopupProperties(focusable = false)) {
        Surface(color = cs.surfaceContainerHighest, contentColor = cs.onSurfaceVariant, shape = RoundedCornerShape(12.dp), shadowElevation = 6.dp, modifier = Modifier.width(252.dp)) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeGlyph(Shp.difficulty[k], 16.dp, cs.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(DIFFICULTY_NAMES[k], color = cs.onSurface, style = TextStyle(fontFamily = flex(14f, 600), fontSize = 14.sp))
                }
                Spacer(Modifier.height(4.dp))
                Text(DIFFICULTY_TECH[k], style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LengthTiles(sel: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in 0..2) Box(Modifier.weight(1f)) {
            PickTile(on = k == sel, height = 56.dp, rest = corners(k, 3, 24.dp, 8.dp), full = 28.dp, onClick = { onPick(k) }, label = LENGTH_NAMES[k])
        }
    }
}

/**
 * ToggleButton with connected → CornerFull shapes [M3E-NEW]; selectableGroup + Role.RadioButton by
 * hand. The checked tile springs to full corners and fills secondary; its icon scales and turns.
 */
@Composable
private fun PickTile(
    on: Boolean, height: Dp, rest: RoundedCornerShape, full: Dp, onClick: () -> Unit, label: String,
    shape: RoundedPolygon? = null, onLongPress: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val t by animateFloatAsState(if (on) 1f else 0f, springs.fast(), label = "on")
    val c by animateFloatAsState(if (on) 1f else 0f, springs.effects(), label = "c")
    var pressed by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (pressed) 1f else 0f, springs.fast(), label = "p")
    val bg = lerp(cs.surfaceContainerHighest, cs.secondary, c)
    val fg = lerp(cs.onSurface, cs.onSecondary, c)
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(
        Modifier.fillMaxWidth().height(height)
            .graphicsLayer { val s = 1f - 0.05f * p; scaleX = s; scaleY = s }
            .semantics { role = Role.RadioButton; selected = on; contentDescription = label }
            .pointerInput(onLongPress) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() },
                    onLongPress = onLongPress?.let { f -> { _: Offset -> f() } },
                )
            }
            .drawBehind {
                val sz = this.size
                val restO = rest.createOutline(sz, layoutDirection, density)
                val r = full.toPx()
                // Corners tween from connected to full (the outline lerp, per corner).
                val path = Path()
                val o = restO as androidx.compose.ui.graphics.Outline.Rounded
                val rr = o.roundRect
                fun mix(a: Float) = a + (minOf(r, sz.height / 2) - a) * t
                path.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        0f, 0f, sz.width, sz.height,
                        CornerRadius(mix(rr.topLeftCornerRadius.x)), CornerRadius(mix(rr.topRightCornerRadius.x)),
                        CornerRadius(mix(rr.bottomRightCornerRadius.x)), CornerRadius(mix(rr.bottomLeftCornerRadius.x)),
                    ),
                )
                drawPath(path, bg)
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shape != null) ShapeGlyph(shape, 34.dp, lerp(cs.onSurfaceVariant, cs.onSecondary, c), Modifier.graphicsLayer {
                val s = 1f + 0.16f * t; scaleX = s; scaleY = s; rotationZ = -12f * t
            })
            Text(label, color = fg, style = TextStyle(fontFamily = flex(16f, if (on) 720 else 560), fontSize = 16.sp, lineHeight = 20.sp))
        }
    }
}

/**
 * The big Start: a Surface whose shape is a Morph between the previous and the chosen difficulty's
 * MaterialShape. With a game running it steps back (0.84×, secondaryContainer, "New game").
 */
@Composable
private fun BigStart(d: Int, l: Int, inProgress: Boolean, starting: Boolean, size: Dp, onGo: (androidx.compose.ui.geometry.Rect) -> Unit) {
    var shapeRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf(Shp.difficulty[d]) }
    var to by remember { mutableStateOf(Shp.difficulty[d]) }
    val morph = remember { Animatable(1f) }
    var turn by remember { mutableFloatStateOf(0f) }
    var echoTurn by remember { mutableFloatStateOf(-14f) }
    LaunchedEffect(d) {
        val target = Shp.difficulty[d]
        if (target != to) {
            from = to; to = target; turn += 24f; echoTurn -= 30f
            morph.snapTo(0f); morph.animateTo(1f, springs.slow())
        }
    }
    val spin by animateFloatAsState(turn, springs.slow(), label = "turn")
    val echoSpin by animateFloatAsState(echoTurn, springs.slow(), label = "eturn")
    val run by animateFloatAsState(if (inProgress) 1f else 0f, springs.slow(), label = "run")
    val runC by animateFloatAsState(if (inProgress) 1f else 0f, springs.effects(), label = "runC")
    val lenScale by animateFloatAsState(listOf(0.94f, 1f, 1.05f)[l], springs.slow(), label = "len")
    var pressed by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (pressed) 1f else 0f, springs.fast(), label = "p")
    val go = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val path = remember { Path() }
    val k = size / 222.dp

    Box(Modifier.fillMaxWidth().height(size * 1.3f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size)
                .graphicsLayer { val s = lenScale * (1f - 0.16f * run); scaleX = s; scaleY = s }
                .onGloballyPositioned { shapeRect = it.boundsInRoot() }
                .semantics { role = Role.Button; contentDescription = (if (inProgress) "New game, " else "Start, ") + "${DIFFICULTY_NAMES[d]}, ${LENGTH_NAMES[l]}" }
                .pointerInput(inProgress, starting) {
                    detectTapGestures(
                        onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                        onTap = {
                            if (starting) return@detectTapGestures
                            scope.launch { ring.snapTo(0.01f); ring.animateTo(1f, tween(700)); ring.snapTo(0f) }
                            scope.launch {
                                go.snapTo(0f)
                                go.animateTo(1f, tween(700))
                                go.snapTo(0f)
                            }
                            onGo(shapeRect)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val s = this.size
                // The echo: a larger copy behind, counter-turning.
                if (run < 0.99f) {
                    val e = s.width * 1.26f
                    morphPath(Morphs.of(from, to), morph.value, Size(e, e), path)
                    path.translate(Offset(-(e - s.width) / 2, -(e - s.height) / 2))
                    rotateAbout(echoSpin, s) { drawPath(path, cs.surfaceContainerHigh.copy(alpha = 1f - run)) }
                }
                if (ring.value > 0f) drawCircle(cs.primary.copy(alpha = 0.9f * (1f - ring.value)), radius = s.width / 2 * (0.9f + 0.7f * ring.value), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                // The press: squish and an 8° turn; the go: 30° back-swing then a quarter wind.
                val g = go.value
                val wind = if (g < 0.25f) -30f * (g / 0.25f) else -30f + 120f * ((g - 0.25f) / 0.75f)
                val gs = if (g < 0.25f) 1f - 0.1f * (g / 0.25f) else 0.9f + 0.1f * ((g - 0.25f) / 0.75f)
                val sc = (1f - 0.07f * p) * (if (g > 0f) gs else 1f)
                morphPath(Morphs.of(from, to), morph.value, s, path)
                rotateAbout(spin + 8f * p + (if (g > 0f) wind else 0f), s) {
                    scaleAbout(sc, s) { drawPath(path, lerp(cs.primary, cs.secondaryContainer, runC)) }
                }
            }
            // Labels: two layers cross-faded by one float.
            Column(Modifier.graphicsLayer { alpha = 1f - runC; val q = (1f - 0.1f * runC) * k; scaleX = q; scaleY = q }, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Sym["play_fill"], null, Modifier.size(36.dp), tint = cs.onPrimary)
                Text(if (starting && !inProgress) "Starting" else "Start", color = cs.onPrimary, style = TextStyle(fontFamily = flex(30f, 680), fontSize = 30.sp, lineHeight = 34.sp))
                Text("${DIFFICULTY_NAMES[d]} · ${LENGTH_NAMES[l]}", color = cs.onPrimary.copy(alpha = 0.85f), style = TextStyle(fontFamily = flex(15f, 480), fontSize = 15.sp, lineHeight = 20.sp))
            }
            Column(Modifier.graphicsLayer { alpha = runC; val q = (0.9f + 0.1f * runC) * k; scaleX = q; scaleY = q }, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Sym["add"], null, Modifier.size(32.dp), tint = cs.onSecondaryContainer)
                Text(if (starting) "Starting" else "New game", color = cs.onSecondaryContainer, style = TextStyle(fontFamily = flex(28f, 680), fontSize = 28.sp, lineHeight = 34.sp))
                Text("${DIFFICULTY_NAMES[d]} · ${LENGTH_NAMES[l]}", color = cs.onSecondaryContainer.copy(alpha = 0.85f), style = TextStyle(fontFamily = flex(15f, 480), fontSize = 15.sp, lineHeight = 20.sp))
            }
        }
    }
}

private inline fun DrawScope.rotateAbout(deg: Float, s: Size, block: DrawScope.() -> Unit) =
    rotate(deg, Offset(s.width / 2, s.height / 2), block)

private inline fun DrawScope.scaleAbout(k: Float, s: Size, block: DrawScope.() -> Unit) =
    scale(k, k, Offset(s.width / 2, s.height / 2), block)


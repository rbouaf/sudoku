package app.materialsudoku

import android.os.Bundle
import android.provider.Settings as SystemSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.materialsudoku.data.Motion
import app.materialsudoku.ui.home.HomeScreen
import app.materialsudoku.ui.kit.Buzz
import app.materialsudoku.ui.kit.LocalBuzz
import app.materialsudoku.ui.kit.LocalSprings
import app.materialsudoku.ui.kit.Springs
import app.materialsudoku.ui.play.PlayScreen
import app.materialsudoku.ui.results.ResultsScreen
import app.materialsudoku.ui.settings.SettingsScreen
import app.materialsudoku.ui.stats.StatsScreen
import app.materialsudoku.ui.theme.SudokuTheme

/** "Medium game replaced · Undo" rides over Play for 4 s; no dialog (board 10). */
@androidx.compose.runtime.Composable
private fun ReplacedSnackbar(model: AppModel) {
    val old = model.replaced ?: return
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(old) { kotlinx.coroutines.delay(4000); model.dropReplaced() }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().systemBarsPadding()) {
        androidx.compose.material3.Snackbar(
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 26.dp),
            containerColor = cs.inverseSurface, contentColor = cs.inverseOnSurface,
            action = { androidx.compose.material3.TextButton(onClick = { model.undoReplace() }) { androidx.compose.material3.Text("Undo", color = cs.inversePrimary) } },
        ) { androidx.compose.material3.Text("${app.materialsudoku.ui.play.DIFFICULTY_NAMES[old.deal.difficulty]} game replaced") }
    }
}

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val buzz = Buzz(this)
        val systemReduced = SystemSettings.Global.getFloat(contentResolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent {
            val s by model.settings.collectAsStateWithLifecycle()
            LaunchedEffect(s.haptics) { buzz.level = s.haptics }
            LaunchedEffect(s.kanjiName) { app.materialsudoku.data.LauncherName.apply(applicationContext, s.kanjiName) }
            val reduced = when (s.motion) { Motion.SYSTEM -> systemReduced; Motion.REDUCED -> true; Motion.FULL -> false }
            val springs = remember(reduced) { Springs(reduced) }
            SudokuTheme(mode = s.theme, seed = s.seed, amoled = s.amoled) {
                CompositionLocalProvider(LocalBuzz provides buzz, LocalSprings provides springs) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        BackHandler(model.screen != Screen.HOME) {
                            when (model.screen) {
                                Screen.PLAY -> model.leavePlay()
                                Screen.SETTINGS -> model.screen = model.settingsFrom
                                else -> model.screen = Screen.HOME
                            }
                        }
                        AnimatedContent(
                            targetState = model.screen,
                            transitionSpec = {
                                if (targetState == Screen.PLAY && model.intro != null) {
                                    androidx.compose.animation.EnterTransition.None.togetherWith(androidx.compose.animation.ExitTransition.None)
                                } else {
                                    (fadeIn(tween(220, 60)) + scaleIn(initialScale = 0.96f, animationSpec = tween(320)))
                                        .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 1.02f, animationSpec = tween(200)))
                                }
                            },
                            label = "screen",
                        ) { screen ->
                            when (screen) {
                                Screen.HOME -> HomeScreen(model, s)
                                Screen.PLAY -> model.current?.let { g ->
                                    PlayScreen(
                                        g, s,
                                        onBack = model::leavePlay,
                                        onSettings = { model.openSettings(Screen.PLAY) },
                                        onHint = model::hint,
                                        onFinished = model::onFinished,
                                        halfwayLine = { shown -> app.materialsudoku.game.halfwayLine(g, model.history, shown) },
                                        introActive = model.intro != null,
                                        revealed = model.revealed,
                                        introKind = when (model.intro) { is app.materialsudoku.ui.play.Intro.Continue -> 1; else -> 0 },
                                        onBoardGeo = { model.boardGeo = it },
                                    )
                                }
                                Screen.RESULTS -> ResultsScreen(model, s)
                                Screen.STATS -> StatsScreen(model, s)
                                Screen.SETTINGS -> SettingsScreen(model, s)
                            }
                        }
                        val intro = model.intro
                        val geo = model.boardGeo
                        val cur = model.current
                        if (intro != null && geo != null && cur != null && model.screen == Screen.PLAY) {
                            app.materialsudoku.ui.play.IntroOverlay(intro, geo, cur, onReveal = { model.revealed = true }, onDone = { model.intro = null })
                        }
                        ReplacedSnackbar(model)
                    }
                }
            }
        }
    }
}

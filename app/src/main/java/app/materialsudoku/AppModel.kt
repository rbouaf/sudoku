package app.materialsudoku

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.materialsudoku.data.GameStore
import app.materialsudoku.data.Settings
import app.materialsudoku.data.SettingsStore
import app.materialsudoku.engine.Analysis
import app.materialsudoku.engine.Difficulty
import app.materialsudoku.engine.Famous
import app.materialsudoku.game.PlacementRating
import app.materialsudoku.engine.Generator
import app.materialsudoku.engine.Hints
import app.materialsudoku.engine.Length
import app.materialsudoku.game.Deal
import app.materialsudoku.game.Game
import app.materialsudoku.game.Geo
import app.materialsudoku.game.HintView
import app.materialsudoku.game.Mode
import app.materialsudoku.game.Rules
import app.materialsudoku.game.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.SecureRandom

enum class Screen { HOME, PLAY, RESULTS, STATS, SETTINGS }

/** The app's one model: settings, the game in progress, finished games, and where we are. */
class AppModel(app: Application) : AndroidViewModel(app) {
    val settingsStore = SettingsStore(app)
    val settings: StateFlow<Settings> = settingsStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, runBlocking { Settings() })
    private val store = GameStore(app)
    private val saveLock = Mutex()

    var screen by mutableStateOf(Screen.HOME)
    /** Where Settings returns to. */
    var settingsFrom by mutableStateOf(Screen.HOME)
    var current by mutableStateOf<Game?>(null); private set
    var finished by mutableStateOf<Game?>(null); private set
    var history by mutableStateOf<List<Game>>(emptyList()); private set
    /** The game just replaced by "New game", kept for the Undo snackbar. */
    var replaced by mutableStateOf<Game?>(null); private set
    var starting by mutableStateOf(false); private set

    /** Home → Play morph in flight (ui/play/Intro.kt), the board's measured place, and whether Play may show it. */
    var intro by mutableStateOf<app.materialsudoku.ui.play.Intro?>(null)
    var boardGeo by mutableStateOf<app.materialsudoku.ui.play.GridGeo?>(null)
    var revealed by mutableStateOf(true)
    private fun beginIntro(i: app.materialsudoku.ui.play.Intro?) { intro = i; revealed = i == null; boardGeo = null }

    init {
        current = store.loadCurrent()
        viewModelScope.launch(Dispatchers.IO) {
            history = store.all().filter { it.status != Status.PLAYING }
        }
        viewModelScope.launch { snapshotFlow { current?.id }.collect { id -> withContext(Dispatchers.IO) { store.setCurrent(id) } } }
        // Autosave: every change, debounced; the write itself is never cancelled, so a back gesture can't lose a move.
        viewModelScope.launch {
            snapshotFlow { current?.let { it to it.revision } }.filterNotNull().debounce(250).collect { (g, _) -> save(g) }
        }
    }

    private suspend fun save(g: Game) = withContext(NonCancellable + Dispatchers.IO) { saveLock.withLock { store.save(g) } }

    fun updateSettings(f: (Settings) -> Settings) = viewModelScope.launch { settingsStore.update(f) }

    /** Deals a new puzzle. The seed is internal; the puzzle's identity is its givens (06 §1.1). */
    fun start(difficulty: Int, length: Int, from: app.materialsudoku.ui.play.Intro? = null) {
        if (starting) return
        starting = true
        val s = settings.value
        viewModelScope.launch {
            val key = difficulty to length
            val deal = synchronized(ready) { ready.remove(key) } ?: withContext(Dispatchers.Default) { deal(difficulty, length) }
            prefetch(difficulty, length)
            val old = current
            if (old != null && old.status == Status.PLAYING) { replaced = old; save(old) }
            val g = Game(
                id = "g" + System.currentTimeMillis().toString(36),
                deal = deal,
                rules = Rules(flagMistakes = s.flagMistakes, lives = if (s.flagMistakes) s.lives else 0, autoRemoveNotes = s.autoRemoveNotes),
                band = s.band,
            )
            current = g
            save(g)
            starting = false
            beginIntro(from)
            screen = Screen.PLAY
        }
    }

    /** Undo for "New game": the replaced game comes back and the new one is discarded. */
    fun undoReplace() {
        val old = replaced ?: return
        val now = current
        if (now != null) store.delete(now.id)
        current = old
        replaced = null
        beginIntro(null)
        screen = Screen.PLAY
    }
    fun dropReplaced() {
        val old = replaced ?: return
        replaced = null
        // A game left unfinished and replaced is abandoned, not lost: it keeps its log but leaves the board.
        viewModelScope.launch { save(old) }
    }

    /** Rates each placement against the board the player actually had (02 §2.4). */
    fun raterFor(g: Game) = app.materialsudoku.game.Rater { values, cell, digit ->
        val r = Analysis.rate(values, cell, digit, g.deal.solution)
        PlacementRating(r.technique?.displayName, r.technique?.difficulty?.ordinal ?: -1, r.effort, r.easiest?.displayName, r.detour)
    }

    // ── A puzzle ready before you press Start: generation is ~0.2 s on the phone, never seen ───────
    private val ready = HashMap<Pair<Int, Int>, Deal>()
    fun prefetch(difficulty: Int, length: Int) {
        val key = difficulty to length
        if (ready.containsKey(key)) return
        viewModelScope.launch(Dispatchers.Default) { val d = deal(difficulty, length); synchronized(ready) { ready.putIfAbsent(key, d) } }
    }

    private fun deal(difficulty: Int, length: Int): Deal {
        val p = Generator.generate(Difficulty.entries[difficulty], Length.entries[length], SecureRandom().nextLong())
        val famous = Famous.recognise(p.givens, p.solution)
        return Deal(
            givens = p.givens, solution = p.solution, difficulty = p.grade.difficulty.ordinal,
            length = p.grade.length.ordinal, seed = p.seed, generatorVersion = p.generatorVersion,
            hardest = p.grade.hardest?.displayName,
            famous = famous?.let { f -> listOfNotNull(f.name, f.by.takeIf { it.isNotEmpty() }, f.year?.toString()).joinToString(", ") + ". " + f.story },
        )
    }

    /** Same puzzle again: a fresh run of the same givens, under today's rules. */
    fun replay(old: Game) {
        val s = settings.value
        val g = Game(
            id = "g" + System.currentTimeMillis().toString(36),
            deal = old.deal,
            rules = Rules(flagMistakes = s.flagMistakes, lives = if (s.flagMistakes) s.lives else 0, autoRemoveNotes = s.autoRemoveNotes),
            band = s.band,
        )
        current = g
        viewModelScope.launch { save(g) }
        beginIntro(null)
        screen = Screen.PLAY
    }

    /** One row per game; the log itself goes in the JSON export. */
    fun exportCsv(): String = buildString {
        appendLine("id,started,difficulty,length,status,time_ms,lives,lives_left,flag_mistakes,band,assisted,mistakes,givens,solution")
        (history + listOfNotNull(current)).forEach { g ->
            appendLine(listOf(g.id, g.startedAt, g.deal.difficulty, g.deal.length, g.status, g.elapsed, g.rules.lives, g.lives, g.rules.flagMistakes, g.band, g.assisted, g.mistakes, g.deal.givens, g.deal.solution).joinToString(","))
        }
    }

    fun exportJson(): String = store.exportAll()

    fun continueGame(from: app.materialsudoku.ui.play.Intro? = null) {
        if (current == null) return
        beginIntro(from)
        screen = Screen.PLAY
    }

    fun leavePlay() {
        current?.let { g -> g.pause(false); viewModelScope.launch { save(g) } }
        screen = Screen.HOME
    }

    fun onFinished() {
        val g = current ?: return
        viewModelScope.launch { save(g) }
        finished = g
        history = history + g
        current = null
        screen = Screen.RESULTS
    }

    fun openSettings(from: Screen) { settingsFrom = from; screen = Screen.SETTINGS }

    // ── Hints (D5): four presses, from a nudge to the answer ─────────────────────────────────────
    fun hint() {
        val g = current ?: return
        val now = g.hint
        val stage = (now?.stage ?: 0) + 1
        if (now != null && stage <= 4) {
            if (stage == 4) applyHint(g, now) else g.showHint(now.copy(stage = stage))
            return
        }
        viewModelScope.launch {
            val step = withContext(Dispatchers.Default) { Hints.next(g.values, null, g.deal.solution) } ?: return@launch
            val target = step.placements.firstOrNull()?.first ?: step.eliminations.firstOrNull()?.first
            val unit = step.focusUnit ?: target?.let { Geo.unitsOf(it)[2] }
            g.showHint(
                HintView(
                    stage = 1, unit = unit, cells = step.focusCells, technique = step.technique.displayName,
                    cell = target, digit = step.placements.firstOrNull()?.second,
                    eliminations = step.eliminations,
                ),
            )
        }
    }

    private fun applyHint(g: Game, h: HintView) {
        g.showHint(h.copy(stage = 4))
        val c = h.cell
        if (h.digit != null && c != null) {
            g.select(c)
            g.input(h.digit, Mode.PEN)
        } else if (h.eliminations.isNotEmpty()) {
            g.applyEliminations(h.eliminations)
        }
        g.clearHint()
    }
}

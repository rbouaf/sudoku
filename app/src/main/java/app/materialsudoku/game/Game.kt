package app.materialsudoku.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** What a game was dealt. `givens` is the puzzle's identity: 81 chars, '0' empty (06 §1.1). */
data class Deal(
    val givens: String,
    val solution: String,
    val difficulty: Int,          // 0 Easy … 5 Extreme, 6 Impossible
    val length: Int,              // 0 Quick, 1 Normal, 2 Long
    val seed: Long,
    val generatorVersion: Int,
    val hardest: String? = null,  // display name of the hardest technique the grader found
    val famous: String? = null,   // set when the class ID matches a known puzzle
)

enum class Status { PLAYING, WON, LOST }
enum class Mode { PEN, NOTES }

/** The rules a run was started under. The net (mistakes, lives) is locked for the run (SOLVE-CONDITIONS). */
data class Rules(
    val flagMistakes: Boolean = true,
    val lives: Int = 3,
    val autoRemoveNotes: Boolean = true,
)

/**
 * One event in the append-only log (02 §6). Every stat is computed from these, so the log is the
 * record, never a summary. `t` is game time in ms, which excludes pauses.
 */
data class Ev(val t: Long, val kind: Char, val cell: Int = -1, val digit: Int = 0) {
    companion object {
        const val SELECT = 's'
        const val PLACE = 'P'
        const val MISTAKE = 'X'
        const val NOTE_ON = 'n'
        const val NOTE_OFF = 'o'
        const val ERASE = 'E'
        const val UNDO = 'U'
        const val HINT = 'H'          // digit = stage 1..4
        const val FILL_NOTES = 'F'
        const val AUTO = 'A'          // auto-complete placed this cell
        const val PAUSE = 'p'
        const val RESUME = 'r'
        const val GUESS = 'G'         // placement flagged as a guess (no logical step forced it)
        const val AID = 'a'           // an aid setting changed mid-run; digit = new band
        const val HALFWAY = 'h'       // the halfway message was shown
    }
}

/** One-shot things the screen animates and the phone feels. */
sealed interface Fx {
    data class Placed(val cell: Int, val digit: Int, val completedUnits: List<Int>) : Fx
    data class Mistake(val cell: Int, val digit: Int, val livesLeft: Int) : Fx
    data class NoteToggled(val cell: Int, val on: Boolean) : Fx
    data class Erased(val cell: Int) : Fx
    data object Undone : Fx
    data class HintShown(val stage: Int, val cell: Int?) : Fx
    data object Won : Fx
    data object Lost : Fx
    data object FilledWrong : Fx
    /** Half of the empty cells are filled (counted, never checked: it leaks nothing about correctness). */
    data object Halfway : Fx
}

class Snapshot(val values: IntArray, val notes: IntArray)

/**
 * A game in progress. Plain Kotlin with Compose state, owned by the app model; the screen reads it
 * and calls its verbs. All rule logic lives here so it can be unit-tested without a screen.
 */
class Game(
    val id: String,
    val deal: Deal,
    val rules: Rules,
    values: IntArray = IntArray(81) { deal.givens[it] - '0' },
    notes: IntArray = IntArray(81),
    lives: Int = rules.lives,
    elapsed: Long = 0L,
    val events: MutableList<Ev> = mutableListOf(),
    status: Status = Status.PLAYING,
    val startedAt: Long = System.currentTimeMillis(),
    band: Int = 0,
) {
    val givens = IntArray(81) { deal.givens[it] - '0' }
    val solution = IntArray(81) { deal.solution[it] - '0' }
    fun isGiven(i: Int) = givens[i] != 0

    var values by mutableStateOf(values); private set
    var notes by mutableStateOf(notes); private set
    var selected by mutableIntStateOf(-1); private set
    var highlight by mutableIntStateOf(0); private set
    var mode by mutableStateOf(Mode.PEN)
    var lives by mutableIntStateOf(lives); private set
    var status by mutableStateOf(status); private set
    var paused by mutableStateOf(false); private set
    /** Number-first input: the armed digit, or 0. */
    var armed by mutableIntStateOf(0); private set
    var hint by mutableStateOf<HintView?>(null); private set
    /** The most assisted record band this run ever reached (SOLVE-CONDITIONS §5): 0..2. */
    var band by mutableIntStateOf(band.coerceIn(0, 1)); private set
    val hinted: Boolean get() = events.any { it.kind == Ev.HINT }
    val usedFillNotes: Boolean get() = events.any { it.kind == Ev.FILL_NOTES }
    /** Hints and fill-all-notes are equally big aids (user, 2026-10-27): either puts the run in the assisted records. */
    val assisted: Boolean get() = hinted || usedFillNotes

    /** Aid settings may change mid-run; the run keeps the highest band it reached, and the change is logged. */
    fun raiseBand(b: Int) {
        if (b > band && status == Status.PLAYING) { band = b; log(Ev.AID, digit = b) }
    }

    private var _elapsed = mutableLongStateOf(elapsed)
    val elapsed: Long get() = _elapsed.longValue

    var mistakes = events.count { it.kind == Ev.MISTAKE }; private set
    private val undo = ArrayDeque<Snapshot>()

    private val _fx = MutableSharedFlow<Fx>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val fx: SharedFlow<Fx> = _fx

    /** Bumped on every change worth saving. */
    var revision by mutableIntStateOf(0); private set

    private fun log(kind: Char, cell: Int = -1, digit: Int = 0) { events += Ev(elapsed, kind, cell, digit); revision++ }

    fun tick(dtMs: Long) {
        if (status == Status.PLAYING && !paused) _elapsed.longValue += dtMs
    }

    fun pause(p: Boolean) {
        if (status != Status.PLAYING || p == paused) return
        paused = p
        log(if (p) Ev.PAUSE else Ev.RESUME)
    }

    fun remaining(d: Int): Int = 9 - values.count { it == d }

    // ── Input ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Number-first input (Settings → Entering numbers). In this mode the highlighted number *is* the
     * armed number: a filled cell arms its digit, an empty cell receives the armed digit, and once all
     * nine are placed the next unfinished number is armed (9 wraps to 1). Set by Play from the setting.
     */
    var numberFirst = false
        set(v) { field = v; if (!v) armed = 0 }

    fun select(i: Int) {
        if (status != Status.PLAYING || paused) return
        if (numberFirst) {
            if (values[i] != 0) {
                // A filled cell arms its own digit, so the board shows one number at a time.
                armed = values[i]; highlight = armed; selected = -1
                log(Ev.SELECT, i)
                return
            }
            selected = i
            if (armed != 0) input(armed) else log(Ev.SELECT, i)
            return
        }
        selected = i
        if (values[i] != 0) highlight = values[i]
        log(Ev.SELECT, i)
    }

    /** A key press in cell-first input. */
    fun press(d: Int) {
        if (status != Status.PLAYING || paused) return
        val i = selected
        if (i < 0 || isGiven(i)) { highlight = if (highlight == d) 0 else d; return }
        input(d)
    }

    /**
     * Number-first: once all nine of the armed number are placed, move on to the next number that
     * still has places left, wrapping 9 → 1 (user, 2026-10-27). Nothing left: disarm.
     */
    private fun armNext() {
        val next = (1..8).map { (armed - 1 + it) % 9 + 1 }.firstOrNull { remaining(it) > 0 } ?: 0
        armed = next
        highlight = next
    }

    /** Number-first input: tapping a key arms it; tapping it again disarms. */
    fun arm(d: Int) {
        if (status != Status.PLAYING || paused) return
        armed = if (armed == d) 0 else d
        highlight = armed
        // The key now owns the highlight: a selected (filled) cell lets go, so only this number lights.
        selected = -1
    }

    /** Place or note `d` in the selected cell, according to the mode. */
    fun input(d: Int, forceMode: Mode? = null) {
        val i = selected
        if (i < 0 || isGiven(i) || status != Status.PLAYING || paused) return
        highlight = d
        if ((forceMode ?: mode) == Mode.NOTES) {
            if (values[i] != 0) return
            push()
            val on = !notes[i].hasBit(d)
            notes = notes.copyOf().also { it[i] = it[i] xor (1 shl d) }
            log(if (on) Ev.NOTE_ON else Ev.NOTE_OFF, i, d)
            _fx.tryEmit(Fx.NoteToggled(i, on))
            return
        }
        if (values[i] == d) return
        if (rules.flagMistakes && d != solution[i]) { mistake(i, d); return }
        place(i, d, Ev.PLACE)
        if (numberFirst && armed != 0 && remaining(armed) == 0) armNext()
    }

    private fun place(i: Int, d: Int, kind: Char) {
        push()
        val v = values.copyOf().also { it[i] = d }
        val n = notes.copyOf().also { it[i] = 0 }
        if (rules.autoRemoveNotes) for (p in Geo.peers[i]) n[p] = n[p] and (1 shl d).inv()
        values = v; notes = n
        log(kind, i, d)
        hint = null
        val done = Geo.celebratedUnitsOf(i).filter { u -> Geo.cellsOf(u).all { v[it] != 0 } }
        _fx.tryEmit(Fx.Placed(i, d, done))
        val empties = givens.count { it == 0 }
        val filled = (0 until 81).count { givens[it] == 0 && v[it] != 0 }
        if (filled * 2 >= empties && filled < empties && events.none { it.kind == Ev.HALFWAY }) {
            log(Ev.HALFWAY)
            _fx.tryEmit(Fx.Halfway)
        }
        checkEnd()
    }

    private fun mistake(i: Int, d: Int) {
        lives -= 1
        mistakes += 1
        log(Ev.MISTAKE, i, d)
        _fx.tryEmit(Fx.Mistake(i, d, lives))
        if (lives <= 0) { status = Status.LOST; revision++; _fx.tryEmit(Fx.Lost) }
    }

    private fun checkEnd() {
        if (values.any { it == 0 }) return
        if (values.contentEquals(solution)) { status = Status.WON; armed = 0; revision++; _fx.tryEmit(Fx.Won) }
        else _fx.tryEmit(Fx.FilledWrong)
    }

    fun erase() {
        val i = selected
        if (i < 0 || isGiven(i) || status != Status.PLAYING || paused) return
        if (values[i] == 0 && notes[i] == 0) return
        push()
        values = values.copyOf().also { it[i] = 0 }
        notes = notes.copyOf().also { it[i] = 0 }
        log(Ev.ERASE, i)
        _fx.tryEmit(Fx.Erased(i))
    }

    fun undo() {
        if (status != Status.PLAYING || paused) return
        val s = undo.removeLastOrNull() ?: return
        values = s.values; notes = s.notes
        log(Ev.UNDO)
        _fx.tryEmit(Fx.Undone)
    }
    val canUndo get() = undo.isNotEmpty()

    private fun push() {
        undo.addLast(Snapshot(values, notes))
        if (undo.size > 2000) undo.removeFirst()
    }

    fun fillNotes() {
        if (status != Status.PLAYING || paused) return
        push()
        notes = IntArray(81) { if (values[it] == 0) candidatesOf(values, it) else 0 }
        log(Ev.FILL_NOTES)
    }

    // ── Auto-complete (B4) ───────────────────────────────────────────────────────────────────────

    /**
     * Available once every empty cell has exactly one number that fits (the Settings wording), and
     * nothing on the board is wrong. Earlier than that it would be solving the puzzle for you.
     */
    val autoCompleteAvailable: Boolean
        get() = status == Status.PLAYING && values.any { it == 0 } &&
            values.indices.all { values[it] == 0 || values[it] == solution[it] } &&
            values.indices.all { values[it] != 0 || Integer.bitCount(candidatesOf(values, it)) == 1 }

    /** True while the easy end is being filled for you, one cell at a time. */
    var autoFinishing by mutableStateOf(false)

    /**
     * Fills one remaining cell; the caller paces the calls. It walks to the empty cell nearest the
     * last one, so the finish travels across the board instead of jumping about.
     */
    fun autoCompleteStep(): Boolean {
        if (!autoCompleteAvailable) return false
        val from = selected.takeIf { it >= 0 } ?: 0
        val i = (0 until 81).filter { values[it] == 0 }.minByOrNull { Geo.euclid(it, from) * 100 + it } ?: return false
        val d = Integer.numberOfTrailingZeros(candidatesOf(values, i))
        selected = i
        place(i, d, Ev.AUTO)
        return true
    }

    // ── Hints (D5): four steps, from a nudge to the answer ─────────────────────────────────────

    fun showHint(next: HintView) {
        hint = next
        log(Ev.HINT, next.cell ?: -1, next.stage)
        _fx.tryEmit(Fx.HintShown(next.stage, next.cell))
        if (next.stage >= 3 && next.cell != null) selected = next.cell
    }

    fun clearHint() { hint = null }

    /** The easy-cell marker counts as a hint (Settings): the run goes to the assisted records, once. */
    var easyCell by mutableIntStateOf(-1)
    fun markAssisted() { if (!hinted && status == Status.PLAYING) log(Ev.HINT, -1, 0) }

    /** A hint's answer when the step only removes candidates: write the notes, then strike them. */
    fun applyEliminations(el: List<Pair<Int, Int>>) {
        if (status != Status.PLAYING) return
        push()
        val n = notes.copyOf()
        for ((c, d) in el) {
            if (values[c] != 0) continue
            if (n[c] == 0) n[c] = candidatesOf(values, c)
            n[c] = n[c] and (1 shl d).inv()
        }
        notes = n
        revision++
    }

    /** Restores state from the log on load (positions are re-derived, not stored separately). */
    fun restoreUndoFromValues() { /* undo history is not persisted across process death */ }
}

/** What the hint button currently shows. Stage 1 where to look, 2 the technique, 3 the cell, 4 the answer. */
data class HintView(
    val stage: Int,
    val unit: Int?,
    val cells: List<Int>,
    val technique: String,
    val cell: Int?,
    val digit: Int?,
    val eliminations: List<Pair<Int, Int>> = emptyList(),
)

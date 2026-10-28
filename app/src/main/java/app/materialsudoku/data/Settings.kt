package app.materialsudoku.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.materialsudoku.ui.theme.Seed
import app.materialsudoku.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Input { CELL, NUMBER, NOTES }
enum class Haptics { OFF, SOME, ALL }
enum class Motion { SYSTEM, REDUCED, FULL }

/** Every decided setting (DECISIONS.md; Settings board A). Defaults are the decided defaults. */
data class Settings(
    /** Difficulties shown on Home, bit k = difficulty k. Easy, Medium, Hard on by default (D2). */
    val difficulties: Int = 0b000111,
    val impossible: Boolean = false,
    val flagMistakes: Boolean = true,
    val lives: Int = 3,
    /** 0 Off · 1 Digit · 2 Lines · 3 Notes · 4 Blocked. */
    val highlight: Int = 3,
    val fillNotesButton: Boolean = true,
    val autoRemoveNotes: Boolean = true,
    val autoCompleteAuto: Boolean = false,
    val hints: Boolean = true,
    val easyCellMarker: Boolean = false,
    val input: Input = Input.CELL,
    val showTimer: Boolean = true,
    /** Once per game at half the empty cells: pace, or a word of encouragement. */
    val halfwayMessage: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    /** null = wallpaper colour. */
    val seed: Seed? = null,
    val arabicNumerals: Boolean = false,
    val motion: Motion = Motion.SYSTEM,
    val haptics: Haptics = Haptics.ALL,
    /** The name under the launcher icon: false "Sudoku", true "数独". */
    val kanjiName: Boolean = false,
    // Home's last picks.
    val lastDifficulty: Int = 0,
    val lastLength: Int = 1,
) {
    fun difficultyOn(k: Int) = if (k == 6) impossible else (difficulties shr k) and 1 == 1

    /**
     * Record band (SOLVE-CONDITIONS §5): 0 Little help, 1 Some help. Only what does the thinking for
     * you counts (user, 2026-10-27): highlighting up to Notes, tidy notes and auto-finish don't;
     * Blocked highlighting does part of the scanning. Hints and fill-all-notes are not a band: either
     * one sends that game to the assisted records (Game.assisted).
     */
    val band: Int get() = if (highlight >= 4) 1 else 0
}

private val Context.store by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val diffs = intPreferencesKey("difficulties")
        val imp = booleanPreferencesKey("impossible")
        val flag = booleanPreferencesKey("flag_mistakes")
        val lives = intPreferencesKey("lives")
        val hl = intPreferencesKey("highlight")
        val fill = booleanPreferencesKey("fill_notes_button")
        val autorm = booleanPreferencesKey("auto_remove_notes")
        val autoc = booleanPreferencesKey("auto_complete_auto")
        val hints = booleanPreferencesKey("hints")
        val easy = booleanPreferencesKey("easy_cell_marker")
        val input = stringPreferencesKey("input")
        val timer = booleanPreferencesKey("show_timer")
        val halfway = booleanPreferencesKey("halfway_message")
        val theme = stringPreferencesKey("theme")
        val amoled = booleanPreferencesKey("amoled")
        val seed = stringPreferencesKey("seed")
        val arabic = booleanPreferencesKey("arabic_numerals")
        val motion = stringPreferencesKey("motion")
        val haptics = stringPreferencesKey("haptics")
        val kanji = booleanPreferencesKey("kanji_name")
        val lastD = intPreferencesKey("last_difficulty")
        val lastL = intPreferencesKey("last_length")
    }

    val flow: Flow<Settings> = context.store.data.map { p -> read(p) }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            difficulties = p[K.diffs] ?: d.difficulties,
            impossible = p[K.imp] ?: d.impossible,
            flagMistakes = p[K.flag] ?: d.flagMistakes,
            lives = p[K.lives] ?: d.lives,
            highlight = p[K.hl] ?: d.highlight,
            fillNotesButton = p[K.fill] ?: d.fillNotesButton,
            autoRemoveNotes = p[K.autorm] ?: d.autoRemoveNotes,
            autoCompleteAuto = p[K.autoc] ?: d.autoCompleteAuto,
            hints = p[K.hints] ?: d.hints,
            easyCellMarker = p[K.easy] ?: d.easyCellMarker,
            input = p[K.input]?.let { runCatching { Input.valueOf(it) }.getOrNull() } ?: d.input,
            showTimer = p[K.timer] ?: d.showTimer,
            halfwayMessage = p[K.halfway] ?: d.halfwayMessage,
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            amoled = p[K.amoled] ?: d.amoled,
            seed = p[K.seed]?.let { s -> Seed.entries.firstOrNull { it.name == s } },
            arabicNumerals = p[K.arabic] ?: d.arabicNumerals,
            motion = p[K.motion]?.let { runCatching { Motion.valueOf(it) }.getOrNull() } ?: d.motion,
            haptics = p[K.haptics]?.let { runCatching { Haptics.valueOf(it) }.getOrNull() } ?: d.haptics,
            kanjiName = p[K.kanji] ?: d.kanjiName,
            lastDifficulty = p[K.lastD] ?: d.lastDifficulty,
            lastLength = p[K.lastL] ?: d.lastLength,
        )
    }

    suspend fun update(f: (Settings) -> Settings) {
        context.store.edit { p ->
            val s = f(read(p))
            p[K.diffs] = s.difficulties; p[K.imp] = s.impossible
            p[K.flag] = s.flagMistakes; p[K.lives] = s.lives
            p[K.hl] = s.highlight; p[K.fill] = s.fillNotesButton; p[K.autorm] = s.autoRemoveNotes
            p[K.autoc] = s.autoCompleteAuto; p[K.hints] = s.hints; p[K.easy] = s.easyCellMarker
            p[K.input] = s.input.name; p[K.timer] = s.showTimer; p[K.halfway] = s.halfwayMessage
            p[K.theme] = s.theme.name; p[K.amoled] = s.amoled
            if (s.seed == null) p.remove(K.seed) else p[K.seed] = s.seed.name
            p[K.arabic] = s.arabicNumerals; p[K.motion] = s.motion.name; p[K.haptics] = s.haptics.name
            p[K.kanji] = s.kanjiName
            p[K.lastD] = s.lastDifficulty; p[K.lastL] = s.lastLength
        }
    }
}

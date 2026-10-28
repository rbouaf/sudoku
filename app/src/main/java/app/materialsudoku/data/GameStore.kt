package app.materialsudoku.data

import android.content.Context
import app.materialsudoku.game.Deal
import app.materialsudoku.game.Ev
import app.materialsudoku.game.Game
import app.materialsudoku.game.Rules
import app.materialsudoku.game.Status
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Every game is one JSON file in `filesDir/games/`: the deal, the rules, and the full event log.
 * The log is the record (02 §6); every stat is recomputed from it, so stats added later still see
 * every game ever played. Files are written whole, through a temp file and a rename, so a crash
 * mid-write never leaves half a game.
 */
class GameStore(context: Context) {
    private val dir = File(context.filesDir, "games").apply { mkdirs() }

    fun save(g: Game) {
        val o = JSONObject()
            .put("v", 1)
            .put("id", g.id)
            .put("givens", g.deal.givens)
            .put("solution", g.deal.solution)
            .put("difficulty", g.deal.difficulty)
            .put("length", g.deal.length)
            .put("seed", g.deal.seed)
            .put("generator", g.deal.generatorVersion)
            .put("hardest", g.deal.hardest ?: JSONObject.NULL)
            .put("famous", g.deal.famous ?: JSONObject.NULL)
            .put("flag", g.rules.flagMistakes)
            .put("lives", g.rules.lives)
            .put("autorm", g.rules.autoRemoveNotes)
            .put("band", g.band)
            .put("startedAt", g.startedAt)
            .put("status", g.status.name)
            .put("elapsed", g.elapsed)
            .put("livesLeft", g.lives)
            .put("values", g.values.joinToString(""))
            .put("notes", JSONArray(g.notes.toList()))
            .put("events", JSONArray().apply { g.events.forEach { put(JSONArray(listOf(it.t, it.kind.toString(), it.cell, it.digit))) } })
        val f = File(dir, "${g.id}.json")
        val tmp = File(dir, "${g.id}.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(f)
    }

    private val pointer = File(dir, "current.txt")

    /** The game in progress, read before the first frame so Home's slot never swaps at launch. */
    fun loadCurrent(): Game? = runCatching { pointer.readText().trim() }.getOrNull()?.let { load(it) }?.takeIf { it.status == Status.PLAYING }
    fun setCurrent(id: String?) { if (id == null) pointer.delete() else pointer.writeText(id) }

    fun load(id: String): Game? = runCatching { parse(File(dir, "$id.json").readText()) }.getOrNull()

    fun delete(id: String) { File(dir, "$id.json").delete() }

    fun all(): List<Game> = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        .mapNotNull { runCatching { parse(it.readText()) }.getOrNull() }
        .sortedBy { it.startedAt }

    /** Every game file, verbatim, as one JSON array. */
    fun exportAll(): String = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
        .joinToString(",\n", "[\n", "\n]") { it.readText() }

    private fun parse(text: String): Game {
        val o = JSONObject(text)
        val deal = Deal(
            givens = o.getString("givens"), solution = o.getString("solution"),
            difficulty = o.getInt("difficulty"), length = o.getInt("length"),
            seed = o.getLong("seed"), generatorVersion = o.getInt("generator"),
            hardest = o.optString("hardest").takeIf { it.isNotEmpty() && it != "null" },
            famous = o.optString("famous").takeIf { it.isNotEmpty() && it != "null" },
        )
        val rules = Rules(o.getBoolean("flag"), o.getInt("lives"), o.optBoolean("autorm", true))
        val ev = o.getJSONArray("events")
        val events = MutableList(ev.length()) { k ->
            val a = ev.getJSONArray(k)
            Ev(a.getLong(0), a.getString(1)[0], a.getInt(2), a.getInt(3))
        }
        val notesA = o.getJSONArray("notes")
        return Game(
            id = o.getString("id"), deal = deal, rules = rules,
            values = IntArray(81) { o.getString("values")[it] - '0' },
            notes = IntArray(81) { notesA.getInt(it) },
            lives = o.getInt("livesLeft"), elapsed = o.getLong("elapsed"),
            events = events, status = Status.valueOf(o.getString("status")),
            startedAt = o.getLong("startedAt"), band = o.optInt("band", 0),
        )
    }
}

package app.materialsudoku.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * "App name on your phone": the launcher shows whichever of two activity-aliases is enabled
 * (`.LauncherSudoku` "Sudoku", `.LauncherKanji` "数独"). The full name stays "Material Sudoku".
 *
 * Enable the new alias before disabling the old one, so there is never a moment with no launcher
 * entry, and pass DONT_KILL_APP so the switch doesn't end the session it was made from. Some
 * launchers drop a home-screen shortcut when its component is disabled; the app stays in All apps.
 */
object LauncherName {
    fun apply(context: Context, kanji: Boolean) {
        val pm = context.packageManager
        val on = ComponentName(context, "app.materialsudoku." + if (kanji) "LauncherKanji" else "LauncherSudoku")
        val off = ComponentName(context, "app.materialsudoku." + if (kanji) "LauncherSudoku" else "LauncherKanji")
        if (pm.getComponentEnabledSetting(on) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        if (pm.getComponentEnabledSetting(off) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}

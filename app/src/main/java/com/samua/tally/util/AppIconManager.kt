package com.samua.tally.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

data class AppIconChoice(val key: String, val title: String, val subtitle: String, val alias: String)

val appIconChoices = listOf(
    AppIconChoice("ClassicBlue", "Classic Blue", "The original bright Tally icon", "ClassicBlueIcon"),
    AppIconChoice("NeonDark", "Neon Dark", "Deep black with electric blue", "NeonDarkIcon"),
    AppIconChoice("Glass", "Glass", "Transparent cyan glass highlights", "GlassIcon"),
    AppIconChoice("Pearl", "Pearl", "Matte ivory with dark details", "PearlIcon"),
    AppIconChoice("Amber", "Amber", "Warm orange and gold", "AmberIcon"),
    AppIconChoice("TechGreen", "Tech Green", "Dark technical green", "TechGreenIcon"),
    AppIconChoice("CosmicPurple", "Cosmic Purple", "Deep violet and pink", "CosmicPurpleIcon"),
    AppIconChoice("Synthwave", "Synthwave", "Pink, blue, and violet", "SynthwaveIcon")
)

object AppIconManager {
    fun apply(context: Context, selectedKey: String) {
        val packageManager = context.packageManager
        val ordered = appIconChoices.sortedByDescending { it.key == selectedKey }
        ordered.forEach { choice ->
            val component = ComponentName(context.packageName, "${context.packageName}.${choice.alias}")
            val state = if (choice.key == selectedKey) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            runCatching {
                packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}

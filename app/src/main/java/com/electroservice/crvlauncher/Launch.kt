package com.electroservice.crvlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** Un acceso del inicio: qué app abre y qué paquetes probar si el usuario no eligió ninguno. */
data class Slot(
    val key: String,
    val label: String,
    val candidates: List<String> = emptyList(),
    val fallback: (() -> Intent?)? = null,
    val labelHints: List<String> = emptyList() // busca apps instaladas cuyo nombre contenga esto
)

object Launch {
    private const val PREFS = "slots"

    // Accesos de la fila inferior (en el mismo orden que la imagen base)
    // Spotify y Waze se usan por Android Auto, que proyecta Zlink: ambos accesos abren Zlink
    val SPOTIFY = Slot("spotify", "Spotify", listOf("com.spotify.music"), labelHints = listOf("zlink"))
    val NAV = Slot(
        "nav", "Navegador",
        listOf("com.waze", "com.google.android.apps.maps", "com.here.app.maps"),
        { Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")) },
        labelHints = listOf("zlink")
    )
    val RADIO = Slot(
        "radio", "Radio",
        listOf("com.syu.radio", "com.microntek.radio", "com.android.fmradio", "com.mediatek.fmradio")
    )
    val MEDIA = Slot(
        "media", "Multimedia",
        listOf("com.syu.music", "com.microntek.music", "com.android.music", "com.google.android.apps.youtube.music"),
        { Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC) }
    )
    val SETTINGS = Slot("settings", "Ajustes", emptyList(), { Intent(Settings.ACTION_SETTINGS) })

    // Tarjetas grandes (sin app por defecto: se asignan con pulsación larga)
    val CAR = Slot("car", "Auto (ajustes del vehículo)")
    val CAM = Slot("cam", "Cámara / 360")

    fun savedPackage(ctx: Context, slot: Slot): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(slot.key, null)

    fun savePackage(ctx: Context, key: String, pkg: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, pkg).apply()
    }

    private fun intentFor(ctx: Context, pkg: String): Intent? =
        ctx.packageManager.getLaunchIntentForPackage(pkg)

    private fun findByLabel(ctx: Context, hints: List<String>): Intent? {
        if (hints.isEmpty()) return null
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(main, 0).firstOrNull { ri ->
            val name = ri.loadLabel(pm).toString().lowercase()
            val pkg = ri.activityInfo.packageName.lowercase()
            hints.any { name.contains(it) || pkg.contains(it) }
        } ?: return null
        return intentFor(ctx, match.activityInfo.packageName)
    }

    fun run(ctx: Context, slot: Slot) {
        val intent: Intent? =
            savedPackage(ctx, slot)?.let { intentFor(ctx, it) }
                ?: findByLabel(ctx, slot.labelHints)
                ?: slot.candidates.firstNotNullOfOrNull { intentFor(ctx, it) }
                ?: slot.fallback?.invoke()

        if (intent == null) {
            Toast.makeText(ctx, "Mantené presionado para elegir la app de ${slot.label}", Toast.LENGTH_LONG).show()
            return
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "No se pudo abrir ${slot.label}. Mantené presionado para elegir otra app.", Toast.LENGTH_LONG).show()
        }
    }

    fun pick(ctx: Context, slot: Slot) {
        ctx.startActivity(
            Intent(ctx, AppsActivity::class.java)
                .putExtra(AppsActivity.EXTRA_KEY, slot.key)
                .putExtra(AppsActivity.EXTRA_LABEL, slot.label)
        )
    }
}

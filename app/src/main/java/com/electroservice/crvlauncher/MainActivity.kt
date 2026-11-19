package com.electroservice.crvlauncher

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var splash: View? = null
    private val splashRunnable = Runnable { hideSplash() }

    // --- Modo noche (luces encendidas) ---
    private var lastNight: Boolean? = null
    private val pollRunnable = object : Runnable {
        override fun run() { applyNight(); handler.postDelayed(this, 4000) }
    }
    private val brightnessObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { applyNight() }
    }

    private data class Tile(val slot: Slot?, val label: String, val icon: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) showSplash()

        // Tarjetas grandes: toque = abrir, pulsación larga = elegir app
        findViewById<android.view.View>(R.id.cardFront).apply {
            setOnClickListener { Launch.run(this@MainActivity, Launch.CAR) }
            setOnLongClickListener { Launch.pick(this@MainActivity, Launch.CAR); true }
        }
        findViewById<android.view.View>(R.id.cardRear).apply {
            setOnClickListener { Launch.run(this@MainActivity, Launch.CAM) }
            setOnLongClickListener { Launch.pick(this@MainActivity, Launch.CAM); true }
        }

        // Diagnóstico: mantené presionada la hora para ver brillo, umbral y modo noche
        findViewById<View>(R.id.clock).setOnLongClickListener {
            Toast.makeText(this,
                "Brillo: ${brightness()}/255 · Umbral: ${threshold()} · Modo noche del sistema: ${if (uiNightMode()) "sí" else "no"} · Imagen: ${if (computeNight()) "noche" else "día"}",
                Toast.LENGTH_LONG).show()
            true
        }
        // Calibrar: con las luces encendidas, mantené presionada la temperatura
        findViewById<View>(R.id.temp).setOnLongClickListener {
            val t = brightness() + 10
            getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("night_threshold", t).apply()
            lastNight = null; applyNight()
            Toast.makeText(this, "Listo: se usa la imagen de noche cuando el brillo sea ≤ $t", Toast.LENGTH_LONG).show()
            true
        }

        // Fila inferior, en el orden de la imagen base
        val tiles = listOf(
            Tile(Launch.SPOTIFY, "Spotify", R.drawable.ic_spotify),
            Tile(Launch.NAV, "Navegador", R.drawable.ic_nav),
            Tile(Launch.RADIO, "Radio", R.drawable.ic_radio),
            Tile(Launch.MEDIA, "Multimedia", R.drawable.ic_media),
            Tile(Launch.SETTINGS, "Ajustes", R.drawable.ic_settings),
            Tile(null, "Aplicaciones", R.drawable.ic_apps) // cajón de apps propio
        )

        val row = findViewById<LinearLayout>(R.id.row)
        val inflater = LayoutInflater.from(this)
        for (t in tiles) {
            val v = inflater.inflate(R.layout.item_tile, row, false)
            v.findViewById<ImageView>(R.id.icon).setImageResource(t.icon)
            v.findViewById<TextView>(R.id.label).text = t.label
            if (t.slot != null) {
                v.setOnClickListener { Launch.run(this, t.slot) }
                v.setOnLongClickListener { Launch.pick(this, t.slot); true }
            } else {
                v.setOnClickListener { startActivity(Intent(this, AppsActivity::class.java)) }
            }
            row.addView(v)
        }
    }

    /** Pantalla de bienvenida: logo Honda + "CR-V", luego aparece el inicio. Solo al arrancar. */
    private fun showSplash() {
        val v = LayoutInflater.from(this).inflate(R.layout.splash, null)
        splash = v
        addContentView(v, android.view.ViewGroup.LayoutParams(-1, -1))
        v.setOnClickListener { hideSplash() } // un toque la salta
        v.findViewById<View>(R.id.splashLogo).animate().alpha(1f).setDuration(700).start()
        v.findViewById<View>(R.id.splashText).animate().alpha(1f).setStartDelay(500).setDuration(700).start()
        handler.postDelayed(splashRunnable, 2800)
    }

    private fun hideSplash() {
        val v = splash ?: return
        splash = null
        handler.removeCallbacks(splashRunnable)
        v.animate().alpha(0f).setDuration(600).withEndAction {
            (v.parent as? android.view.ViewGroup)?.removeView(v)
        }.start()
    }

    private fun brightness(): Int = try {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
    } catch (e: Exception) { 255 }

    private fun uiNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun threshold(): Int = getSharedPreferences("settings", MODE_PRIVATE).getInt("night_threshold", 128)

    /** Noche = la unidad entró en modo nocturno O el brillo bajó (luces encendidas). */
    private fun computeNight(): Boolean = uiNightMode() || brightness() <= threshold()

    private fun applyNight() {
        val night = computeNight()
        if (night == lastNight) return
        val first = lastNight == null
        lastNight = night
        swap(R.id.imgFront, if (night) R.drawable.img_car_front_night else R.drawable.img_car_front, first)
        swap(R.id.imgRear, if (night) R.drawable.img_car_rear_night else R.drawable.img_car_rear, first)
    }

    private fun swap(id: Int, res: Int, instant: Boolean) {
        val iv = findViewById<ImageView>(id)
        if (instant) { iv.setImageResource(res); return }
        iv.animate().alpha(0f).setDuration(250).withEndAction {
            iv.setImageResource(res)
            iv.animate().alpha(1f).setDuration(400).start()
        }.start()
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, brightnessObserver)
        handler.removeCallbacks(pollRunnable)
        pollRunnable.run()
    }

    override fun onPause() {
        contentResolver.unregisterContentObserver(brightnessObserver)
        handler.removeCallbacks(pollRunnable)
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyNight()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // Un launcher no debe cerrarse con "atrás"
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}
}

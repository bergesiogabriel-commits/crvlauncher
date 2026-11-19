package com.electroservice.crvlauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/** Cajón de aplicaciones. También sirve de selector cuando se abre con EXTRA_KEY. */
class AppsActivity : Activity() {

    companion object {
        const val EXTRA_KEY = "assign_key"
        const val EXTRA_LABEL = "assign_label"
    }

    private data class Entry(val label: String, val pkg: String, val icon: Drawable, val intent: Intent)

    private var assignKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        assignKey = intent.getStringExtra(EXTRA_KEY)
        val assignLabel = intent.getStringExtra(EXTRA_LABEL)
        if (assignKey != null) {
            findViewById<TextView>(R.id.title).text = "Elegí la app para: $assignLabel"
        }

        val grid = findViewById<GridView>(R.id.grid)
        Thread {
            val pm = packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list: List<ResolveInfo> = pm.queryIntentActivities(main, 0)
            val entries = list
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    val pkg = it.activityInfo.packageName
                    Entry(
                        it.loadLabel(pm).toString(), pkg, it.loadIcon(pm),
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(pkg, it.activityInfo.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                .sortedBy { it.label.lowercase() }

            runOnUiThread {
                grid.adapter = object : BaseAdapter() {
                    override fun getCount() = entries.size
                    override fun getItem(p: Int) = entries[p]
                    override fun getItemId(p: Int) = p.toLong()
                    override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
                        val v = convert ?: LayoutInflater.from(this@AppsActivity)
                            .inflate(R.layout.item_app, parent, false)
                        v.findViewById<ImageView>(R.id.appIcon).setImageDrawable(entries[p].icon)
                        v.findViewById<TextView>(R.id.appLabel).text = entries[p].label
                        return v
                    }
                }
                grid.setOnItemClickListener { _, _, pos, _ ->
                    val e = entries[pos]
                    val key = assignKey
                    if (key != null) {
                        Launch.savePackage(this, key, e.pkg)
                        Toast.makeText(this, "Asignada: ${e.label}", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        try { startActivity(e.intent) } catch (ex: Exception) {
                            Toast.makeText(this, "No se pudo abrir ${e.label}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                // Pulsación larga = mostrar el nombre de paquete (útil para saber cuál es cuál)
                grid.setOnItemLongClickListener { _, _, pos, _ ->
                    Toast.makeText(this, entries[pos].pkg, Toast.LENGTH_LONG).show()
                    true
                }
            }
        }.start()
    }
}

package com.a7a.cards

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {

    private lateinit var cardsBox: LinearLayout
    private val prefs by lazy { getSharedPreferences("cards", MODE_PRIVATE) }

    private data class Network(val name: String, val prefix: String)
    private val networks = listOf(
        Network("Orange", "#102*"),
        Network("Vodafone", "*858*"),
        Network("WE", "*555*"),
        Network("e& Egypt", "*556*")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 18)
        }

        val title = TextView(this).apply {
            text = "A7A Cards"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "كروتك ولوحة الاتصال في مكان واحد"
            textSize = 15f
        }
        root.addView(subtitle)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val cardsTab = Button(this).apply { text = "💳 الكروت" }
        val dialTab = Button(this).apply { text = "📞 الاتصال" }
        tabs.addView(cardsTab, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(dialTab, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(tabs)

        cardsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply { addView(cardsBox) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        cardsTab.setOnClickListener { showCards() }
        dialTab.setOnClickListener { showDialer() }

        setContentView(root)
        showCards()
    }

    private fun showCards() {
        cardsBox.removeAllViews()

        val add = Button(this).apply {
            text = "➕ إضافة كارت"
            setOnClickListener { addCardDialog() }
        }
        cardsBox.addView(add)

        val paste = Button(this).apply {
            text = "📋 لصق رسالة واستخراج رقم الكارت"
            setOnClickListener { pasteDialog() }
        }
        cardsBox.addView(paste)

        val saved = prefs.getStringSet("list", emptySet())!!.toList()
        if (saved.isEmpty()) {
            cardsBox.addView(TextView(this).apply {
                text = "\nلا توجد كروت محفوظة حتى الآن."
                textSize = 17f
            })
            return
        }

        saved.forEachIndexed { index, item ->
            val parts = item.split("|", limit = 3)
            if (parts.size < 3) return@forEachIndexed
            val network = parts[0]
            val code = parts[1]
            val used = parts[2] == "1"

            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 18, 8, 8)
            }
            box.addView(TextView(this).apply {
                text = "${index + 1}. $network\n$code\nالحالة: ${if (used) "مستخدم" else "غير مستخدم"}"
                textSize = 17f
            })

            val buttons = LinearLayout(this)
            val charge = Button(this).apply {
                text = "⚡ اشحن الكارت"
                isEnabled = !used
                setOnClickListener {
                    dial(buildCode(network, code))
                    markUsed(item)
                }
            }
            val copy = Button(this).apply {
                text = "نسخ"
                setOnClickListener {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("كود الشحن", buildCode(network, code)))
                    Toast.makeText(this@MainActivity, "تم نسخ كود الشحن", Toast.LENGTH_SHORT).show()
                }
            }
            val delete = Button(this).apply {
                text = "حذف"
                setOnClickListener {
                    prefs.edit().putStringSet("list", saved.filter { it != item }.toSet()).apply()
                    showCards()
                }
            }
            buttons.addView(charge, LinearLayout.LayoutParams(0, -2, 1f))
            buttons.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            buttons.addView(delete, LinearLayout.LayoutParams(0, -2, 1f))
            box.addView(buttons)
            cardsBox.addView(box)
        }
    }

    private fun addCardDialog(prefill: String? = null) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 10, 30, 0)
        }
        val code = EditText(this).apply {
            hint = "رقم الكارت"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefill ?: "")
        }
        layout.addView(code)

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            networks.map { it.name })
        layout.addView(spinner)

        AlertDialog.Builder(this)
            .setTitle("إضافة كارت")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val clean = code.text.toString().filter { it.isDigit() }
                if (clean.isEmpty()) {
                    Toast.makeText(this, "اكتب رقم الكارت", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val network = networks[spinner.selectedItemPosition].name
                val current = prefs.getStringSet("list", emptySet())!!.toMutableSet()
                current.add("$network|$clean|0")
                prefs.edit().putStringSet("list", current).apply()
                showCards()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun pasteDialog() {
        val input = EditText(this).apply {
            hint = "الصق رسالة الهدية هنا"
            minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle("استخراج رقم الكارت")
            .setView(input)
            .setPositiveButton("استخراج") { _, _ ->
                val text = input.text.toString()
                val m = Pattern.compile("\\b\\d{12,20}\\b").matcher(text)
                if (m.find()) addCardDialog(m.group())
                else Toast.makeText(this, "لم أجد رقم كارت واضحًا", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun buildCode(network: String, code: String): String {
        val n = networks.firstOrNull { it.name == network } ?: return code
        return n.prefix + code + "#"
    }

    private fun markUsed(item: String) {
        val current = prefs.getStringSet("list", emptySet())!!.toMutableSet()
        current.remove(item)
        current.add(item.substringBeforeLast("|") + "|1")
        prefs.edit().putStringSet("list", current).apply()
        showCards()
    }

    private fun dial(number: String) {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            pendingDial = number
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 100)
            return
        }
        startActivity(Intent(Intent.ACTION_CALL, uri))
    }

    private var pendingDial: String? = null

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingDial?.let { dial(it) }
            pendingDial = null
        }
    }

    private fun showDialer() {
        cardsBox.removeAllViews()
        val display = EditText(this).apply {
            textSize = 26f
            gravity = android.view.Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        cardsBox.addView(display)

        val grid = GridLayout(this).apply {
            columnCount = 3
        }
        val keys = listOf("1","2","3","4","5","6","7","8","9","*","0","#")
        keys.forEach { key ->
            grid.addView(Button(this).apply {
                text = key
                textSize = 22f
                setOnClickListener { display.append(key) }
            }, GridLayout.LayoutParams().apply {
                width = 0
                height = 130
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        cardsBox.addView(grid)

        val call = Button(this).apply {
            text = "📞 اتصال"
            setOnClickListener {
                val number = display.text.toString()
                if (number.isNotBlank()) dial(number)
            }
        }
        cardsBox.addView(call)
    }
}

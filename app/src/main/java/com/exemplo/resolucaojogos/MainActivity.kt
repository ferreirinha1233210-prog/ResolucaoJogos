package com.exemplo.resolucaojogos

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("jogos", Context.MODE_PRIVATE) }

    // Porcentagens da resolução original. 100 = original (sem redução).
    private val niveis = listOf(100, 80, 70, 60, 50, 40, 30)

    private lateinit var statusText: TextView
    private lateinit var permButton: Button
    private lateinit var listaLayout: LinearLayout

    private var larguraTela = 720
    private var larguraLida = false

    private val binderRecebido = Shizuku.OnBinderReceivedListener { runOnUiThread { atualizarStatus() } }
    private val binderMorto = Shizuku.OnBinderDeadListener { runOnUiThread { atualizarStatus() } }
    private val resultadoPermissao =
        Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { atualizarStatus() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        montarTela()
        Shizuku.addBinderReceivedListenerSticky(binderRecebido)
        Shizuku.addBinderDeadListener(binderMorto)
        Shizuku.addRequestPermissionResultListener(resultadoPermissao)
        atualizarStatus()
    }

    override fun onResume() {
        super.onResume()
        atualizarStatus()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderRecebido)
        Shizuku.removeBinderDeadListener(binderMorto)
        Shizuku.removeRequestPermissionResultListener(resultadoPermissao)
        super.onDestroy()
    }

    // ---------- Tela ----------

    private fun montarTela() {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }
        val titulo = TextView(this).apply {
            text = "Resolução dos Jogos"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(8), 0, dp(8))
        }
        permButton = Button(this).apply {
            text = "Conceder permissão do Shizuku"
            visibility = View.GONE
            setOnClickListener {
                if (Shizuku.pingBinder()) Shizuku.requestPermission(1001)
            }
        }
        val addButton = Button(this).apply {
            text = "+ Adicionar jogo"
            setOnClickListener { escolherApp() }
        }
        listaLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        raiz.addView(titulo)
        raiz.addView(statusText)
        raiz.addView(permButton)
        raiz.addView(addButton)
        raiz.addView(listaLayout)
        setContentView(ScrollView(this).apply { addView(raiz) })
    }

    private fun atualizarStatus() {
        if (!Shizuku.pingBinder()) {
            statusText.text = "Shizuku não está rodando. Abra o app Shizuku e inicie o serviço."
            permButton.visibility = View.GONE
        } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Shizuku rodando, mas falta permissão para este app."
            permButton.visibility = View.VISIBLE
        } else {
            statusText.text = "Shizuku OK ✔  (largura da tela: ${larguraTela}px)"
            permButton.visibility = View.GONE
            if (!larguraLida) {
                larguraLida = true
                lerLargura()
            }
        }
        montarLista()
    }

    private fun montarLista() {
        listaLayout.removeAllViews()
        val pm = packageManager
        val pacotes = prefs.getStringSet("pacotes", emptySet())!!.toList().sorted()
        if (pacotes.isEmpty()) {
            listaLayout.addView(TextView(this).apply {
                text = "Nenhum jogo ainda. Toque em \"+ Adicionar jogo\"."
                setPadding(0, dp(16), 0, 0)
            })
            return
        }
        for (pkg in pacotes) {
            val info = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            } ?: continue
            val nome = info.loadLabel(pm).toString()
            val icone = info.loadIcon(pm)
            val pct = prefs.getInt("nivel_$pkg", 100)

            val linha = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(12), dp(8), dp(12))
                val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                background = a.getDrawable(0)
                a.recycle()
                setOnClickListener { mostrarOpcoes(pkg, nome) }
            }
            val img = ImageView(this).apply { setImageDrawable(icone) }
            linha.addView(img, LinearLayout.LayoutParams(dp(48), dp(48)))

            val textos = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, 0, 0)
            }
            textos.addView(TextView(this).apply {
                text = nome
                textSize = 17f
            })
            textos.addView(TextView(this).apply {
                text = "Resolução: ${rotulo(pct)}"
                textSize = 13f
            })
            linha.addView(textos)
            listaLayout.addView(linha)
        }
    }

    // ---------- Ações ----------

    private fun escolherApp() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .sortedBy { it.second.lowercase() }
        val nomes = apps.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Escolha o jogo")
            .setItems(nomes) { _, i -> adicionar(apps[i].first) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun adicionar(pkg: String) {
        val novo = prefs.getStringSet("pacotes", emptySet())!!.toMutableSet()
        novo.add(pkg)
        prefs.edit().putStringSet("pacotes", novo).apply()
        montarLista()
    }

    private fun remover(pkg: String) {
        if (prefs.getInt("nivel_$pkg", 100) != 100 && shizukuPronto()) {
            Thread { executar("cmd game reset $pkg") }.start()
        }
        val novo = prefs.getStringSet("pacotes", emptySet())!!.toMutableSet()
        novo.remove(pkg)
        prefs.edit().putStringSet("pacotes", novo).remove("nivel_$pkg").apply()
        montarLista()
    }

    private fun mostrarOpcoes(pkg: String, nome: String) {
        val atual = prefs.getInt("nivel_$pkg", 100)
        val itens = niveis.map { (if (it == atual) "✔ " else "") + rotulo(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("$nome\nAtual: ${rotulo(atual)}")
            .setItems(itens) { _, i -> aplicar(pkg, niveis[i]) }
            .setNeutralButton("Remover da lista") { _, _ -> remover(pkg) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun aplicar(pkg: String, pct: Int) {
        if (!shizukuPronto()) {
            Toast.makeText(this, "Shizuku não está pronto", Toast.LENGTH_LONG).show()
            return
        }
        val comando = if (pct == 100) {
            "cmd game reset $pkg"
        } else {
            "cmd game set --mode performance --downscale 0.${pct / 10} $pkg && cmd game mode performance $pkg"
        }
        Thread {
            val saida = executar(comando)
            val falhou = saida.contains("ERRO") ||
                saida.contains("Exception", true) ||
                saida.contains("Unknown", true) ||
                saida.contains("Error", true)
            runOnUiThread {
                if (falhou) {
                    AlertDialog.Builder(this)
                        .setTitle("Não funcionou")
                        .setMessage(saida.ifBlank { "Sem resposta do sistema." })
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    prefs.edit().putInt("nivel_$pkg", pct).apply()
                    montarLista()
                    Toast.makeText(this, "Aplicado! Feche o jogo e abra de novo.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // ---------- Shizuku ----------

    private fun shizukuPronto(): Boolean =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    private fun lerLargura() {
        Thread {
            val saida = executar("wm size")
            val m = Regex("Physical size: (\\d+)x(\\d+)").find(saida)
            if (m != null) {
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                runOnUiThread {
                    larguraTela = minOf(a, b)
                    atualizarStatus()
                }
            }
        }.start()
    }

    // Roda um comando de shell com a permissão do Shizuku.
    private fun executar(comando: String): String {
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", comando), null, null) as Process
            val saida = p.inputStream.bufferedReader().readText()
            val erro = p.errorStream.bufferedReader().readText()
            p.waitFor()
            (saida + erro).trim()
        } catch (e: Exception) {
            "ERRO: ${e.cause?.message ?: e.message}"
        }
    }

    // ---------- Utilitários ----------

    private fun rotulo(pct: Int): String =
        if (pct == 100) "${larguraTela}p (original)" else "${larguraTela * pct / 100}p"

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

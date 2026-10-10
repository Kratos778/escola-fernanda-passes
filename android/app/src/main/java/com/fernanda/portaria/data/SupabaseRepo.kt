package com.fernanda.portaria.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.fernanda.portaria.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class SupabaseRepo(private val context: Context) {
    private val db = AppDatabase.get(context)
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://blfmtlselqhdpnowpuqj.supabase.co" },
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "sb_publishable_Qv-rc2IuTagnhnYnVhKeqA_UT8bv3uK" }
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    fun temInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun login(email: String, password: String) = runCatching {
        client.auth.signInWith(Email) { this.email = email; this.password = password }
    }

    suspend fun logout() = runCatching { client.auth.signOut() }
    fun sessaoAtiva() = client.auth.currentSessionOrNull() != null

    suspend fun registarLeitura(
        token: String, tipo: TipoMovimento, offline: Boolean = false,
        decisaoLocal: String? = null, eventoId: String = UUID.randomUUID().toString()
    ): RespostaLeitura {
        if (!temInternet() || offline) {
            db.eventoDao().inserir(EventoPendente(id = eventoId, token = token, tipo = tipo.name, decisaoLocal = decisaoLocal))
            return RespostaLeitura(
                ok = true,
                mensagem = if (tipo == TipoMovimento.saida) "Saida guardada offline" else "EVENTO GUARDADO OFFLINE",
                resultado = if (tipo == TipoMovimento.saida) "permitido" else "pendente_autorizacao",
                tipo = tipo.name, offline = true
            )
        }
        return runCatching {
            val params = buildJsonObject {
                put("p_evento_id", eventoId); put("p_token", token); put("p_tipo", tipo.name)
                put("p_ocorrido_em", Instant.now().toString()); put("p_offline", false)
                if (decisaoLocal != null) put("p_decisao_local", decisaoLocal)
            }
            parseResposta(client.postgrest.rpc("registar_leitura", params).data)
        }.getOrElse {
            db.eventoDao().inserir(EventoPendente(id = eventoId, token = token, tipo = tipo.name, decisaoLocal = decisaoLocal))
            RespostaLeitura(ok = true, mensagem = "EVENTO GUARDADO OFFLINE", resultado = "pendente_autorizacao", tipo = tipo.name, offline = true)
        }
    }

    suspend fun sincronizarPendentes(): Int {
        if (!temInternet() || !sessaoAtiva()) return 0
        var n = 0
        for (e in db.eventoDao().pendentes()) {
            runCatching {
                val params = buildJsonObject {
                    put("p_evento_id", e.id); put("p_token", e.token); put("p_tipo", e.tipo)
                    put("p_ocorrido_em", Instant.ofEpochMilli(e.ocorridoEm).toString()); put("p_offline", true)
                    e.decisaoLocal?.let { put("p_decisao_local", it) }
                }
                client.postgrest.rpc("registar_leitura", params)
                db.eventoDao().marcarSincronizado(e.id); n++
            }
        }
        return n
    }

    private fun parseResposta(raw: String): RespostaLeitura {
        fun g(k: String) = Regex("\"$k\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)
        return RespostaLeitura(
            ok = true, mensagem = g("mensagem") ?: "Resposta", resultado = g("resultado") ?: "",
            tipo = g("tipo") ?: "", alunoNome = g("nome"), alunoTurma = g("turma"), estadoPropina = g("estado_propina")
        )
    }
}

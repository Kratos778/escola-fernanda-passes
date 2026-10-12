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
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.hours

class SupabaseRepo(private val context: Context) {
    private val db = AppDatabase.get(context)
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://blfmtlselqhdpnowpuqj.supabase.co" },
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "sb_publishable_Qv-rc2IuTagnhnYnVhKeqA_UT8bv3uK" }
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
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
        token: String,
        tipo: TipoMovimento,
        offline: Boolean = false,
        decisaoLocal: String? = null,
        eventoId: String = UUID.randomUUID().toString()
    ): RespostaLeitura {
        if (!temInternet() || offline) {
            db.eventoDao().inserir(
                EventoPendente(id = eventoId, token = token, tipo = tipo.name, decisaoLocal = decisaoLocal)
            )
            return RespostaLeitura(
                ok = true,
                mensagem = if (tipo == TipoMovimento.saida) "SAIDA REGISTADA (OFFLINE)"
                else "EVENTO GUARDADO OFFLINE — VERIFICACAO PENDENTE",
                resultado = if (tipo == TipoMovimento.saida) "permitido" else "pendente_autorizacao",
                tipo = tipo.name,
                offline = true
            )
        }
        return runCatching {
            val params = buildJsonObject {
                put("p_evento_id", eventoId)
                put("p_token", token)
                put("p_tipo", tipo.name)
                put("p_ocorrido_em", Instant.now().toString())
                put("p_offline", false)
                if (decisaoLocal != null) put("p_decisao_local", decisaoLocal)
            }
            val r = parseResposta(client.postgrest.rpc("registar_leitura", params).data)
            r.copy(fotoPath = resolverFotoUrl(r.fotoPath))
        }.getOrElse {
            db.eventoDao().inserir(
                EventoPendente(id = eventoId, token = token, tipo = tipo.name, decisaoLocal = decisaoLocal)
            )
            RespostaLeitura(
                ok = true,
                mensagem = "EVENTO GUARDADO OFFLINE — VERIFICACAO PENDENTE",
                resultado = "pendente_autorizacao",
                tipo = tipo.name,
                offline = true
            )
        }
    }

    suspend fun sincronizarPendentes(): Int {
        if (!temInternet() || !sessaoAtiva()) return 0
        var n = 0
        for (e in db.eventoDao().pendentes()) {
            runCatching {
                val params = buildJsonObject {
                    put("p_evento_id", e.id)
                    put("p_token", e.token)
                    put("p_tipo", e.tipo)
                    put("p_ocorrido_em", Instant.ofEpochMilli(e.ocorridoEm).toString())
                    put("p_offline", true)
                    e.decisaoLocal?.let { put("p_decisao_local", it) }
                }
                client.postgrest.rpc("registar_leitura", params)
                db.eventoDao().marcarSincronizado(e.id)
                n++
            }
        }
        return n
    }

    suspend fun listarHistoricoServidor(limite: Int = 50): List<MovimentoItem> {
        if (!temInternet() || !sessaoAtiva()) return emptyList()
        return runCatching {
            val raw = client.postgrest.rpc(
                "listar_movimentos_recentes",
                buildJsonObject { put("p_limite", limite) }
            ).data
            parseListaMovimentos(raw)
        }.getOrElse { emptyList() }
    }

    suspend fun resolverFotoUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return runCatching {
            client.storage.from("fotos").createSignedUrl(path, 2.hours)
        }.getOrNull()
    }

    private fun parseListaMovimentos(raw: String): List<MovimentoItem> {
        val items = mutableListOf<MovimentoItem>()
        val objRegex = Regex("\\{[^{}]*\\}")
        for (m in objRegex.findAll(raw)) {
            val o = m.value
            fun g(k: String) = Regex("\"$k\"\\s*:\\s*\"([^\"]*)\"").find(o)?.groupValues?.get(1)
            fun gb(k: String) = Regex("\"$k\"\\s*:\\s*(true|false)").find(o)?.groupValues?.get(1) == "true"
            items.add(
                MovimentoItem(
                    id = g("id") ?: "",
                    tipo = g("tipo") ?: "",
                    resultado = g("resultado") ?: "",
                    offline = gb("offline"),
                    ocorridoEm = g("ocorrido_em") ?: "",
                    alunoNome = g("aluno_nome"),
                    alunoTurma = g("aluno_turma"),
                    alunoCurso = g("aluno_curso"),
                    alunoCodigo = g("aluno_codigo"),
                    numeroPasse = g("numero_passe"),
                    passeEstado = g("passe_estado"),
                    fotoPath = g("foto_path")
                )
            )
        }
        return items
    }

    private fun parseResposta(raw: String): RespostaLeitura {
        fun g(k: String): String? =
            Regex("\"$k\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)
        fun nested(parent: String, k: String): String? =
            Regex("\"$parent\"\\s*:\\s*\\{[^}]*\"$k\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)

        val res = g("resultado") ?: ""
        return RespostaLeitura(
            ok = true,
            mensagem = g("mensagem") ?: "Resposta",
            resultado = res,
            tipo = g("tipo") ?: "",
            alunoNome = nested("aluno", "nome") ?: g("nome"),
            alunoTurma = nested("aluno", "turma") ?: g("turma"),
            alunoCurso = nested("aluno", "curso"),
            alunoCodigo = nested("aluno", "codigo_id"),
            numeroPasse = nested("aluno", "numero_passe"),
            estadoPropina = g("estado_propina"),
            passeEstado = g("passe_estado"),
            vencimento = nested("aluno", "vencimento") ?: g("vencimento"),
            fotoPath = nested("aluno", "foto_path"),
            offline = raw.contains("\"offline\":true") || raw.contains("\"offline\": true"),
            repetido = res == "duplicado",
            ocorridoEm = g("ocorrido_em")
        )
    }
}

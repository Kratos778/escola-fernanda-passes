package com.fernanda.portaria.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** auto = servidor decide entrada/saida conforme ultimo movimento */
enum class TipoMovimento { auto, entrada, saida }

@Entity(tableName = "eventos_pendentes")
data class EventoPendente(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val token: String,
    val tipo: String,
    val ocorridoEm: Long = System.currentTimeMillis(),
    val decisaoLocal: String? = null,
    val sincronizado: Boolean = false
)

data class RespostaLeitura(
    val ok: Boolean = false,
    val mensagem: String = "",
    val resultado: String = "",
    val tipo: String = "",
    val alunoNome: String? = null,
    val alunoTurma: String? = null,
    val alunoCurso: String? = null,
    val alunoCodigo: String? = null,
    val numeroPasse: String? = null,
    val estadoPropina: String? = null,
    val passeEstado: String? = null,
    val vencimento: String? = null,
    val fotoPath: String? = null,
    val offline: Boolean = false,
    val repetido: Boolean = false,
    val ocorridoEm: String? = null
) {
    val mostrarAvisoPropina: Boolean
        get() = when (estadoPropina) {
            null, "em_dia" -> false
            else -> true
        }
}

data class MovimentoItem(
    val id: String = "",
    val tipo: String = "",
    val resultado: String = "",
    val offline: Boolean = false,
    val ocorridoEm: String = "",
    val alunoNome: String? = null,
    val alunoTurma: String? = null,
    val alunoCurso: String? = null,
    val alunoCodigo: String? = null,
    val numeroPasse: String? = null,
    val passeEstado: String? = null,
    val fotoPath: String? = null
)

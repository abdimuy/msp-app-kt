package com.example.msp_app.core.common.cobranza.domain.casos

import com.example.msp_app.core.common.cobranza.domain.CuentaDelPeriodo
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.PagoEnVentana
import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import com.example.msp_app.core.common.cobranza.domain.VisitaEnVentana
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Códigos snake_case del contrato (§1 `EstadoCuenta`) ↔ enum del teléfono. */
internal object CodigoEstado {
    private val porCodigo: Map<String, EstadoCuenta> =
        EstadoCuenta.entries.associateBy { it.name.lowercase(Locale.ROOT) }

    fun aEnum(codigo: String): EstadoCuenta =
        requireNotNull(porCodigo[codigo]) { "estado desconocido en el caso: \"$codigo\"" }

    fun aCodigo(estado: EstadoCuenta): String = estado.name.lowercase(Locale.ROOT)
}

internal data class Esperado(val ventaId: Int, val estado: EstadoCuenta, val conAlgo: Boolean)

/**
 * Un caso del juego compartido con msp-api (contratos de desempeño §7). El
 * parseo es ESTRICTO a propósito: una clave desconocida, una obligatoria
 * ausente o dinero como número JSON fallan con el nombre del problema, porque un
 * campo mal escrito que se leyera como `null` daría verde por casualidad.
 */
internal data class CasoDeEstadoCuenta(
    val archivo: String,
    val nombre: String,
    val ventana: VentanaCobro,
    val cuentas: List<CuentaDelPeriodo>,
    val pagos: List<PagoEnVentana>,
    val visitas: List<VisitaEnVentana>,
    val esperado: List<Esperado>
) {
    override fun toString(): String = "$archivo — $nombre"

    companion object {
        fun desdeJson(archivo: String, texto: String): CasoDeEstadoCuenta {
            val raiz = JsonParser.parseString(texto).asJsonObject
            raiz.soloClaves(archivo, "nombre", "ventana", "cuentas", "pagos", "visitas", "esperado")
            val caso = CasoDeEstadoCuenta(
                archivo = archivo,
                nombre = raiz.str(archivo, "nombre"),
                ventana = ventanaDe(archivo, raiz.obj(archivo, "ventana")),
                cuentas = raiz.arr(archivo, "cuentas").map { cuentaDe(archivo, it) },
                pagos = raiz.arr(archivo, "pagos").map { pagoDe(archivo, it) },
                visitas = raiz.arr(archivo, "visitas").map { visitaDe(archivo, it) },
                esperado = raiz.arr(archivo, "esperado").map { esperadoDe(archivo, it) }
            )
            val idsCuentas = caso.cuentas.map { it.ventaId }
            val idsEsperado = caso.esperado.map { it.ventaId }
            require(
                idsCuentas.size == idsCuentas.toSet().size
            ) { "$archivo: venta_id repetido en cuentas" }
            require(idsEsperado.sorted() == idsCuentas.sorted()) {
                "$archivo: esperado debe traer exactamente una entrada por cuenta; " +
                    "cuentas=$idsCuentas esperado=$idsEsperado"
            }
            return caso
        }

        private fun ventanaDe(archivo: String, o: JsonObject): VentanaCobro {
            o.soloClaves(archivo, "inicio", "fin")
            return VentanaCobro(
                Instant.parse(o.str(archivo, "inicio")),
                Instant.parse(o.str(archivo, "fin"))
            )
        }

        private fun cuentaDe(archivo: String, o: JsonObject): CuentaDelPeriodo {
            o.soloClaves(archivo, "venta_id", "cliente_id", "parcialidad")
            return CuentaDelPeriodo(
                ventaId = o.int(archivo, "venta_id"),
                clienteId = o.int(archivo, "cliente_id"),
                parcialidad = o.dinero(archivo, "parcialidad")
            )
        }

        private fun pagoDe(archivo: String, o: JsonObject): PagoEnVentana {
            o.soloClaves(
                archivo,
                "venta_id",
                "importe",
                "forma_cobro_id",
                "fecha_hora",
                "rechazado"
            )
            return PagoEnVentana(
                ventaId = o.int(archivo, "venta_id"),
                importe = o.dinero(archivo, "importe"),
                formaCobroId = o.int(archivo, "forma_cobro_id"),
                fechaHora = Instant.parse(o.str(archivo, "fecha_hora")),
                rechazado = o.bool(archivo, "rechazado")
            )
        }

        private fun visitaDe(archivo: String, o: JsonObject): VisitaEnVentana {
            o.soloClaves(
                archivo,
                "cliente_id",
                "venta_id",
                "tipo_visita",
                "fecha_hora",
                "fecha_cita",
                "hora_cita",
                "fecha_promesa"
            )
            return VisitaEnVentana(
                clienteId = o.int(archivo, "cliente_id"),
                ventaId = o.intONull(archivo, "venta_id"),
                tipoVisita = o.str(archivo, "tipo_visita"),
                fechaHora = Instant.parse(o.str(archivo, "fecha_hora")),
                fechaPromesa = o.strONull(archivo, "fecha_promesa")?.let(LocalDate::parse),
                fechaCita = o.strONull(archivo, "fecha_cita")?.let(LocalDate::parse),
                horaCita = o.strONull(archivo, "hora_cita")?.let(LocalTime::parse)
            )
        }

        private fun esperadoDe(archivo: String, o: JsonObject): Esperado {
            o.soloClaves(archivo, "venta_id", "estado", "con_algo")
            return Esperado(
                ventaId = o.int(archivo, "venta_id"),
                estado = CodigoEstado.aEnum(o.str(archivo, "estado")),
                conAlgo = o.bool(archivo, "con_algo")
            )
        }

        private fun JsonObject.soloClaves(archivo: String, vararg permitidas: String) {
            val sobran = keySet() - permitidas.toSet()
            val faltan = permitidas.toSet() - keySet()
            require(sobran.isEmpty()) { "$archivo: clave desconocida $sobran" }
            require(faltan.isEmpty()) { "$archivo: falta la clave $faltan" }
        }

        private fun JsonObject.campo(archivo: String, k: String): JsonElement =
            requireNotNull(get(k)) { "$archivo: falta la clave $k" }

        private fun JsonObject.obj(archivo: String, k: String): JsonObject = campo(
            archivo,
            k
        ).asJsonObject

        private fun JsonObject.arr(archivo: String, k: String): List<JsonObject> =
            campo(archivo, k).asJsonArray.map { it.asJsonObject }

        private fun JsonObject.str(archivo: String, k: String): String {
            val e = campo(archivo, k)
            require(
                e.isJsonPrimitive && e.asJsonPrimitive.isString
            ) { "$archivo: $k debe ser string" }
            return e.asString
        }

        private fun JsonObject.strONull(archivo: String, k: String): String? =
            if (campo(archivo, k).isJsonNull) null else str(archivo, k)

        private fun JsonObject.int(archivo: String, k: String): Int {
            val e = campo(archivo, k)
            require(
                e.isJsonPrimitive && e.asJsonPrimitive.isNumber
            ) { "$archivo: $k debe ser número" }
            return e.asInt
        }

        private fun JsonObject.intONull(archivo: String, k: String): Int? =
            if (campo(archivo, k).isJsonNull) null else int(archivo, k)

        private fun JsonObject.bool(archivo: String, k: String): Boolean {
            val e = campo(archivo, k)
            require(
                e.isJsonPrimitive && e.asJsonPrimitive.isBoolean
            ) { "$archivo: $k debe ser booleano" }
            return e.asBoolean
        }

        /** Dinero SOLO desde string (`"150.00"`): nunca pasa por `Double`. */
        private fun JsonObject.dinero(archivo: String, k: String): BigDecimal {
            val e = campo(archivo, k)
            require(e.isJsonPrimitive && e.asJsonPrimitive.isString) {
                "$archivo: $k es dinero y debe venir como string decimal, no como número JSON"
            }
            return BigDecimal(e.asString)
        }
    }
}

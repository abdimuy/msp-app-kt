package com.example.msp_app.core.common.visitas.domain

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Acceso al juego de casos de compromisos compartido con msp-api
 * (`src/test/resources/compromisos/`, copiado con
 * `scripts/casos-compromisos.sh copiar`; nunca se edita a mano). Mismo
 * mecanismo que `internal/visitas/domain/casos_compromisos_test.go`.
 */
internal object JuegoDeCompromisos {

    const val DIRECTORIO = "compromisos"
    private const val VERSION = "VERSION"
    val NOMBRE_CASO = Regex("^[0-9]{3}-[a-z0-9-]+\\.json$")

    fun directorio(): File {
        val url = requireNotNull(javaClass.classLoader?.getResource(DIRECTORIO)) {
            "no está src/test/resources/$DIRECTORIO; corre scripts/casos-compromisos.sh copiar"
        }
        require(
            url.protocol == "file"
        ) { "los casos deben leerse de disco, llegaron como ${url.protocol}" }
        return File(url.toURI())
    }

    /** Los .json en orden de nombre (= `LC_ALL=C sort`, nombres solo ASCII). */
    fun archivosDeCaso(dir: File = directorio()): List<File> {
        val visibles = dir.listFiles().orEmpty().filterNot { it.name.startsWith(".") }
        val fueraDeFormato = visibles.filterNot { it.name == VERSION || NOMBRE_CASO.matches(it.name) }
        require(fueraDeFormato.isEmpty()) {
            "archivos fuera de formato en ${dir.name}: ${fueraDeFormato.map { it.name }.sorted()}"
        }
        return visibles.filter { NOMBRE_CASO.matches(it.name) }.sortedBy { it.name }
    }

    fun sha256(archivos: List<File>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        archivos.forEach { digest.update(it.readBytes()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun versionDeclarada(dir: File = directorio()): String =
        File(dir, VERSION).readText(Charsets.US_ASCII).trim()

    fun casos(dir: File = directorio()): List<CasoDeCompromiso> =
        archivosDeCaso(dir).map { CasoDeCompromiso.desdeJson(it.name, it.readText(Charsets.UTF_8)) }
}

/** Una tarea del caso con la cuenta de su venta y el estado que se espera (E-APP-122). */
internal data class TareaDeCaso(
    val tarea: TareaCompromiso,
    val cuenta: CuentaCompromiso,
    val esperado: EstadoDeTarea
)

/** Lo que el caso espera para una venta, con la columna. */
internal data class EsperadoCompromiso(val vigente: CompromisoVigente, val columna: ColumnaSugerida)

/**
 * Un caso del juego. El parseo es ESTRICTO, igual que `parsearCasoCompromiso`
 * en Go: cada objeto trae EXACTAMENTE sus claves (una de menos se leería como
 * null y daría verde por casualidad), el "hoy" es el día civil de "ahora" en
 * la zona del negocio, la fecha de un pago con hora es el día civil de su
 * instante, el tipo de visita está en el catálogo y los instantes van en UTC
 * con Z.
 */
internal data class CasoDeCompromiso(
    val archivo: String,
    val nombre: String,
    val hoy: LocalDate,
    val ahora: Instant,
    val zona: ZoneId,
    val cuentas: List<CuentaCompromiso>,
    val visitas: List<VisitaCompromiso>,
    val pagos: List<PagoCompromiso>,
    val localPendiente: List<Boolean>,
    val esperado: Map<Int, EsperadoCompromiso>,
    /** Tareas del caso (E-APP-122); vacío si el caso no las trae. */
    val tareas: List<TareaDeCaso> = emptyList()
) {
    override fun toString(): String = "$archivo — $nombre"

    companion object {
        private const val ZONA_UNICA = "America/Mexico_City"
        private val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        @Suppress("LongMethod")
        fun desdeJson(archivo: String, texto: String): CasoDeCompromiso {
            val raiz = JsonParser.parseString(texto).asJsonObject
            raiz.soloClavesConOpcionales(
                archivo,
                listOf("tareas", "esperado_tareas"),
                "nombre",
                "hoy",
                "ahora",
                "zona",
                "cuentas",
                "visitas",
                "pagos",
                "esperado"
            )
            // Mismo criterio que Go: un caso con "tareas" pide cobrador_id en las visitas y id y cobrador_id en los pagos.
            val conTareas = raiz.has("tareas")
            val zonaTexto = raiz.str(archivo, "zona")
            require(zonaTexto == ZONA_UNICA) { "$archivo: zona solo $ZONA_UNICA: $zonaTexto" }
            val zona = ZoneId.of(zonaTexto)
            val hoy = fecha(archivo, raiz.str(archivo, "hoy"))
            val ahora = instante(archivo, raiz.str(archivo, "ahora"))
            // Review Focus 2: el "hoy" del caso es el día de "ahora" en la zona del negocio.
            require(ahora.atZone(zona).toLocalDate() == hoy) {
                "$archivo: hoy $hoy no es el día de ahora $ahora en $zona"
            }
            val cuentas = raiz.arr(archivo, "cuentas").map { cuentaDe(archivo, it) }
            require(
                cuentas.map {
                    it.ventaId
                }.toSet().size == cuentas.size
            ) { "$archivo: venta repetida en cuentas" }
            val visitas = raiz.arr(archivo, "visitas").map { visitaDe(archivo, it, conTareas) }
            require(
                visitas.map { it.id }.toSet().size == visitas.size
            ) { "$archivo: id de visita repetido" }
            val pagosCrudos = raiz.arr(archivo, "pagos").map { it.asJsonObject }
            val pagos = pagosCrudos.map { pagoDe(archivo, it, zona, conTareas) }
            val idsPago = pagos.mapNotNull { it.id }
            require(idsPago.toSet().size == idsPago.size) { "$archivo: id de pago repetido" }
            val localPendiente = pagosCrudos.map { it.bool(archivo, "local_pendiente") }
            val esperado = LinkedHashMap<Int, EsperadoCompromiso>()
            val ventas = cuentas.map { it.ventaId }.toSet()
            raiz.arr(archivo, "esperado").forEach {
                val e = esperadoDe(archivo, it)
                val venta = e.vigente.ventaId
                require(venta in ventas) { "$archivo: esperado de venta $venta fuera de cuentas" }
                require(
                    esperado.put(venta, e) == null
                ) { "$archivo: esperado de venta $venta repetido" }
            }
            val tareas = tareasDe(
                archivo,
                raiz,
                cuentas,
                visitas.map { it.id }.toSet(),
                idsPago.toSet(),
                ahora,
                zona
            )
            return CasoDeCompromiso(
                archivo,
                raiz.str(
                    archivo,
                    "nombre"
                ).also { require(it.isNotBlank()) { "$archivo: nombre vacío" } },
                hoy,
                ahora,
                zona,
                cuentas,
                visitas,
                pagos,
                localPendiente,
                esperado,
                tareas
            )
        }

        private fun cuentaDe(archivo: String, e: JsonElement): CuentaCompromiso {
            val o = e.asJsonObject
            o.soloClaves(archivo, "venta_id", "cliente_id")
            return CuentaCompromiso(o.int(archivo, "venta_id"), o.int(archivo, "cliente_id"))
        }

        private fun visitaDe(
            archivo: String,
            e: JsonElement,
            conTareas: Boolean
        ): VisitaCompromiso {
            val o = e.asJsonObject
            val claves =
                listOf("id", "cliente_id", "venta_id", "tipo_visita", "instante", "promesa", "cita")
            o.soloClaves(
                archivo,
                *(if (conTareas) claves + "cobrador_id" else claves).toTypedArray()
            )
            val tipo = o.str(archivo, "tipo_visita")
            require(
                TipoVisitaCatalogo.esConocido(tipo)
            ) { "$archivo: tipo_visita fuera del catálogo: $tipo" }
            return VisitaCompromiso(
                id = o.str(archivo, "id"),
                clienteId = o.int(archivo, "cliente_id"),
                ventaId = o.intONulo("venta_id"),
                tipoVisita = tipo,
                instante = instante(archivo, o.str(archivo, "instante")),
                promesa = o.objONulo("promesa")?.let { promesaDe(archivo, it) },
                cita = o.objONulo("cita")?.let { citaDe(archivo, it) },
                cobradorId = o.intONulo("cobrador_id")
            )
        }

        private fun promesaDe(archivo: String, o: JsonObject): PromesaCompromiso {
            o.soloClaves(archivo, "venta_id", "fecha", "monto_centavos")
            // DA-16: una promesa sin fecha no existe; el caso la escribe como null.
            val monto = o.longONulo("monto_centavos")
            require(monto == null || monto > 0) { "$archivo: monto_centavos debe ser > 0" }
            return PromesaCompromiso(
                o.intONulo("venta_id"),
                fecha(archivo, o.str(archivo, "fecha")),
                monto
            )
        }

        private fun citaDe(archivo: String, o: JsonObject): CitaCompromiso {
            o.soloClaves(archivo, "fecha", "hora")
            return CitaCompromiso(
                fecha(archivo, o.str(archivo, "fecha")),
                o.strONulo("hora")?.let {
                    hora(archivo, it)
                }
            )
        }

        private fun pagoDe(
            archivo: String,
            o: JsonObject,
            zona: ZoneId,
            conTareas: Boolean
        ): PagoCompromiso {
            val base = arrayOf("venta_id", "instante", "fecha", "rechazado", "local_pendiente")
            if (conTareas) {
                o.soloClaves(archivo, "id", "cobrador_id", *base)
            } else {
                o.soloClavesConOpcionales(archivo, listOf("id"), *base)
            }
            val id = o.strONulo("id")
            require(id == null || id.isNotEmpty()) { "$archivo: id de pago vacío" }
            require(
                !conTareas || id != null
            ) { "$archivo: un caso con tareas lleva id en cada pago" }
            val instante = o.strONulo("instante")?.let { instante(archivo, it) }
            val dia = fecha(archivo, o.str(archivo, "fecha"))
            require(instante == null || instante.atZone(zona).toLocalDate() == dia) {
                "$archivo: fecha $dia no es el día civil de su instante $instante"
            }
            return PagoCompromiso(
                o.int(archivo, "venta_id"),
                instante,
                dia,
                o.bool(archivo, "rechazado"),
                id = id,
                cobradorId = if (id != null) o.intONulo("cobrador_id") else null
            )
        }

        /** "tareas" y "esperado_tareas" (E-APP-122); espejo de `parsearTareasDeCaso` en Go. */
        @Suppress("LongParameterList")
        private fun tareasDe(
            archivo: String,
            raiz: JsonObject,
            cuentas: List<CuentaCompromiso>,
            idsVisita: Set<String>,
            idsPago: Set<String>,
            ahora: Instant,
            zona: ZoneId
        ): List<TareaDeCaso> {
            if (!raiz.has("tareas")) {
                require(!raiz.has("esperado_tareas")) { "$archivo: esperado_tareas sin tareas" }
                return emptyList()
            }
            val crudas = raiz.arr(archivo, "tareas")
            val esperadas = raiz.arr(archivo, "esperado_tareas")
            require(crudas.isNotEmpty() && esperadas.size == crudas.size) {
                "$archivo: tareas ${crudas.size} y esperado_tareas ${esperadas.size}: una entrada esperada por tarea"
            }
            val clienteDe = cuentas.associate { it.ventaId to it.clienteId }
            val tareas = crudas.map { tareaDe(archivo, it.asJsonObject, clienteDe, ahora) }
            require(
                tareas.map { it.first.id }.toSet().size == tareas.size
            ) { "$archivo: id de tarea repetido" }
            val esperadoPorId = HashMap<String, EstadoDeTarea>()
            esperadas.forEach {
                val o = it.asJsonObject
                o.soloClaves(archivo, "id", "estado", "atendida_por")
                val id = o.str(archivo, "id")
                require(tareas.any { t -> t.first.id == id } && esperadoPorId[id] == null) {
                    "$archivo: esperado_tareas $id no es una tarea del caso o está repetido"
                }
                esperadoPorId[id] = estadoEsperadoDe(archivo, o, idsVisita, idsPago, zona)
            }
            return tareas.map { (t, cuenta) ->
                TareaDeCaso(
                    t,
                    cuenta,
                    esperadoPorId.getValue(t.id)
                )
            }
        }

        private fun tareaDe(
            archivo: String,
            o: JsonObject,
            clienteDe: Map<Int, Int>,
            ahora: Instant
        ): Pair<TareaCompromiso, CuentaCompromiso> {
            o.soloClaves(archivo, "id", "venta_id", "fecha", "hora", "created_at", "cancelada_at")
            val ventaId = o.int(archivo, "venta_id")
            val cliente =
                requireNotNull(
                    clienteDe[ventaId]
                ) { "$archivo: venta $ventaId de la tarea no está en cuentas" }
            val creada = instante(archivo, o.str(archivo, "created_at"))
            require(!creada.isAfter(ahora)) { "$archivo: created_at posterior a ahora" }
            val cancelada = o.strONulo("cancelada_at")?.let { instante(archivo, it) }
            require(
                cancelada == null || (!cancelada.isBefore(creada) && !cancelada.isAfter(ahora))
            ) {
                "$archivo: cancelada_at fuera de [created_at, ahora]"
            }
            val t = TareaCompromiso(
                id = o.str(archivo, "id"),
                ventaId = ventaId,
                fecha = fecha(archivo, o.str(archivo, "fecha")),
                hora = o.strONulo("hora")?.let { hora(archivo, it) },
                createdAt = creada,
                canceladaAt = cancelada
            )
            return t to CuentaCompromiso(ventaId, cliente)
        }

        private fun estadoEsperadoDe(
            archivo: String,
            o: JsonObject,
            idsVisita: Set<String>,
            idsPago: Set<String>,
            zona: ZoneId
        ): EstadoDeTarea {
            val codigo = o.str(archivo, "estado")
            val estado = requireNotNull(EstadoTarea.entries.find { it.codigo == codigo }) {
                "$archivo: estado de tarea $codigo"
            }
            val atendida = o.objONulo("atendida_por")
            require((estado == EstadoTarea.ATENDIDA) == (atendida != null)) {
                "$archivo: atendida_por va solo y siempre con estado atendida"
            }
            return EstadoDeTarea(
                estado,
                atendida?.let { atendidaPorDe(archivo, it, idsVisita, idsPago, zona) }
            )
        }

        private fun atendidaPorDe(
            archivo: String,
            o: JsonObject,
            idsVisita: Set<String>,
            idsPago: Set<String>,
            zona: ZoneId
        ): AtendidaPor {
            o.soloClaves(archivo, "tipo", "id", "por", "instante", "fecha")
            val codigo = o.str(archivo, "tipo")
            val tipo = requireNotNull(TipoAtencion.entries.find { it.codigo == codigo }) {
                "$archivo: atendida_por.tipo $codigo"
            }
            val id = o.str(archivo, "id")
            val instante = o.strONulo("instante")?.let { instante(archivo, it) }
            when (tipo) {
                TipoAtencion.VISITA -> require(id in idsVisita && instante != null) {
                    "$archivo: visita $id inexistente o sin instante"
                }
                TipoAtencion.PAGO -> require(id in idsPago) { "$archivo: pago $id inexistente" }
            }
            val dia = fecha(archivo, o.str(archivo, "fecha"))
            require(instante == null || instante.atZone(zona).toLocalDate() == dia) {
                "$archivo: fecha $dia no es el día civil de su instante"
            }
            return AtendidaPor(tipo, id, o.intONulo("por"), instante, dia)
        }

        private fun esperadoDe(archivo: String, e: JsonElement): EsperadoCompromiso {
            val o = e.asJsonObject
            o.soloClaves(
                archivo,
                "venta_id",
                "visita_id",
                "tipo",
                "estado",
                "dia",
                "hora",
                "monto_centavos",
                "columna"
            )
            val tipo = o.str(archivo, "tipo")
            val estado = o.str(archivo, "estado")
            val columna = o.str(archivo, "columna")
            val vigente = CompromisoVigente(
                ventaId = o.int(archivo, "venta_id"),
                visitaId = o.str(archivo, "visita_id"),
                tipo = requireNotNull(
                    TipoCompromiso.entries.find { it.codigo == tipo }
                ) { "$archivo: tipo $tipo" },
                estado = requireNotNull(EstadoCompromiso.entries.find { it.codigo == estado }) {
                    "$archivo: estado $estado"
                },
                dia = fecha(archivo, o.str(archivo, "dia")),
                hora = o.strONulo("hora")?.let { hora(archivo, it) },
                montoCentavos = o.longONulo("monto_centavos")
            )
            val col = requireNotNull(ColumnaSugerida.entries.find { it.codigo == columna }) {
                "$archivo: columna $columna"
            }
            return EsperadoCompromiso(vigente, col)
        }

        private fun fecha(archivo: String, s: String): LocalDate {
            val f = runCatching { LocalDate.parse(s) }.getOrNull()
            require(f != null && f.toString() == s) { "$archivo: fecha no canónica: $s" }
            return f
        }

        private fun hora(archivo: String, s: String): LocalTime {
            val h = runCatching { LocalTime.parse(s, HORA) }.getOrNull()
            require(h != null && h.format(HORA) == s) { "$archivo: hora no canónica: $s" }
            return h
        }

        private fun instante(archivo: String, s: String): Instant {
            require(s.endsWith("Z")) { "$archivo: el instante va en UTC con Z: $s" }
            return Instant.parse(s)
        }

        private fun JsonObject.soloClaves(archivo: String, vararg claves: String) {
            val tiene = keySet()
            require(tiene == claves.toSet()) {
                "$archivo: claves ${tiene.sorted()} y se esperaban ${claves.sorted()}"
            }
        }

        private fun JsonObject.soloClavesConOpcionales(
            archivo: String,
            opcionales: List<String>,
            vararg obligatorias: String
        ) {
            val tiene = keySet()
            val sobran = tiene - obligatorias.toSet() - opcionales.toSet()
            val faltan = obligatorias.toSet() - tiene
            require(sobran.isEmpty() && faltan.isEmpty()) {
                "$archivo: claves ${tiene.sorted()}; obligatorias ${obligatorias.sorted()}, opcionales ${opcionales.sorted()}"
            }
        }

        private fun JsonObject.str(archivo: String, k: String): String {
            val v = get(k)
            require(v != null && !v.isJsonNull && v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                "$archivo: $k debe ser texto"
            }
            return v.asString
        }

        private fun JsonObject.strONulo(k: String): String? = get(
            k
        )?.takeUnless { it.isJsonNull }?.asString

        private fun JsonObject.int(archivo: String, k: String): Int {
            val v = get(k)
            require(
                v != null && !v.isJsonNull && v.asJsonPrimitive.isNumber
            ) { "$archivo: $k debe ser número" }
            return v.asInt
        }

        private fun JsonObject.intONulo(k: String): Int? = get(
            k
        )?.takeUnless { it.isJsonNull }?.asInt

        private fun JsonObject.longONulo(k: String): Long? = get(
            k
        )?.takeUnless { it.isJsonNull }?.asLong

        private fun JsonObject.bool(archivo: String, k: String): Boolean {
            val v = get(k)
            require(v != null && !v.isJsonNull && v.asJsonPrimitive.isBoolean) {
                "$archivo: $k debe ser booleano"
            }
            return v.asBoolean
        }

        private fun JsonObject.objONulo(k: String): JsonObject? = get(k)?.takeUnless {
            it.isJsonNull
        }?.asJsonObject

        private fun JsonObject.arr(archivo: String, k: String): List<JsonElement> {
            val v = get(k)
            require(v != null && v.isJsonArray) { "$archivo: $k debe ser lista" }
            return v.asJsonArray.toList()
        }
    }
}

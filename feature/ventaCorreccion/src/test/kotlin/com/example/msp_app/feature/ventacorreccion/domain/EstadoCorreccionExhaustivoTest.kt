package com.example.msp_app.feature.ventacorreccion.domain

import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val AHORA = 10_000_000_000L

/**
 * Las cinco columnas de `local_sale` que, junto con el candado, deciden [EstadoCorreccion].
 * Agrupadas en una clase (en vez de sueltas en el constructor del caso) para no chocar con
 * `LongParameterList` de detekt sin perder ninguna dimensión de la tabla. Las dos últimas
 * llegaron con el nivel 2 ("Corregir una venta DESPUÉS de que subió").
 */
data class FilaDeVenta(
    val enviado: Boolean = false,
    val permanente: Boolean = false,
    val correccionNoEnviada: Boolean = false,
    val correccionRemotaPendiente: Boolean = false,
    val correccionRemotaEstado: String? = null
)

/** Un estado posible del candado único: `CLAIM_KIND` + `CLAIMED_AT`, crudos. */
data class CandadoDePrueba(val nombre: String, val kind: String?, val claimedAt: Long?)

/** Un renglón de la tabla exhaustiva. */
data class CasoEvaluarCorregibilidad(
    val nombre: String,
    val fila: FilaDeVenta,
    val candado: CandadoDePrueba,
    val esperado: EstadoCorreccion
) {
    override fun toString() = nombre
}

/**
 * Barrido paramétrico EXHAUSTIVO de [evaluarCorregibilidad] sobre las tablas del plan "Corregir
 * una venta antes de que suba" (Task 2) y su ampliación "Corregir una venta DESPUÉS de que
 * subió, mientras siga en borrador" (nivel 2, que agregó `correccionRemotaPendiente` y
 * `correccionRemotaEstado` y le cambió el destino a la rama `enviado`).
 *
 * La precedencia que esta tabla cubre, en el orden EXACTO del KDoc de [evaluarCorregibilidad]:
 * 1. marca terminal → [EstadoCorreccion.LaOficinaYaLaAplico], gane quien gane lo demás
 *    ([casosMarcaTerminalGanaSobreTodo]: las 16 combinaciones de los cuatro booleanos × 3 marcas
 *    × candado ausente/vivo).
 * 2. `correccionRemotaPendiente` → [EstadoCorreccion.CorreccionEnCamino], gana sobre la
 *    divergencia, el fallo permanente y cualquier candado
 *    ([casosColaGanaSobreTodoMenosLaMarca]).
 * 3. `enviado` **y** `correccionNoEnviada` → [EstadoCorreccion.LaRevisaLaOficina]
 *    ([casosEnviadaConDivergencia], contra los diez estados del candado).
 * 4. `enviado` → [EstadoCorreccion.CorregibleEnviada] ([casosEnviadaLimpiaEsCorregible],
 *    también contra los diez): es el cambio de fondo del nivel 2 — antes esta rama daba
 *    `YaSeEnvio` y el dueño se quedaba sin salida sobre su propia venta.
 * 5. sin enviar, el fallo permanente manda sobre CUALQUIER candado ([casosPermanente]).
 * 6. sin enviar y sin permanente, sólo un candado `UPLOAD` VIVO produce
 *    [EstadoCorreccion.SeEstaEnviando]; vencido, `EDIT`, o con datos incompletos/corruptos, caen
 *    todos en [EstadoCorreccion.Corregible] ([casosCandadoFrontera],
 *    [casosCandadoDatosIncompletos]).
 *
 * En los cuatro grupos generados el esperado es CONSTANTE, y esa constancia es justo la
 * aserción: dice que ninguna otra columna puede mover ese resultado. No reimplementa la
 * función. La tabla literal de las 16 combinaciones de los cuatro booleanos con el candado
 * ausente ([casosCuatroBooleanosSinEnviar] + [casosCuatroBooleanosEnviada]) lleva cada esperado
 * escrito A MANO, uno por uno, y es la que ancla todo lo demás.
 *
 * [EstadoCorreccion.YaSeEnvio] NO aparece como esperado en NINGÚN renglón: desde el nivel 2 esta
 * función no lo produce nunca — sobrevive sólo como el estado que `GuardarCorreccion` devuelve
 * cuando la fila ya no existe, un camino que esta función no recorre.
 */
@RunWith(Parameterized::class)
class EstadoCorreccionExhaustivoTest(private val caso: CasoEvaluarCorregibilidad) {

    @Test
    fun `precedencia exhaustiva`() {
        val estado = evaluarCorregibilidad(
            enviado = caso.fila.enviado,
            permanente = caso.fila.permanente,
            correccionNoEnviada = caso.fila.correccionNoEnviada,
            correccionRemotaPendiente = caso.fila.correccionRemotaPendiente,
            correccionRemotaEstado = caso.fila.correccionRemotaEstado,
            claimKind = caso.candado.kind,
            claimedAt = caso.candado.claimedAt,
            ahora = AHORA
        )

        assertEquals(caso.nombre, caso.esperado, estado)
    }

    @Suppress("TooManyFunctions")
    companion object {
        private val editLease = LocalSaleClaimLeases.EDIT_LEASE_MS
        private val uploadLease = LocalSaleClaimLeases.UPLOAD_LEASE_MS

        private val sinCandado = CandadoDePrueba("sin candado", null, null)
        private val uploadVivo = CandadoDePrueba("UPLOAD vivo", "UPLOAD", AHORA - uploadLease + 1)

        /**
         * Los DIEZ estados del candado que este archivo reconoce: los seis reales (ausente,
         * `UPLOAD`/`EDIT` vivo y vencido justo en su arrendamiento) más los cuatro corruptos o
         * incompletos, que el dominio trata como vencidos por defensa en profundidad — mismo
         * criterio que el DAO ("una captura nunca se retiene para siempre").
         */
        private val candados: List<CandadoDePrueba> = listOf(
            sinCandado,
            uploadVivo,
            CandadoDePrueba("UPLOAD vencido", "UPLOAD", AHORA - uploadLease),
            CandadoDePrueba("UPLOAD sin claimedAt", "UPLOAD", null),
            CandadoDePrueba("EDIT vivo", "EDIT", AHORA - editLease + 1),
            CandadoDePrueba("EDIT vencido", "EDIT", AHORA - editLease),
            CandadoDePrueba("EDIT sin claimedAt", "EDIT", null),
            CandadoDePrueba("CLAIM_KIND vacio", "", AHORA),
            CandadoDePrueba("CLAIM_KIND desconocido", "borrado", AHORA),
            CandadoDePrueba("CLAIM_KIND nulo con claimedAt", null, AHORA)
        )

        /**
         * Los tres valores de `CORRECCION_REMOTA_ESTADO` que NO son marca terminal. El `"   "`
         * está por [esCorreccionRemotaTerminal], que usa `isNullOrBlank`: una columna en blanco
         * es "sin incidencia", no una puerta cerrada.
         */
        private val marcasEnBlanco: List<String?> = listOf(null, "", "   ")

        /**
         * Las marcas terminales que llevan a [EstadoCorreccion.LaOficinaYaLaAplico]: las del
         * vocabulario que significan "no entró nada", más una desconocida — el predicado falla
         * CERRADO a propósito (cualquier valor no vacío cuenta), al revés que [tipoCandadoDe]. Si
         * algún día alguien lo restringe a [CorreccionRemotaTerminal.CONOCIDOS], la tercera se
         * pone roja.
         *
         * `APLICADA_PARCIAL` NO está aquí: es terminal igual, pero su desenlace es otro y tiene
         * su propio grupo ([casosMarcaParcial]). Meterla en esta lista escondería justo la
         * distinción que importa — "no entró nada" contra "entró parte".
         */
        private val marcasTerminales: List<String> = listOf(
            CorreccionRemotaTerminal.RECHAZADA_ESTADO,
            CorreccionRemotaTerminal.CONFLICTO,
            "UN_TERMINAL_QUE_TODAVIA_NO_EXISTE"
        )

        private val booleanos = listOf(false, true)

        /** Las 16 combinaciones de los cuatro booleanos, con [marca] fija en la quinta columna. */
        private fun dieciseisFilas(marca: String?): List<FilaDeVenta> =
            booleanos.flatMap { enviado ->
                booleanos.flatMap { permanente ->
                    booleanos.flatMap { noEnviada ->
                        booleanos.map { pendiente ->
                            FilaDeVenta(enviado, permanente, noEnviada, pendiente, marca)
                        }
                    }
                }
            }

        /** Las 8 combinaciones de (enviado, permanente, correccionNoEnviada). */
        private fun ochoFilas(marca: String?, pendiente: Boolean): List<FilaDeVenta> =
            booleanos.flatMap { enviado ->
                booleanos.flatMap { permanente ->
                    booleanos.map { noEnviada ->
                        FilaDeVenta(enviado, permanente, noEnviada, pendiente, marca)
                    }
                }
            }

        private fun filasEnviadas(correccionNoEnviada: Boolean): List<FilaDeVenta> =
            marcasEnBlanco.flatMap { marca ->
                booleanos.map { permanente ->
                    FilaDeVenta(
                        enviado = true,
                        permanente = permanente,
                        correccionNoEnviada = correccionNoEnviada,
                        correccionRemotaPendiente = false,
                        correccionRemotaEstado = marca
                    )
                }
            }

        private fun etiqueta(fila: FilaDeVenta): String =
            "enviado=${fila.enviado} permanente=${fila.permanente} " +
                "noEnviada=${fila.correccionNoEnviada} " +
                "pendiente=${fila.correccionRemotaPendiente} " +
                "marca=${fila.correccionRemotaEstado?.let { "'$it'" } ?: "null"}"

        private fun literal(nombre: String, fila: FilaDeVenta, esperado: EstadoCorreccion) =
            CasoEvaluarCorregibilidad("$nombre -> $esperado", fila, sinCandado, esperado)

        // ── Tabla LITERAL: los cuatro booleanos, sin candado, sin marca ──
        // Cada esperado escrito a mano. Es la única parte del archivo que no
        // se genera, y por eso es la que ancla todo lo demás.

        private fun casosCuatroBooleanosSinEnviar() = listOf(
            literal("sin enviar, limpia", FilaDeVenta(), EstadoCorreccion.Corregible),
            literal(
                "sin enviar, con la cola levantada",
                FilaDeVenta(correccionRemotaPendiente = true),
                EstadoCorreccion.CorreccionEnCamino
            ),
            literal(
                "sin enviar, con divergencia marcada (que sin enviar no significa nada)",
                FilaDeVenta(correccionNoEnviada = true),
                EstadoCorreccion.Corregible
            ),
            literal(
                "sin enviar, divergencia + cola",
                FilaDeVenta(correccionNoEnviada = true, correccionRemotaPendiente = true),
                EstadoCorreccion.CorreccionEnCamino
            ),
            literal(
                "sin enviar, fallo permanente",
                FilaDeVenta(permanente = true),
                EstadoCorreccion.LaRevisaLaOficina
            ),
            literal(
                "sin enviar, permanente + cola",
                FilaDeVenta(permanente = true, correccionRemotaPendiente = true),
                EstadoCorreccion.CorreccionEnCamino
            ),
            literal(
                "sin enviar, permanente + divergencia",
                FilaDeVenta(permanente = true, correccionNoEnviada = true),
                EstadoCorreccion.LaRevisaLaOficina
            ),
            literal(
                "sin enviar, permanente + divergencia + cola",
                FilaDeVenta(
                    permanente = true,
                    correccionNoEnviada = true,
                    correccionRemotaPendiente = true
                ),
                EstadoCorreccion.CorreccionEnCamino
            )
        )

        private fun casosCuatroBooleanosEnviada() = listOf(
            literal(
                "enviada, limpia",
                FilaDeVenta(enviado = true),
                EstadoCorreccion.CorregibleEnviada
            ),
            literal(
                "enviada, con la cola levantada",
                FilaDeVenta(enviado = true, correccionRemotaPendiente = true),
                EstadoCorreccion.CorreccionEnCamino
            ),
            literal(
                "enviada, con divergencia marcada",
                FilaDeVenta(enviado = true, correccionNoEnviada = true),
                EstadoCorreccion.LaRevisaLaOficina
            ),
            literal(
                "enviada, divergencia + cola",
                FilaDeVenta(
                    enviado = true,
                    correccionNoEnviada = true,
                    correccionRemotaPendiente = true
                ),
                EstadoCorreccion.CorreccionEnCamino
            ),
            literal(
                "enviada, fallo permanente (enviado manda)",
                FilaDeVenta(enviado = true, permanente = true),
                EstadoCorreccion.CorregibleEnviada
            ),
            literal(
                "enviada, permanente + cola",
                FilaDeVenta(enviado = true, permanente = true, correccionRemotaPendiente = true),
                EstadoCorreccion.CorreccionEnCamino
            ),
            literal(
                "enviada, permanente + divergencia",
                FilaDeVenta(enviado = true, permanente = true, correccionNoEnviada = true),
                EstadoCorreccion.LaRevisaLaOficina
            ),
            literal(
                "enviada, permanente + divergencia + cola",
                FilaDeVenta(
                    enviado = true,
                    permanente = true,
                    correccionNoEnviada = true,
                    correccionRemotaPendiente = true
                ),
                EstadoCorreccion.CorreccionEnCamino
            )
        )

        // ── Paso 1: la marca terminal gana sobre TODO (96 renglones) ─────

        private fun casosMarcaTerminalGanaSobreTodo(): List<CasoEvaluarCorregibilidad> =
            marcasTerminales.flatMap { marca ->
                dieciseisFilas(marca).flatMap { fila ->
                    listOf(sinCandado, uploadVivo).map { candado ->
                        CasoEvaluarCorregibilidad(
                            "marca terminal gana: ${etiqueta(fila)}, ${candado.nombre}",
                            fila,
                            candado,
                            EstadoCorreccion.LaOficinaYaLaAplico
                        )
                    }
                }
            }

        // ── Paso 1b: APLICADA_PARCIAL gana igual, pero con OTRO desenlace (32) ─

        /**
         * Mismo barrido que [casosMarcaTerminalGanaSobreTodo] pero con la marca que significa
         * "entró parte": ninguna otra columna puede moverlo, y el desenlace NO es
         * [EstadoCorreccion.LaOficinaYaLaAplico]. Que el esperado sea constante ES la aserción.
         *
         * Lo que blinda: decirle al cobrador "la aplicó la oficina" cuando parte de su corrección
         * sí entró lo mandaría a confiar en datos viejos, porque el servidor quedó distinto de
         * como estaba y nadie más se va a dar cuenta.
         */
        private fun casosMarcaParcial(): List<CasoEvaluarCorregibilidad> =
            dieciseisFilas(CorreccionRemotaTerminal.APLICADA_PARCIAL).flatMap { fila ->
                listOf(sinCandado, uploadVivo).map { candado ->
                    CasoEvaluarCorregibilidad(
                        "aplicada parcial gana: ${etiqueta(fila)}, ${candado.nombre}",
                        fila,
                        candado,
                        EstadoCorreccion.SeAplicoAMedias
                    )
                }
            }

        // ── Paso 2: la cola gana sobre todo lo que no sea la marca (240) ─

        private fun casosColaGanaSobreTodoMenosLaMarca(): List<CasoEvaluarCorregibilidad> =
            marcasEnBlanco.flatMap { marca ->
                ochoFilas(marca, pendiente = true).flatMap { fila ->
                    candados.map { candado ->
                        CasoEvaluarCorregibilidad(
                            "la cola gana: ${etiqueta(fila)}, ${candado.nombre}",
                            fila,
                            candado,
                            EstadoCorreccion.CorreccionEnCamino
                        )
                    }
                }
            }

        // ── Pasos 3 y 4: dentro de enviada la divergencia decide, y el
        // candado NO tiene voz — ni un UPLOAD vivo la vuelve SeEstaEnviando.

        private fun casosEnviadaConDivergencia(): List<CasoEvaluarCorregibilidad> =
            filasEnviadas(correccionNoEnviada = true).flatMap { fila ->
                candados.map { candado ->
                    CasoEvaluarCorregibilidad(
                        "enviada con divergencia: ${etiqueta(fila)}, ${candado.nombre}",
                        fila,
                        candado,
                        EstadoCorreccion.LaRevisaLaOficina
                    )
                }
            }

        private fun casosEnviadaLimpiaEsCorregible(): List<CasoEvaluarCorregibilidad> =
            filasEnviadas(correccionNoEnviada = false).flatMap { fila ->
                candados.map { candado ->
                    CasoEvaluarCorregibilidad(
                        "enviada limpia: ${etiqueta(fila)}, ${candado.nombre}",
                        fila,
                        candado,
                        EstadoCorreccion.CorregibleEnviada
                    )
                }
            }

        // ── Pasos 5, 6 y 7: el nivel 1, intacto ──────────────────────────
        // Estos tres grupos son los del nivel 1 palabra por palabra: sólo
        // alcanzan a ventas sin enviar, sin cola y sin marca, y su
        // comportamiento NO cambió. Que sigan aquí verdes es la prueba.

        private fun casosPermanente() = listOf(
            CasoEvaluarCorregibilidad(
                "sin enviar, permanente, sin candado -> LaRevisaLaOficina",
                FilaDeVenta(permanente = true),
                sinCandado,
                EstadoCorreccion.LaRevisaLaOficina
            ),
            CasoEvaluarCorregibilidad(
                "sin enviar, permanente, candado UPLOAD vivo -> LaRevisaLaOficina",
                FilaDeVenta(permanente = true),
                CandadoDePrueba("UPLOAD recien tomado", "UPLOAD", AHORA),
                EstadoCorreccion.LaRevisaLaOficina
            ),
            CasoEvaluarCorregibilidad(
                "sin enviar, permanente, candado EDIT vivo -> LaRevisaLaOficina",
                FilaDeVenta(permanente = true),
                CandadoDePrueba("EDIT recien tomado", "EDIT", AHORA),
                EstadoCorreccion.LaRevisaLaOficina
            )
        )

        private fun casosCandadoFrontera() = listOf(
            CasoEvaluarCorregibilidad(
                "sin enviar, sin permanente, sin candado -> Corregible",
                FilaDeVenta(),
                sinCandado,
                EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "candado UPLOAD vivo (lease-1ms) -> SeEstaEnviando",
                FilaDeVenta(),
                uploadVivo,
                EstadoCorreccion.SeEstaEnviando
            ),
            CasoEvaluarCorregibilidad(
                "candado UPLOAD vencido justo en el lease -> Corregible",
                FilaDeVenta(),
                CandadoDePrueba("UPLOAD vencido", "UPLOAD", AHORA - uploadLease),
                EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "candado EDIT vivo (lease-1ms) -> Corregible",
                FilaDeVenta(),
                CandadoDePrueba("EDIT vivo", "EDIT", AHORA - editLease + 1),
                EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "candado EDIT vencido justo en el lease -> Corregible",
                FilaDeVenta(),
                CandadoDePrueba("EDIT vencido", "EDIT", AHORA - editLease),
                EstadoCorreccion.Corregible
            )
        )

        private fun casosCandadoDatosIncompletos() = listOf(
            CasoEvaluarCorregibilidad(
                "candado UPLOAD sin claimedAt -> Corregible",
                FilaDeVenta(),
                CandadoDePrueba("UPLOAD sin claimedAt", "UPLOAD", null),
                EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "candado EDIT sin claimedAt -> Corregible",
                FilaDeVenta(),
                CandadoDePrueba("EDIT sin claimedAt", "EDIT", null),
                EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "CLAIM_KIND vacio con timestamp reciente -> Corregible",
                FilaDeVenta(),
                CandadoDePrueba("CLAIM_KIND vacio", "", AHORA),
                EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "CLAIM_KIND desconocido con timestamp reciente -> Corregible",
                FilaDeVenta(),
                CandadoDePrueba("CLAIM_KIND desconocido", "borrado", AHORA),
                EstadoCorreccion.Corregible
            ),
            CasoEvaluarCorregibilidad(
                "CLAIM_KIND nulo con claimedAt puesto -> Corregible",
                FilaDeVenta(),
                CandadoDePrueba("CLAIM_KIND nulo con claimedAt", null, AHORA),
                EstadoCorreccion.Corregible
            )
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun casos(): List<CasoEvaluarCorregibilidad> = casosCuatroBooleanosSinEnviar() +
            casosCuatroBooleanosEnviada() +
            casosMarcaTerminalGanaSobreTodo() +
            casosMarcaParcial() +
            casosColaGanaSobreTodoMenosLaMarca() +
            casosEnviadaConDivergencia() +
            casosEnviadaLimpiaEsCorregible() +
            casosPermanente() +
            casosCandadoFrontera() +
            casosCandadoDatosIncompletos()
    }
}

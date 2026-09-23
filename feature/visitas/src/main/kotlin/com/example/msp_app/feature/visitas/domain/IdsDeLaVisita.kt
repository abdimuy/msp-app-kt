package com.example.msp_app.feature.visitas.domain

import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * **Cómo se nombran las N visitas que una sola captura escribe.**
 *
 * La pantalla acuña UN id, una sola vez, y lo guarda en su `SavedStateHandle`
 * (`RegistrarVisitaViewModel.visitaId`). Ese id es la semilla de la captura. Un
 * desenlace de alcance `VENTA` marcado sobre N cuentas escribe **N filas**, y
 * cada fila necesita su propio `Visit.ID`: es la llave primaria en Room y la
 * clave de idempotencia de `POST /v2/visitas`, así que dos filas con el mismo id
 * serían una sola visita.
 *
 * ## El esquema
 *
 * ```
 * alcance CLIENTE  ->  una visita, y su id ES la semilla (no hay cuenta de la
 *                      que derivar; el hecho es de la puerta entera)
 * alcance VENTA    ->  una visita por cuenta marcada, y el id de la cuenta `v`
 *                      es  UUID v3 (name-based, MD5) sobre el nombre
 *                      "<semilla>/<v>"
 * ```
 *
 * ## Por qué esto es determinista
 *
 * [UUID.nameUUIDFromBytes] es una función PURA del nombre que recibe, y el
 * nombre solo lleva dos cosas: la semilla de la captura y el `DOCTO_CC_ACR_ID`
 * de la cuenta. **Ni reloj, ni azar, ni el orden de la lista, ni cuántas cuentas
 * hay marcadas.** Las mismas dos entradas producen el mismo id en cualquier
 * teléfono, en cualquier corrida, hoy o dentro de un año.
 *
 * Que el nombre NO lleve la posición dentro de la lista es deliberado: si los
 * ids se numeraran por índice, desmarcar la primera cuenta renumeraría a todas
 * las demás y el reintento escribiría filas nuevas al lado de las viejas.
 *
 * ## Por qué esto es idempotente
 *
 * El `INSERT` de `VisitDao` es `REPLACE` sobre `Visit.ID`. Si el proceso muere
 * después de escribir 2 de 3, el reintento vuelve a derivar **los mismos tres
 * ids** desde la misma semilla y las mismas cuentas: reescribe las dos que ya
 * estaban —sobre sí mismas, sin duplicarlas— y escribe la tercera. Del lado del
 * servidor pasa lo mismo: `RegistrarVisita` es idempotente en el id, así que un
 * reenvío devuelve la visita ya guardada en vez de crear una segunda.
 *
 * ## Por qué un UUID y no "<semilla>-<v>"
 *
 * Porque el servidor **parsea el id como UUID** antes de mirar nada más
 * (`internal/visitas/infra/visitashttp/handlers.go:46`, `uuid.Parse(in.Body.ID)`;
 * `domain.NewVisita` rechaza `uuid.Nil`). Un id con forma libre viajaría bien por
 * Room y por `GET /v2/visitas/by-ids` —que solo compara cadenas— y rebotaría con
 * 422 en el único punto que importa: la subida. La visita quedaría en el teléfono
 * reintentando para siempre. `nameUUIDFromBytes` devuelve un UUID canónico
 * (8-4-4-4-12), que es lo que `uuid.Parse` acepta.
 */
object IdsDeLaVisita {

    /**
     * El id de la visita de la cuenta [ventaId] dentro de la captura [semilla].
     *
     * El separador `/` no aparece en ninguna de las dos partes —la semilla es un
     * UUID y la cuenta un entero—, así que dos pares distintos no pueden producir
     * el mismo nombre.
     */
    fun idDe(semilla: String, ventaId: Int): String =
        UUID.nameUUIDFromBytes("$semilla/$ventaId".toByteArray(StandardCharsets.UTF_8)).toString()

    /**
     * Los ids de [cuentas], **ordenados por cuenta** para que el recorrido de la
     * escritura sea siempre el mismo. Con la lista vacía devuelve vacío: ese es
     * el caso de alcance CLIENTE, que no pasa por aquí.
     */
    fun paraCuentas(semilla: String, cuentas: Collection<Int>): Map<Int, String> =
        cuentas.distinct().sorted().associateWith { idDe(semilla, it) }

    /**
     * El id de la visita **ancla**: la de la cuenta más baja, o la semilla misma
     * cuando no hay cuentas (alcance CLIENTE).
     *
     * El ancla no es un detalle de presentación. Es la visita que se lleva las
     * fotos —`VisitImageEntity.ID` es llave primaria, así que la misma foto no
     * puede colgar de dos visitas— y el enlace con la recomendación —el par "qué
     * sugirió el sistema / qué hizo el cobrador" es UN hecho, no N—, y es la que
     * abre el ticket al terminar.
     *
     * El desempate es el `DOCTO_CC_ACR_ID` más bajo, el mismo criterio explícito
     * que ya usa `RegistroDeVisitaAdapter.cuentaDeAtribucion`: llave primaria,
     * total, sin empates posibles y estable entre corridas.
     */
    fun ancla(semilla: String, cuentas: Collection<Int>): String =
        cuentas.minOrNull()?.let { idDe(semilla, it) } ?: semilla
}

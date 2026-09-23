package com.example.msp_app.feature.ventacorreccion.domain.usecase

import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.evaluarCorregibilidad
import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort
import com.example.msp_app.feature.ventacorreccion.domain.port.RelojPort
import com.example.msp_app.feature.ventacorreccion.domain.port.VentaLocalCorreccionPort
import javax.inject.Inject

/**
 * Se lanza cuando el guardado se rechaza — SIEMPRE porque el guardia (dentro de
 * [VentaLocalCorreccionPort.guardarCorreccion]) devolvió `false`. Cuál de los dos guardias corrió
 * lo decide `ENVIADO`, leído dentro de la misma transacción:
 * - venta sin enviar → `commitEditGuard`, que sólo mira `ENVIADO = 0 AND CLAIM_ID = :claimId` —
 *   NO mira `LAST_UPLOAD_PERMANENT` (ese chequeo vive en `claimForEdit`, no aquí; corregido en la
 *   ronda 1 de arreglo de Task 3, este KDoc antes decía lo contrario). Sus dos únicas razones
 *   reales de rechazo son: la venta ya se envió, o el candado ya no es el del llamador (venció y
 *   alguien más lo tomó — de cualquier tipo).
 * - venta ya enviada → `commitEditGuardEnviada`, que agrega dos razones del nivel 2: ya hay una
 *   corrección esperando en la cola (`CORRECCION_REMOTA_PENDIENTE = 1`), o el servidor ya cerró
 *   la puerta en definitiva (`CORRECCION_REMOTA_ESTADO IS NOT NULL`).
 * [estado] clasifica la razón, releída INMEDIATAMENTE después del rechazo — puede ser
 * [EstadoCorreccion.Corregible] en el caso "candado ajeno pero la fila sigue abierta" (otra
 * sesión de EDICIÓN, reentrante, ganó la fila entre el guardia y esta lectura); en ese caso la
 * UI debe tratarlo igual que cualquier otro rechazo (la corrección de ESTE llamador no se
 * escribió), no como luz verde para reintentar con el mismo `claimId` vencido — ver
 * `CorreccionVentaViewModel.aTextoDeRechazoDeGuardado`, que usa
 * `TextosCorreccion.NO_SE_PUDO_GUARDAR` para esa rama en vez de `CORREGIR_VENTA`.
 */
class GuardadoRechazadoException(val estado: EstadoCorreccion) :
    Exception("No se pudo guardar la corrección: $estado")

/**
 * Guarda la corrección de forma atómica (guardia primero, en la MISMA transacción que campos +
 * merge de líneas + `clearUploadFailure` — ver [VentaLocalCorreccionPort.guardarCorreccion]).
 * La `Idempotency-Key` NUNCA se toca aquí (Global Constraint del plan): sólo se limpia el
 * registro de fallo previo, no se rota la llave — a diferencia del `EditLocalSaleViewModel`
 * legado que este plan reemplaza.
 *
 * El reencolado ([ReencolarSubidaPort.reencolar]) corre DESPUÉS del commit y es una
 * optimización pura: si lanza (p. ej. el proceso muere justo después de guardar), la corrección
 * YA quedó persistida — el barrido (`getUploadableSales`) la recoge en la siguiente apertura de
 * sesión, sin ayuda de este puerto.
 */
class GuardarCorreccion @Inject constructor(
    private val port: VentaLocalCorreccionPort,
    private val reloj: RelojPort,
    private val reencolar: ReencolarSubidaPort
) {
    suspend operator fun invoke(
        saleId: String,
        claimId: String,
        campos: CamposVentaCorregidos,
        productos: List<LocalSaleProductEntity>,
        combos: List<LocalSaleComboEntity>,
        userEmail: String
    ) {
        val guardado = port.guardarCorreccion(saleId, claimId, campos, productos, combos)
        if (!guardado) {
            throw GuardadoRechazadoException(estadoTrasRechazo(saleId))
        }

        // Optimización, nunca un requisito: la corrección ya está commiteada en Room. Si esto
        // lanza (proceso muerto, WorkManager caído), la corrección persiste igual.
        runCatching { reencolar.reencolar(saleId, userEmail) }
    }

    private suspend fun estadoTrasRechazo(saleId: String): EstadoCorreccion {
        val ahora = reloj.ahoraEpochMillis()
        // Defensivo: si la venta no existe (nunca debería pasar — el claimId vino de un
        // reclamo previo exitoso sobre una fila real), no hay una `EstadoCorreccion` que la
        // represente; `YaSeEnvio` es el mensaje menos accionable, el más seguro para no invitar
        // a reintentar contra una fila que no está.
        val estadoActual = port.leerEstado(saleId) ?: return EstadoCorreccion.YaSeEnvio
        return evaluarCorregibilidad(
            enviado = estadoActual.enviado,
            permanente = estadoActual.permanente,
            correccionNoEnviada = estadoActual.correccionNoEnviada,
            correccionRemotaPendiente = estadoActual.correccionRemotaPendiente,
            correccionRemotaEstado = estadoActual.correccionRemotaEstado,
            claimKind = estadoActual.claimKind,
            claimedAt = estadoActual.claimedAt,
            ahora = ahora
        )
    }
}

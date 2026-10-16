package com.example.msp_app.core.database.entities

import androidx.room.Entity

/**
 * Motivo del último fallo de envío de un pago o de una visita (E-APP-063).
 *
 * **Solo diagnóstico.** Ninguna consulta de envío, de pendientes ni de
 * reintento lee esta tabla: lo que decide si algo se manda o cuenta como
 * pendiente sigue viviendo en `Payment.GUARDADO_EN_MICROSIP` y
 * `Visit.GUARDADO_EN_MICROSIP`. La regla la cobra
 * `EnvioDiagnosticoAislamientoTest` (`:app`), que falla si alguien fuera de la
 * pantalla de pendientes, el depurador remoto o la telemetría la usa.
 *
 * A propósito NO es una columna en `Payment`/`Visit` (como hace
 * `LocalSaleEntity.LAST_UPLOAD_*`): `PaymentEntity` está vedado (spec
 * visitas-compromisos §7.1) y, en ventas locales, `LAST_UPLOAD_PERMANENT`
 * terminó DECIDIENDO pendientes — aquí no hay manera de que eso pase porque la
 * tabla no está en ningún `JOIN` de envío.
 *
 * @property TIPO `pago` o `visita` ([TIPO_PAGO] / [TIPO_VISITA]).
 * @property FILA_ID el `ID` de la fila de `Payment` o `Visit` (UUID local).
 * @property ULTIMO_ERROR_CODIGO código corto (`network_error`, `timeout`, el
 *   `code` del problem+json del servidor, `http_502`…); `null` tras un éxito.
 * @property ULTIMO_ERROR_MENSAJE mensaje corto en español para la pantalla.
 * @property ULTIMO_ERROR_HTTP estado HTTP; `0` cuando no hubo respuesta (red,
 *   tiempo agotado, excepción).
 * @property INTENTOS envíos observados (éxitos y fallos).
 * @property ULTIMO_INTENTO_AT instante del último envío observado (formato de cable UTC).
 * @property ULTIMO_EXITO_AT instante del último 2xx observado, o `null`.
 * @property REPORTADO_CODIGO códigos ya mandados a `RemoteLogger`, separados por
 *   coma: a lo más un evento por (fila, código), tope de 8 por fila (ver
 *   `EnvioDiagnosticoDao.registrarFallo`).
 */
@Entity(tableName = "envio_diagnostico", primaryKeys = ["TIPO", "FILA_ID"])
data class EnvioDiagnosticoEntity(
    val TIPO: String,
    val FILA_ID: String,
    val ULTIMO_ERROR_CODIGO: String?,
    val ULTIMO_ERROR_MENSAJE: String?,
    val ULTIMO_ERROR_HTTP: Int?,
    val INTENTOS: Int,
    val ULTIMO_INTENTO_AT: String?,
    val ULTIMO_EXITO_AT: String?,
    val REPORTADO_CODIGO: String?
) {
    companion object {
        const val TIPO_PAGO = "pago"
        const val TIPO_VISITA = "visita"
    }
}

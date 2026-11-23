package com.example.msp_app.feature.garantias.ui

import com.example.msp_app.feature.garantias.application.EstadoDeLaLectura
import com.example.msp_app.feature.garantias.application.FalloDeCaptura
import com.example.msp_app.feature.garantias.domain.BloqueoDeEntrega
import com.example.msp_app.feature.garantias.domain.model.EstadoDeCuenta
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio

/**
 * Todo el texto de usuario de la captura de garantías, en un solo lugar.
 *
 * ## Por qué un objeto y no literales sueltos
 *
 * El repositorio pide texto en español, con mayúscula inicial, de dos a cuatro
 * palabras y sin punto final. Repartir esas frases por cinco pantallas hace que
 * una corrección de redacción toque cinco archivos y que la próxima pantalla
 * invente otra variante. Acá el bloqueo del dominio se traduce **una vez** y la
 * pantalla sólo lo pinta.
 *
 * Las claves de [FalloDeCaptura], [BloqueoDeEntrega] y los estados son enums:
 * la pantalla no decide qué decir, elige la frase que ya existe.
 */
internal object TextosDeGarantias {

    // ── Encabezados ─────────────────────────────────────────────────────────

    const val TITULO_BANDEJA: String = "Mis folios"
    const val TITULO_ABRIR: String = "Abrir folio"
    const val TITULO_DETALLE: String = "Detalle del folio"
    const val TITULO_RECOLECCION: String = "Recolección"
    const val TITULO_ENTREGA: String = "Entrega con firma"
    const val VOLVER: String = "Volver"

    // ── Bandeja ─────────────────────────────────────────────────────────────

    const val NUEVO_FOLIO: String = "Abrir folio"
    const val SIN_FOLIOS: String = "Sin folios todavía"
    const val SIN_FOLIOS_PIE: String = "Abre el primero para empezar"
    const val RECARGAR: String = "Volver a cargar"
    const val CARGANDO: String = "Cargando"
    const val PENDIENTES: String = "Por enviar"
    const val NO_SE_PUDO_LEER: String = "No se pudo leer"

    // ── Abrir folio ─────────────────────────────────────────────────────────

    const val ORIGEN: String = "Origen"
    const val ORIGEN_PISO: String = "Piso"
    const val ORIGEN_CLIENTE: String = "Cliente"
    const val BUSCAR_CLIENTE: String = "Buscar cliente"
    const val NOMBRE_DEL_CLIENTE: String = "Nombre del cliente"
    const val SIN_CLIENTES: String = "Sin coincidencias"
    const val CLIENTE_ELEGIDO: String = "Cliente elegido"
    const val SIN_VENTA: String = "Sin venta ligada"
    const val ESTADO_DE_CUENTA: String = "Estado de cuenta"
    const val CUENTA_LIQUIDADA: String = "Liquidada"
    const val CUENTA_CON_SALDO: String = "Con saldo"
    const val DOMICILIO: String = "Domicilio"
    const val CALLE: String = "Calle"
    const val NUMERO: String = "Número"
    const val COLONIA: String = "Colonia"
    const val LOCALIDAD: String = "Localidad"
    const val CIUDAD: String = "Ciudad"
    const val CODIGO_POSTAL: String = "Código postal"
    const val DESCRIPCION_DEL_FOLIO: String = "¿Qué le pasó?"
    const val DESCRIPCION_DEL_FOLIO_MARCADOR: String = "Falla reportada por el cliente"
    const val CLAVE_DEL_ARTICULO: String = "Clave del artículo"
    const val DESCRIPCION_DEL_ARTICULO: String = "Descripción del artículo"
    const val VIGENCIA: String = "Vigencia (AAAA-MM-DD)"
    const val GUARDAR_FOLIO: String = "Abrir y guardar"
    const val GUARDANDO: String = "Guardando folio"
    const val PRIMER_ARTICULO: String = "Primer artículo"
    const val NO_SE_PUDO_GUARDAR: String = "No se pudo guardar"

    // ── Detalle ─────────────────────────────────────────────────────────────

    const val LINEA_DE_TIEMPO: String = "Línea de tiempo"
    const val SIN_EVENTOS: String = "Sin eventos todavía"
    const val ARTICULOS: String = "Artículos"
    const val SIN_ARTICULOS: String = "Sin artículos"
    const val RECOLECTAR: String = "Recolectar"
    const val ENTREGAR: String = "Entregar"
    const val FOLIO_CERRADO: String = "Folio cerrado"
    const val SIN_SENAL: String = "Sin señal"
    const val PENDIENTE_ENVIO: String = "Pendiente de envío"
    const val FOLIO_RECHAZADO: String = "Folio rechazado"
    const val FOLIO_DESCONOCIDO: String = "Folio desconocido"
    const val UBICACION_REGISTRADA: String = "Ubicación registrada"
    const val SIN_UBICACION: String = "Sin ubicación"
    const val IMAGENES: String = "Imágenes"
    const val ETAPA: String = "Etapa"

    // ── Recolección ─────────────────────────────────────────────────────────

    const val FOTOS: String = "Fotos"
    const val TOMAR_FOTO: String = "Tomar foto"
    const val SIN_FOTOS: String = "Sin fotos"
    const val UBICACION_DE_LA_RECOLECCION: String = "Ubicación de la recolección"
    const val PEDIR_UBICACION: String = "Tomar ubicación"
    const val GUARDAR_RECOLECCION: String = "Guardar recolección"
    const val GUARDANDO_RECOLECCION: String = "Guardando recolección"
    const val SIN_CATALOGO: String = "Sin catálogo disponible"
    const val SIN_ACCIONES: String = "Sin acciones disponibles"
    const val QUITAR: String = "Quitar"
    const val ACCION: String = "Acción"

    // ── Entrega ─────────────────────────────────────────────────────────────

    const val FIRMA: String = "Firma del cliente"
    const val FIRMA_MARCADOR: String = "Firma aquí"
    const val LIMPIAR_FIRMA: String = "Limpiar firma"
    const val UBICACION_DE_LA_ENTREGA: String = "Ubicación de la entrega"
    const val ESPERANDO_UBICACION: String = "Esperando ubicación"
    const val GUARDAR_ENTREGA: String = "Guardar entrega"
    const val GUARDANDO_ENTREGA: String = "Guardando entrega"
    const val ENTREGA_GUARDADA: String = "Entrega guardada"
    const val RECOLECCION_GUARDADA: String = "Recolección guardada"

    // ── Traducciones de estado ──────────────────────────────────────────────

    /** La frase que el cobrador lee para un folio de origen [OrigenDeFolio]. */
    fun textoDe(origen: OrigenDeFolio): String = when (origen) {
        OrigenDeFolio.PISO -> ORIGEN_PISO
        OrigenDeFolio.CLIENTE -> ORIGEN_CLIENTE
    }

    /** El estado local del folio, tal como lo pinta la bandeja. */
    fun textoDe(estado: EstadoLocalDeFolio): String = when (estado) {
        EstadoLocalDeFolio.PENDIENTE_ENVIO -> PENDIENTE_ENVIO
        EstadoLocalDeFolio.ABIERTO -> "Abierto"
        EstadoLocalDeFolio.RECHAZADO -> FOLIO_RECHAZADO
    }

    /** El estado de cuenta del cliente. */
    fun textoDe(estado: EstadoDeCuenta): String = when (estado) {
        EstadoDeCuenta.LIQUIDADA -> CUENTA_LIQUIDADA
        EstadoDeCuenta.SALDO_PENDIENTE -> CUENTA_CON_SALDO
    }

    /** El aviso de la cabecera del detalle cuando el folio no está completo. */
    fun textoDe(estado: EstadoDeLaLectura): String = when (estado) {
        EstadoDeLaLectura.DISPONIBLE -> ""
        EstadoDeLaLectura.SIN_SENAL -> SIN_SENAL
        EstadoDeLaLectura.PENDIENTE_ENVIO -> PENDIENTE_ENVIO
        EstadoDeLaLectura.RECHAZADO -> FOLIO_RECHAZADO
        EstadoDeLaLectura.NO_ENCONTRADO -> FOLIO_DESCONOCIDO
    }

    /** Por qué una captura no se encoló. */
    fun textoDe(fallo: FalloDeCaptura): String = when (fallo) {
        FalloDeCaptura.SIN_DESCRIPCION -> "Falta la descripción"
        FalloDeCaptura.SIN_CLAVE_DEL_ARTICULO -> "Falta la clave"
        FalloDeCaptura.SIN_DESCRIPCION_DEL_ARTICULO -> "Falta describir el artículo"
        FalloDeCaptura.SIN_DATOS_DEL_CLIENTE -> "Faltan datos del cliente"
        FalloDeCaptura.FOLIO_DESCONOCIDO -> FOLIO_DESCONOCIDO
        FalloDeCaptura.FOLIO_RECHAZADO -> FOLIO_RECHAZADO
        FalloDeCaptura.SIN_ACCION -> SIN_ACCIONES
        FalloDeCaptura.SIN_UBICACION -> ESPERANDO_UBICACION
        FalloDeCaptura.SIN_FIRMA -> "Falta la firma"
        FalloDeCaptura.FIRMA_VACIA -> "Firma vacía"
        FalloDeCaptura.FIRMA_MUY_GRANDE -> "Firma muy grande"
        FalloDeCaptura.FIRMA_NO_GUARDADA -> "No se guardó la firma"
        FalloDeCaptura.FOTO_NO_GUARDADA -> "No se guardó la foto"
    }

    /** Por qué todavía no se puede entregar. */
    fun textoDe(bloqueo: BloqueoDeEntrega): String = when (bloqueo) {
        BloqueoDeEntrega.SIN_UBICACION -> ESPERANDO_UBICACION
        BloqueoDeEntrega.SIN_FIRMA -> "Falta la firma"
        BloqueoDeEntrega.FIRMA_VACIA -> "Firma vacía"
        BloqueoDeEntrega.FIRMA_MUY_GRANDE -> "Firma muy grande"
    }
}

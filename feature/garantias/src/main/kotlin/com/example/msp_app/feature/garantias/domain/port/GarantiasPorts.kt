package com.example.msp_app.feature.garantias.domain.port

import com.example.msp_app.feature.garantias.domain.ResultadoDeLaLlamada
import com.example.msp_app.feature.garantias.domain.model.CatalogoDeGarantias
import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ComandoNuevo
import com.example.msp_app.feature.garantias.domain.model.CuerpoHttp
import com.example.msp_app.feature.garantias.domain.model.DetalleDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.FirmaDeConformidad
import com.example.msp_app.feature.garantias.domain.model.FolioSeguido
import com.example.msp_app.feature.garantias.domain.model.FotoNueva
import com.example.msp_app.feature.garantias.domain.model.FotoPendienteDeGarantia
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import java.time.Instant

/**
 * La cola de comandos de garantías: lo que el teléfono guardó y todavía no ha
 * entregado.
 *
 * ## Lo que este puerto NO deja hacer
 *
 * No hay `actualizar`, ni `borrar`, ni un `guardar` que reciba un comando
 * usado. Un comando encolado **no se edita**: [encolar] recibe un
 * [ComandoNuevo] y las únicas escrituras posteriores son de estado
 * ([marcarEnviado], [marcarRechazado], [anotarIntento]) y de resolución de ids
 * ([ResolucionDeReferenciasPort]). Esa ausencia es la garantía de D3 y la que
 * hace que un reintento mande los mismos bytes con la misma clave.
 *
 * ## Por qué el puerto está partido en tres
 *
 * `TooManyFunctions` de detekt cuenta las funciones de una interfaz sin
 * excepción. La cola tiene tres responsabilidades distintas —leer y encolar,
 * anotar el resultado, resolver referencias locales— y separarlas deja cada
 * contrato legible y cada fake pequeño. El adaptador de `data/` puede
 * implementar los tres sobre el mismo DAO.
 */
interface ColaDeComandosPort {

    /**
     * Guarda [comando] y sus [fotos] en **una sola transacción** y devuelve el
     * id local del comando.
     *
     * La transacción cubre la fila del comando y las fotos; si la foto falla,
     * el comando no queda a medias. La foto **nunca** bloquea el guardado del
     * comando: se guarda con él o el guardado entero se revierte, y el
     * llamador decide qué mostrar.
     *
     * [ComandoNuevo.clave], [ComandoNuevo.cuerpo] y
     * [ComandoNuevo.deviceCreatedAt] llegan congelados y se escriben tal cual.
     */
    suspend fun encolar(comando: ComandoNuevo, fotos: List<FotoNueva> = emptyList()): Long

    /** Todo lo que todavía puede viajar, en orden global de encolado. */
    suspend fun pendientes(): List<ComandoDeGarantia>

    /**
     * Los comandos de un folio local, en el orden en que se guardaron.
     *
     * Es la consulta que impide que un comando adelante a su anterior del mismo
     * folio (D4, mutante A5). Devuelve también los enviados y los rechazados:
     * el trabajador necesita ver el rechazo para detener a los que dependen.
     */
    suspend fun delFolio(folioRef: String): List<ComandoDeGarantia>

    /** Un comando por su clave de idempotencia, o `null` si no está. */
    suspend fun porClave(clave: String): ComandoDeGarantia?

    /** El servidor lo tomó. No cambia ni la clave ni el cuerpo. */
    suspend fun marcarEnviado(id: Long, ahora: Instant)

    /** Rechazo definitivo: no se reintenta y la pantalla lo muestra. */
    suspend fun marcarRechazado(id: Long, error: String, ahora: Instant)

    /** El intento falló pero se reintenta: sólo suma el contador y el error. */
    suspend fun anotarIntento(id: Long, error: String, ahora: Instant)
}

/**
 * Escribe los ids que el servidor devolvió en los comandos que los esperaban.
 *
 * Son escrituras de **resolución**, no de contenido: la clave, el cuerpo, la
 * hora y la ruta no se tocan. Cada método devuelve cuántas filas quedaron
 * resueltas, que es la señal de que el comando dependiente ya puede salir.
 */
interface ResolucionDeReferenciasPort {

    /** `abrir` respondió: el `id` del folio se propaga a sus dependientes. */
    suspend fun resolverFolio(folioRef: String, folioId: String, ahora: Instant): Int

    /** `agregar-articulo` respondió: se propaga el `articulo_ref` del servidor. */
    suspend fun resolverArticulo(articuloRef: String, articuloId: String, ahora: Instant): Int

    /** La recolección respondió: se propaga el evento al que se cuelgan fotos. */
    suspend fun resolverEvento(eventoRef: String, eventoId: String, ahora: Instant): Int
}

/**
 * Las fotos en espera de subir.
 *
 * [FotoNueva.clave] se genera una sola vez, al tomar la foto, y es también el
 * `id` de la imagen en el servidor. Este puerto no tiene un `guardar` que
 * reciba una foto ya subida: la fila nace pendiente y sólo cambia de estado.
 */
interface FotosDeGarantiaPort {

    /** Guarda una foto pendiente; [comandoId] la liga al comando que la produjo. */
    suspend fun guardar(foto: FotoNueva, comandoId: Long? = null)

    /** Igual que [guardar], para el lote de una recolección. */
    suspend fun guardarTodas(fotos: List<FotoNueva>, comandoId: Long? = null)

    /** Todas las pendientes, en orden de captura. */
    suspend fun pendientes(): List<FotoPendienteDeGarantia>

    /** Las que ya tienen el `id` del evento y por tanto pueden subir. */
    suspend fun listasParaSubir(): List<FotoPendienteDeGarantia>

    suspend fun porClave(clave: String): FotoPendienteDeGarantia?

    suspend fun pendientesCount(): Int

    /**
     * Las rutas que alguna fila todavía referencia. El barrido de archivos
     * huérfanos no puede borrar ninguna de estas.
     */
    suspend fun rutasVivas(): List<String>
}

/**
 * El estado de una foto después de intentar subirla.
 *
 * Se separa de [FotosDeGarantiaPort] por la misma razón que la cola: leer y
 * escribir estados son contratos distintos. La regla dura es que [eliminar]
 * sólo se llama **después** de [marcarSubida] (D3, mutante A7).
 */
interface EstadoDeLasFotosPort {

    /** El servidor confirmó la imagen: recién ahora puede borrarse el archivo. */
    suspend fun marcarSubida(clave: String, ahora: Instant)

    /** Rechazo definitivo: la fila y su archivo quedan visibles, no se borran. */
    suspend fun marcarRechazada(clave: String, error: String, ahora: Instant)

    /** El intento falló pero se reintenta. */
    suspend fun anotarIntento(clave: String, error: String, ahora: Instant)

    /** El evento ya existe: se propaga a las fotos que esperaban esa referencia. */
    suspend fun resolverEvento(
        eventoRef: String,
        eventoId: String,
        folioId: String,
        ahora: Instant
    ): Int

    /** Sólo después de [marcarSubida]: borra la fila y su archivo. */
    suspend fun eliminar(clave: String)
}

/**
 * Los folios que **este teléfono** abrió o tocó: la memoria de "mis folios".
 *
 * El API no tiene filtro por usuario, así que la bandeja no se pide al
 * servidor; se arma con estas filas y cada una se completa con
 * [GarantiasRemotasPort.detalle] cuando ya tiene `id` remoto. Los que todavía
 * no salieron se muestran como "Pendiente de envío" (D5).
 *
 * [FolioSeguido.id] es local y **nunca** viaja como `id` del folio.
 */
interface FoliosSeguidosPort {

    suspend fun guardar(folio: FolioSeguido)

    /** La bandeja, del más reciente al más viejo. */
    suspend fun todos(): List<FolioSeguido>

    suspend fun porId(id: String): FolioSeguido?

    suspend fun porFolioId(folioId: String): FolioSeguido?

    /** El folio ya existe en el servidor: guarda su `id` y lo marca abierto. */
    suspend fun marcarAbierto(id: String, folioId: String, ahora: Instant)

    /** `pendiente_envio`, `abierto` o `rechazado`, con su último error. */
    suspend fun marcarEstado(id: String, estado: EstadoLocalDeFolio, error: String?, ahora: Instant)

    suspend fun pendientesDeEnvio(): Int
}

/**
 * La tabla de acciones válidas que publica el servidor.
 *
 * Hoy devuelve [CatalogoDeGarantias.SIN_CATALOGO] porque
 * `GET /v2/garantias/catalogos` **no existe todavía** en `msp-api`. La pieza es
 * intercambiable: el día que exista, su adaptador lee la caché de Room o el
 * endpoint y **ninguna pantalla cambia** (D2). Mientras tanto, la pantalla de
 * recolección no ofrece acciones y lo dice.
 */
interface CatalogoDeGarantiasPort {
    suspend fun leer(): CatalogoDeGarantias
}

/**
 * El API de garantías v2: mandar comandos, leer el detalle y subir imágenes.
 *
 * [enviar] recibe el comando **ya serializado**: la clave, el método, la ruta
 * resuelta y los bytes congelados. No reconstruye el cuerpo ni la clave (D3);
 * la cola es la única que decide qué se manda y este puerto sólo lo transporta.
 *
 * [detalle] es el `GET /v2/garantias/{id}` con su línea de tiempo. Es la única
 * forma de saber qué artículo creó un `agregar` y qué evento creó una
 * recolección: la respuesta del comando no trae eventos (D4). Puede lanzar
 * `IOException`; el llamador decide si espera o muestra el último estado
 * conocido.
 *
 * [subirImagen] recibe los bytes del multipart ya armados con el boundary fijo
 * y la clave de la foto. El archivo se leyó antes de llamar, así que un
 * reintento manda exactamente los mismos bytes (D3, mutantes A1 y A2).
 */
interface GarantiasRemotasPort {

    /** Manda un comando con su clave y sus bytes. Nunca los rearma. */
    suspend fun enviar(
        clave: String,
        metodo: String,
        ruta: String,
        cuerpo: CuerpoHttp
    ): ResultadoDeLaLlamada

    /** El folio completo, con eventos e imágenes, tal como lo devuelve el API. */
    suspend fun detalle(folioId: String): DetalleDeGarantia

    /** Sube una foto con su propia clave; el cuerpo ya viene armado. */
    suspend fun subirImagen(clave: String, ruta: String, cuerpo: CuerpoHttp): ResultadoDeLaLlamada
}

/**
 * Los archivos locales que la captura produce y consume: la firma del cliente y
 * las fotos de la recolección.
 *
 * El dominio no conoce `File` ni `filesDir`; este puerto traduce una ruta
 * guardada en Room a los bytes que viajan. [leer] devuelve `null` cuando el
 * archivo ya no está (barrido, reinstalación, error de escritura) y la cola lo
 * trata como un rechazo visible, nunca como una subida exitosa.
 *
 * [borrar] se llama **sólo después** de que el servidor confirmó la subida
 * (D3, mutante A7). Borrar antes dejaría la foto irrecuperable y la fila
 * apuntando a la nada.
 */
interface ArchivosDeGarantiaPort {

    /** Los bytes del archivo, o `null` si ya no existe o no se puede leer. */
    suspend fun leer(ruta: String): ByteArray?

    /** Borra el archivo. Un archivo ya ausente no es un error. */
    suspend fun borrar(ruta: String)
}

/**
 * La ubicación del teléfono en el momento de la captura.
 *
 * Devuelve `null` cuando el permiso está negado, el proveedor no contesta o el
 * hardware falla. **Nunca bloquea la recolección**; sí bloquea la entrega,
 * porque el servidor la exige (D5). El adaptador vive en `:app` porque necesita
 * `Context` y Play Services.
 */
interface UbicacionDeGarantiaPort {
    suspend fun ubicacionActual(): Ubicacion?
}

/**
 * Escribe la firma del cliente en `filesDir` y la borra si el cobrador se
 * arrepiente.
 *
 * El puerto recibe los **bytes del PNG**, no un `Bitmap`: el dominio no conoce
 * Android y la regla de tamaño se evalúa sobre el archivo, que es lo que viaja.
 * `null` significa que el archivo no se pudo escribir y la entrega no se
 * encola.
 */
interface FirmaDeConformidadPort {
    suspend fun guardarPng(bytes: ByteArray): FirmaDeConformidad?

    suspend fun borrar(ruta: String)
}

/**
 * Pide que el trabajador de la cola corra.
 *
 * Es un puerto y no una llamada directa a WorkManager porque el módulo no
 * depende de `:app`: el encolado real vive en `PendingGarantiasV2Worker` y usa
 * nombre único con `ExistingWorkPolicy.KEEP` (D3, mutante A10).
 */
interface EncoladoDeGarantiasPort {
    fun encolar()
}

/**
 * Genera claves de idempotencia.
 *
 * Una clave se genera **una vez**, al guardar, y no se vuelve a generar. El
 * puerto existe para que las pruebas usen claves previsibles sin tocar el
 * código de producción.
 */
interface GeneradorDeClavesPort {
    fun nueva(): String
}

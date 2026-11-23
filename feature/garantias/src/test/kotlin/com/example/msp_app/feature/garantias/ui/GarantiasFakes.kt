package com.example.msp_app.feature.garantias.ui

import com.example.msp_app.feature.garantias.domain.ResultadoDeLaLlamada
import com.example.msp_app.feature.garantias.domain.model.AperturaDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ArticuloDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ArticuloNuevo
import com.example.msp_app.feature.garantias.domain.model.CatalogoDeGarantias
import com.example.msp_app.feature.garantias.domain.model.ClienteDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ComandoNuevo
import com.example.msp_app.feature.garantias.domain.model.CuerpoDeComando
import com.example.msp_app.feature.garantias.domain.model.CuerpoHttp
import com.example.msp_app.feature.garantias.domain.model.DetalleDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EstadoDeComando
import com.example.msp_app.feature.garantias.domain.model.EstadoDeFoto
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.EventoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.FirmaDeConformidad
import com.example.msp_app.feature.garantias.domain.model.FolioSeguido
import com.example.msp_app.feature.garantias.domain.model.FotoDeCaptura
import com.example.msp_app.feature.garantias.domain.model.FotoNueva
import com.example.msp_app.feature.garantias.domain.model.FotoPendienteDeGarantia
import com.example.msp_app.feature.garantias.domain.model.OperacionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import com.example.msp_app.feature.garantias.domain.model.RutasDeGarantia
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.example.msp_app.feature.garantias.domain.port.ArchivosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.CamaraDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.CatalogoDeGarantiasPort
import com.example.msp_app.feature.garantias.domain.port.ClientesDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.ColaDeComandosPort
import com.example.msp_app.feature.garantias.domain.port.CuerposDeComandoPort
import com.example.msp_app.feature.garantias.domain.port.EncoladoDeGarantiasPort
import com.example.msp_app.feature.garantias.domain.port.FirmaDeConformidadPort
import com.example.msp_app.feature.garantias.domain.port.FoliosSeguidosPort
import com.example.msp_app.feature.garantias.domain.port.FotosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.GarantiasRemotasPort
import com.example.msp_app.feature.garantias.domain.port.GeneradorDeClavesPort
import com.example.msp_app.feature.garantias.domain.port.UbicacionDeGarantiaPort
import java.io.IOException
import java.time.Instant

/**
 * Los dobles de los puertos de la captura para las pruebas de ViewModel (prueba
 * 7 del brief).
 *
 * Son fakes escritos a mano —sin MockK ni Mockito, como el resto del repo— y
 * registran lo que recibieron para que la prueba pueda afirmar sobre el efecto,
 * no sobre la llamada. Cada uno permite sembrar el estado inicial y forzar el
 * fallo, que es lo que hace falta para pintar "cargando", "con datos", "sin
 * señal con pendientes" y "rechazado".
 */
internal class FoliosFalsos(
    private val folios: MutableList<FolioSeguido> = mutableListOf()
) : FoliosSeguidosPort {

    val guardados = mutableListOf<FolioSeguido>()

    /** Una base que no abre: la lectura revienta y la pantalla anota el fallo. */
    var falla = false

    override suspend fun guardar(folio: FolioSeguido) {
        folios += folio
        guardados += folio
    }

    override suspend fun todos(): List<FolioSeguido> {
        check(!falla) { "la base no abre" }
        return folios
    }

    override suspend fun porId(id: String): FolioSeguido? {
        check(!falla) { "la base no abre" }
        return folios.firstOrNull { it.id == id }
    }

    override suspend fun porFolioId(folioId: String): FolioSeguido? =
        folios.firstOrNull { it.folioId == folioId }

    override suspend fun marcarAbierto(id: String, folioId: String, ahora: Instant) = Unit

    override suspend fun marcarEstado(
        id: String,
        estado: EstadoLocalDeFolio,
        error: String?,
        ahora: Instant
    ) = Unit

    override suspend fun pendientesDeEnvio(): Int =
        folios.count { it.estadoLocal == EstadoLocalDeFolio.PENDIENTE_ENVIO }
}

/** La cola de comandos: siembra filas para leer y registra lo que se encola. */
internal class ColaFalsa(
    private val comandos: MutableList<ComandoDeGarantia> = mutableListOf(),
    private val fotosPort: FotosFalsas? = null
) : ColaDeComandosPort {

    val encolados = mutableListOf<ComandoNuevo>()
    val fotosEncoladas = mutableListOf<FotoNueva>()

    /** Una base que no abre al escribir: el caso de uso revienta y la pantalla lo anota. */
    var fallaAlEncolar = false

    override suspend fun encolar(comando: ComandoNuevo, fotos: List<FotoNueva>): Long {
        check(!fallaAlEncolar) { "la base no abre" }
        encolados += comando
        fotosEncoladas += fotos
        val id = encolados.size.toLong()
        fotosPort?.guardarTodas(fotos, id)
        return id
    }

    override suspend fun pendientes(): List<ComandoDeGarantia> =
        comandos.filter { it.estado == EstadoDeComando.PENDIENTE }

    override suspend fun delFolio(folioRef: String): List<ComandoDeGarantia> =
        comandos.filter { it.folioRef == folioRef }.sortedBy { it.id }

    override suspend fun porClave(clave: String): ComandoDeGarantia? =
        comandos.firstOrNull { it.clave == clave }

    override suspend fun marcarEnviado(id: Long, ahora: Instant) = Unit

    override suspend fun marcarRechazado(id: Long, error: String, ahora: Instant) = Unit

    override suspend fun anotarIntento(id: Long, error: String, ahora: Instant) = Unit
}

/** Las fotos en espera: siembra las pendientes y registra las que se guardan. */
internal class FotosFalsas(
    private val fotos: MutableList<FotoPendienteDeGarantia> = mutableListOf()
) : FotosDeGarantiaPort {

    val guardadas = mutableListOf<FotoNueva>()

    override suspend fun guardar(foto: FotoNueva, comandoId: Long?) {
        guardadas += foto
    }

    override suspend fun guardarTodas(fotos: List<FotoNueva>, comandoId: Long?) {
        guardadas += fotos
    }

    override suspend fun pendientes(): List<FotoPendienteDeGarantia> = fotos

    override suspend fun listasParaSubir(): List<FotoPendienteDeGarantia> =
        fotos.filter { it.eventoId != null }

    override suspend fun porClave(clave: String): FotoPendienteDeGarantia? =
        fotos.firstOrNull { it.clave == clave }

    override suspend fun pendientesCount(): Int = fotos.size

    override suspend fun rutasVivas(): List<String> = fotos.map { it.rutaArchivo }
}

/** El API remoto: devuelve el detalle preparado o simula que no hay señal. */
internal class RemotasFalsas(
    var detalle: DetalleDeGarantia? = null,
    var sinSenal: Boolean = false
) : GarantiasRemotasPort {

    override suspend fun enviar(
        clave: String,
        metodo: String,
        ruta: String,
        cuerpo: CuerpoHttp
    ): ResultadoDeLaLlamada = error("la captura no envía comandos")

    override suspend fun detalle(folioId: String): DetalleDeGarantia {
        if (sinSenal) throw IOException("sin señal")
        return checkNotNull(detalle) { "la prueba no preparó el detalle de $folioId" }
    }

    override suspend fun subirImagen(
        clave: String,
        ruta: String,
        cuerpo: CuerpoHttp
    ): ResultadoDeLaLlamada = error("la captura no sube imágenes")
}

/** Serializa los cuerpos con texto fijo: la prueba no compara el JSON acá. */
internal class CuerposFalsos : CuerposDeComandoPort {

    override fun apertura(apertura: AperturaDeGarantia, deviceCreatedAt: Instant): CuerpoDeComando =
        CuerpoDeComando(texto = """{"apertura":true}""", tipoDeContenido = TIPO_JSON)

    override fun articulo(articulo: ArticuloNuevo, deviceCreatedAt: Instant): CuerpoDeComando =
        CuerpoDeComando(texto = """{"articulo":true}""", tipoDeContenido = TIPO_JSON)

    override fun avance(
        etapaDestino: String,
        deviceCreatedAt: Instant,
        ubicacion: Ubicacion?
    ): CuerpoDeComando = CuerpoDeComando(
        texto = """{"etapa":"$etapaDestino"}""",
        tipoDeContenido = TIPO_JSON
    )

    override fun entrega(
        deviceCreatedAt: Instant,
        ubicacion: Ubicacion,
        firma: FirmaDeConformidad
    ): CuerpoDeComando = CuerpoDeComando(
        texto = """{"entrega":true}""",
        tipoDeContenido = TIPO_MULTIPART
    )

    private companion object {
        const val TIPO_JSON = "application/json"
        const val TIPO_MULTIPART = "multipart/form-data; boundary=msp-garantias-v2-7d3f1a"
    }
}

/** Cuenta cuántas veces la captura pidió despertar al trabajador. */
internal class EncoladoFalso : EncoladoDeGarantiasPort {

    var veces = 0

    override fun encolar() {
        veces++
    }
}

/** Claves previsibles para comparar rutas y referencias sin adivinar el UUID. */
internal class ClavesFalsas : GeneradorDeClavesPort {

    private var siguiente = 0

    override fun nueva(): String {
        siguiente++
        return "clave-$siguiente"
    }
}

/** El catálogo del servidor, intercambiable por prueba. */
internal class CatalogoFalso(
    var catalogo: CatalogoDeGarantias = CatalogoDeGarantias.SIN_CATALOGO
) : CatalogoDeGarantiasPort {

    override suspend fun leer(): CatalogoDeGarantias = catalogo
}

/** La cámara: entrega un destino fijo y archiva la foto preparada (o `null`). */
internal class CamaraFalsa(
    var foto: FotoDeCaptura? = FotoDeCaptura("foto-1", "fotos/foto-1.jpg", "image/jpeg")
) : CamaraDeGarantiaPort {

    val archivados = mutableListOf<String>()

    override fun nuevoDestino(): String = DESTINO

    override suspend fun archivar(uri: String): FotoDeCaptura? {
        archivados += uri
        return foto
    }

    private companion object {
        const val DESTINO = "content://com.example.msp_app.fileprovider/camara/1"
    }
}

/** El GPS del cobrador: presente o ausente, sin hardware. */
internal class UbicacionFalsa(
    var ubicacion: Ubicacion? = Ubicacion(latitud = 19.4326, longitud = -99.1332)
) : UbicacionDeGarantiaPort {

    override suspend fun ubicacionActual(): Ubicacion? = ubicacion
}

/** Los archivos locales: registra los borrados y no lee bytes. */
internal class ArchivosFalsos : ArchivosDeGarantiaPort {

    val borrados = mutableListOf<String>()

    override suspend fun leer(ruta: String): ByteArray? = null

    override suspend fun borrar(ruta: String) {
        borrados += ruta
    }
}

/** La firma del cliente: registra los bytes escritos y los borrados. */
internal class FirmasFalsas(
    var firma: FirmaDeConformidad? = FirmaDeConformidad(
        rutaArchivo = "firmas/firma.png",
        tamanoBytes = 128
    )
) : FirmaDeConformidadPort {

    val guardadas = mutableListOf<ByteArray>()
    val borradas = mutableListOf<String>()

    override suspend fun guardarPng(bytes: ByteArray): FirmaDeConformidad? {
        guardadas += bytes
        return firma
    }

    override suspend fun borrar(ruta: String) {
        borradas += ruta
    }
}

/** El padrón de clientes: filtra por nombre y puede fallar al leer. */
internal class ClientesFalsos(
    var clientes: List<ClienteDeGarantia> = emptyList(),
    var falla: Boolean = false
) : ClientesDeGarantiaPort {

    override suspend fun buscar(nombre: String, limite: Int): List<ClienteDeGarantia> {
        check(!falla) { "el padrón no abre" }
        return clientes.filter { it.nombre.contains(nombre, ignoreCase = true) }.take(limite)
    }
}

/** Datos de prueba con la forma de las tablas y del detalle remoto. */
internal object GarantiasFixtures {

    /** Un instante fijo: las fechas no dependen del reloj de la máquina. */
    val AHORA: Instant = Instant.parse("2026-11-13T10:00:00Z")

    fun folio(
        id: String = "folio-local-1",
        folioId: String? = null,
        origen: OrigenDeFolio = OrigenDeFolio.PISO,
        estadoLocal: EstadoLocalDeFolio = EstadoLocalDeFolio.PENDIENTE_ENVIO,
        ultimoError: String? = null
    ): FolioSeguido = FolioSeguido(
        id = id,
        folioId = folioId,
        origen = origen,
        estadoLocal = estadoLocal,
        abiertoEn = AHORA,
        actualizadoEn = AHORA,
        ultimoError = ultimoError
    )

    fun comando(
        id: Long,
        folioRef: String = "folio-local-1",
        estado: EstadoDeComando = EstadoDeComando.PENDIENTE,
        articuloRef: String? = null,
        articuloId: String? = null,
        ultimoError: String? = null
    ): ComandoDeGarantia = ComandoDeGarantia(
        id = id,
        clave = "clave-$id",
        operacion = OperacionDeGarantia.AVANZAR_ARTICULO,
        metodo = "POST",
        ruta = RutasDeGarantia.AVANZAR_ARTICULO,
        cuerpo = """{"etapa":"recolectado"}""",
        contentType = "application/json",
        deviceCreatedAt = AHORA,
        estado = estado,
        intentos = 0,
        creadoEn = AHORA,
        actualizadoEn = AHORA,
        ultimoError = ultimoError,
        folioRef = folioRef,
        articuloRef = articuloRef,
        articuloId = articuloId
    )

    fun fotoPendiente(
        clave: String = "foto-1",
        folioRef: String = "folio-local-1",
        eventoId: String? = null
    ): FotoPendienteDeGarantia = FotoPendienteDeGarantia(
        clave = clave,
        rutaArchivo = "fotos/$clave.jpg",
        mime = "image/jpeg",
        estado = EstadoDeFoto.PENDIENTE,
        intentos = 0,
        creadoEn = AHORA,
        actualizadoEn = AHORA,
        folioRef = folioRef,
        eventoId = eventoId
    )

    fun detalle(
        id: String = "folio-servidor-1",
        origen: OrigenDeFolio = OrigenDeFolio.PISO,
        estado: String = "abierto",
        articulos: List<ArticuloDeGarantia> = emptyList(),
        eventos: List<EventoDeGarantia> = emptyList()
    ): DetalleDeGarantia = DetalleDeGarantia(
        id = id,
        folio = "G-0001",
        origen = origen,
        estado = estado,
        descripcion = "bocina",
        abiertoPor = "agente",
        creadoEn = AHORA,
        actualizadoEn = AHORA,
        articulos = articulos,
        eventos = eventos
    )

    fun articulo(
        id: String = "articulo-servidor-1",
        etapa: String = "registrado"
    ): ArticuloDeGarantia = ArticuloDeGarantia(
        id = id,
        rol = "original",
        clave = "clave-del-articulo",
        descripcion = "bocina",
        etapa = etapa,
        ubicacion = "taller",
        creadoEn = AHORA,
        actualizadoEn = AHORA
    )

    fun evento(
        id: String,
        tipo: String = "recoleccion",
        articuloRef: String? = null
    ): EventoDeGarantia = EventoDeGarantia(
        id = id,
        articuloRef = articuloRef,
        tipo = tipo,
        descripcion = "evento",
        usuario = "agente",
        creadoEn = AHORA,
        deviceCreatedAt = AHORA,
        claveIdempotencia = "clave-$id"
    )
}

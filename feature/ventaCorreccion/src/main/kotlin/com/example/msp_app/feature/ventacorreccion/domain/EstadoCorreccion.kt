package com.example.msp_app.feature.ventacorreccion.domain

/**
 * Estado de corregibilidad de una venta local, la ÚNICA regla que decide si
 * el botón "Corregir venta" se ofrece — no depende de Android ni de Room
 * (plan "Corregir una venta antes de que suba", invariante de la Task 2;
 * ampliado por "Corregir una venta DESPUÉS de que subió, mientras siga en
 * borrador", que agregó [CorregibleEnviada], [CorreccionEnCamino] y
 * [LaOficinaYaLaAplico]).
 *
 * Siete estados:
 * - [Corregible]: venta SIN enviar, sin candado, candado vencido (de
 *   cualquier tipo), o candado `EDIT` vivo. Un `EDIT` vivo se toma como
 *   corregible porque en el alcance del nivel 1 (un teléfono, una venta que
 *   nunca salió) sólo puede ser una sesión anterior del editor en el MISMO
 *   teléfono — si la app murió con el editor abierto, el dueño no debe
 *   quedar 30 min sin poder corregir su propia venta.
 * - [CorregibleEnviada]: `ENVIADO = 1`, el servidor ya tiene la venta y no
 *   hay corrección remota pendiente ni marca terminal. Se ofrece corregir
 *   igual que en [Corregible]: lo que se guarde se commitea en Room y viaja
 *   en la COLA de correcciones remotas, así que funciona sin señal. Este es
 *   el cambio de fondo del nivel 2 — antes, toda venta enviada sin marca de
 *   divergencia caía en [YaSeEnvio] y el dueño se quedaba sin salida.
 * - [SeEstaEnviando]: candado `UPLOAD` vivo — el subidor tiene la venta en
 *   vuelo ahora mismo.
 * - [CorreccionEnCamino]: `CORRECCION_REMOTA_PENDIENTE = 1` — ya hay una
 *   corrección commiteada esperando a que la cola la entregue. No se ofrece
 *   corregir otra vez encima: se informa que va en camino.
 * - [YaSeEnvio]: NO lo produce [evaluarCorregibilidad] desde el nivel 2 —
 *   su único caso (enviada y sin divergencia) ahora es [CorregibleEnviada].
 *   Se conserva porque sigue siendo el estado que
 *   [com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardarCorreccion]
 *   devuelve cuando la fila ya no existe: el mensaje menos accionable, el
 *   más seguro para no invitar a reintentar contra una fila que no está.
 * - [LaRevisaLaOficina]: un fallo permanente del subidor, o `ENVIADO = 1`
 *   con `CORRECCION_NO_ENVIADA = 1` — el servidor tiene la venta pero NO la
 *   última corrección (se coló mientras el POST anterior seguía en vuelo).
 * - [SeAplicoAMedias]: `CORRECCION_REMOTA_ESTADO` trae `'APLICADA_PARCIAL'` —
 *   la corrección viaja en tres peticiones (header, cliente, líneas) y la
 *   oficina cerró la venta con alguna ya aplicada. Terminal también, pero el
 *   servidor quedó DISTINTO de como estaba: es el único aviso que pide una
 *   acción, porque el teléfono y la oficina no coinciden y nadie más se va a
 *   dar cuenta.
 * - [LaOficinaYaLaAplico]: `CORRECCION_REMOTA_ESTADO` trae una marca
 *   TERMINAL — el servidor rechazó la corrección para siempre porque la
 *   venta salió de `borrador` (`'RECHAZADA_ESTADO'`) o porque la oficina
 *   escribió primero (`'CONFLICTO'`). No se puede corregir nunca más.
 */
sealed interface EstadoCorreccion {
    data object Corregible : EstadoCorreccion
    data object CorregibleEnviada : EstadoCorreccion
    data object SeEstaEnviando : EstadoCorreccion
    data object CorreccionEnCamino : EstadoCorreccion
    data object YaSeEnvio : EstadoCorreccion
    data object LaRevisaLaOficina : EstadoCorreccion
    data object LaOficinaYaLaAplico : EstadoCorreccion
    data object SeAplicoAMedias : EstadoCorreccion
}

/**
 * El vocabulario EXACTO de la columna `CORRECCION_REMOTA_ESTADO`, tal como lo
 * escribe
 * [com.example.msp_app.core.database.dao.localsale.LocalSaleDao.marcarCorreccionRemotaTerminal]
 * (único camino que la escribe) y lo documenta `MIGRATION_31_32`. No se
 * inventa ninguno aquí: si aparece un valor nuevo, nace en el DAO primero.
 *
 * `NULL` (o vacío) = sin incidencia. Cualquier otra cosa es una marca
 * terminal, ver [esCorreccionRemotaTerminal].
 */
object CorreccionRemotaTerminal {
    /** El servidor ya no deja editar: la venta salió de `borrador` o ya se aplicó en Microsip. */
    const val RECHAZADA_ESTADO = "RECHAZADA_ESTADO"

    /** La oficina escribió primero (412 `venta_version_conflicto`): gana la oficina. */
    const val CONFLICTO = "CONFLICTO"

    /**
     * La corrección entró **a medias**: alguna de las tres peticiones ya había
     * pasado cuando el servidor cerró la puerta, así que parte de los cambios
     * quedó aplicada y parte no.
     *
     * Existe separado de [RECHAZADA_ESTADO] porque decirle al cobrador "la
     * aplicó la oficina" cuando parte de su corrección SÍ entró es mentirle en
     * la dirección peligrosa: se iría creyendo que el servidor quedó como
     * estaba, y quedó distinto. La corrección completa viaja en tres
     * peticiones (header, cliente, líneas) y no hay transacción que las
     * abarque; mientras no la haya, este estado es la forma honesta de
     * contarlo.
     */
    const val APLICADA_PARCIAL = "APLICADA_PARCIAL"

    /** Los valores conocidos, para pruebas y para documentar el vocabulario. */
    val CONOCIDOS = setOf(RECHAZADA_ESTADO, CONFLICTO, APLICADA_PARCIAL)
}

/**
 * `true` si la columna `CORRECCION_REMOTA_ESTADO` trae una marca terminal.
 *
 * Reconoce CUALQUIER valor no vacío, no sólo los dos de
 * [CorreccionRemotaTerminal.CONOCIDOS] — al revés que [tipoCandadoDe], que
 * ante un `CLAIM_KIND` desconocido falla ABIERTO (deja corregir). Aquí la
 * dirección segura es la contraria: la columna sólo se escribe para decir
 * "esta corrección ya no puede aplicarse", así que un valor que no
 * conocemos se trata como marca igual. Fallar abierto ofrecería corregir una
 * venta que el servidor ya cerró, y esa corrección se perdería en silencio.
 */
fun esCorreccionRemotaTerminal(correccionRemotaEstado: String?): Boolean =
    !correccionRemotaEstado.isNullOrBlank()

/**
 * Decide el [EstadoCorreccion] de una venta. Los dos arrendamientos que
 * deciden si el candado vigente venció NO son parámetros de esta función:
 * vienen de la fuente única ([com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases],
 * vía [Reclamo]) para que nadie pueda llamar esto con un arrendamiento
 * inventado a mano.
 *
 * Precedencia, exhaustiva y en este orden (ver la tabla espejo en
 * `EstadoCorreccionExhaustivoTest.kt` y los casos con nombre propio en
 * `EstadoCorreccionTest.kt`):
 * 1. marca terminal en `correccionRemotaEstado` → [EstadoCorreccion.SeAplicoAMedias] si el
 *    valor es `APLICADA_PARCIAL`, y [EstadoCorreccion.LaOficinaYaLaAplico] para cualquier otra
 *    marca. Los dos son terminales; los separa lo que hay que contarle al cobrador.
 *    Va PRIMERO porque es el único estado del que no se sale nunca: ni una
 *    corrección en vuelo ni un candado cambian que el servidor ya cerró la
 *    puerta.
 * 2. `correccionRemotaPendiente` → [EstadoCorreccion.CorreccionEnCamino].
 * 3. candado `REMOTE` vivo → [EstadoCorreccion.CorreccionEnCamino], aunque la bandera del
 *    paso 2 ya esté en 0: el worker remoto reclama antes de mirarla, y en esa ventana la
 *    corrección ya viajó con el candado todavía vivo.
 * 4. `enviado` **y** `correccionNoEnviada` → [EstadoCorreccion.LaRevisaLaOficina].
 * 5. `enviado` → [EstadoCorreccion.CorregibleEnviada].
 * 6. `permanente` → [EstadoCorreccion.LaRevisaLaOficina].
 * 7. candado `UPLOAD` vivo → [EstadoCorreccion.SeEstaEnviando].
 * 8. el resto → [EstadoCorreccion.Corregible].
 *
 * Los pasos 5, 6 y 7 son EXACTAMENTE los del nivel 1 y sólo alcanzan a
 * ventas sin enviar: su comportamiento no cambió. Lo que cambió es el paso
 * 4, que antes era [EstadoCorreccion.YaSeEnvio].
 *
 * Los pasos 1 y 2 no preguntan por `enviado` a propósito: las dos columnas
 * sólo se escriben sobre ventas con `ENVIADO = 1` (el `AND ENVIADO = 1` de
 * `marcarCorreccionRemotaPendiente`), así que preguntarlo otra vez aquí
 * sería una condición que nunca cambia nada — y si alguna vez cambiara, la
 * marca gana igual: una corrección en cola o cerrada por el servidor no se
 * vuelve corregible porque la fila diga `ENVIADO = 0`.
 *
 * @param enviado columna `ENVIADO` de la fila.
 * @param permanente columna `LAST_UPLOAD_PERMANENT` (`true`/`false`, nunca
 *   se le pasa `null` — un `null` en la fila real significa "sin intento
 *   fallido", equivalente a `false` para este predicado).
 * @param correccionNoEnviada columna `CORRECCION_NO_ENVIADA`.
 * @param correccionRemotaPendiente columna `CORRECCION_REMOTA_PENDIENTE`
 *   (migración 31→32): hay una corrección commiteada que la cola todavía no
 *   entregó.
 * @param correccionRemotaEstado columna `CORRECCION_REMOTA_ESTADO`
 *   (migración 31→32) cruda: `null` sin incidencia, `'RECHAZADA_ESTADO'` o
 *   `'CONFLICTO'` marca terminal — ver [esCorreccionRemotaTerminal].
 * @param claimKind columna `CLAIM_KIND` cruda (`"EDIT"`, `"UPLOAD"`, `null`,
 *   vacía o cualquier otro valor — todo lo que no sea `EDIT`/`UPLOAD` cuenta
 *   como sin candado reconocible, ver [tipoCandadoDe]).
 * @param claimedAt columna `CLAIMED_AT` cruda.
 * @param ahora epoch millis del reloj inyectado (nunca reloj real en pruebas).
 */
@Suppress("LongParameterList")
fun evaluarCorregibilidad(
    enviado: Boolean,
    permanente: Boolean,
    correccionNoEnviada: Boolean,
    correccionRemotaPendiente: Boolean,
    correccionRemotaEstado: String?,
    claimKind: String?,
    claimedAt: Long?,
    ahora: Long
): EstadoCorreccion {
    // Un `when` sin sujeto y no una cadena de `if`/`return`: así las siete ramas quedan en el
    // mismo orden y al mismo nivel que la tabla del KDoc de arriba, que es el contrato. Leer si
    // la precedencia cambió es entonces mirar siete renglones seguidos, no seguir saltos.
    val reclamo = Reclamo(tipoCandadoDe(claimKind), claimedAt)
    return when {
        // Las dos ramas terminales se distinguen por el VALOR, no sólo por "hay marca": una
        // corrección que entró a medias dejó el servidor distinto de como estaba, y decirle al
        // cobrador lo mismo que cuando no entró nada lo mandaría a confiar en datos viejos.
        correccionRemotaEstado == CorreccionRemotaTerminal.APLICADA_PARCIAL ->
            EstadoCorreccion.SeAplicoAMedias
        esCorreccionRemotaTerminal(correccionRemotaEstado) -> EstadoCorreccion.LaOficinaYaLaAplico
        correccionRemotaPendiente -> EstadoCorreccion.CorreccionEnCamino
        // Un `REMOTE` vivo también, aunque la bandera ya esté en 0: el worker remoto reclama
        // ANTES de mirar la bandera, así que existe una ventana en la que la corrección ya
        // viajó y el candado sigue vivo. Sin este paso el dominio decía `CorregibleEnviada`
        // mientras `claimForEdit` rechazaba, y el usuario se topaba con un botón que no hace
        // nada y no explica por qué. Va aquí y no más abajo porque el candado describe algo que
        // está pasando AHORA, igual que `SeEstaEnviando` para la subida.
        reclamo.kind == TipoCandado.REMOTE && reclamo.estaVivo(ahora) ->
            EstadoCorreccion.CorreccionEnCamino
        enviado && correccionNoEnviada -> EstadoCorreccion.LaRevisaLaOficina
        enviado -> EstadoCorreccion.CorregibleEnviada
        permanente -> EstadoCorreccion.LaRevisaLaOficina
        reclamo.kind == TipoCandado.UPLOAD && reclamo.estaVivo(ahora) -> EstadoCorreccion.SeEstaEnviando
        else -> EstadoCorreccion.Corregible
    }
}

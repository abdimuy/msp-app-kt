package com.example.msp_app.core.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Qué tan vieja es una nota de puerta, dicho como lo diría el cobrador:
 * *"hoy"*, *"ayer"*, *"hace 3 días"*, *"hace 2 semanas"*.
 *
 * ## Por qué se compara por DÍA de negocio y no por milisegundos
 *
 * Una nota de una puerta se lee por día, no por reloj. Algo escrito anoche a
 * las 23:00 y leído hoy a las 07:00 lleva ocho horas de viejo, pero el cobrador
 * que llega a la casa piensa *"eso fue ayer"*, no *"eso fue hace ocho horas"*.
 * Un formateador basado en duración diría *"hace 8 horas"* y además diría
 * *"hace 1 día"* a las 23:01 de esa misma noche, o sea que cambiaría de palabra
 * sin que cambie el día — justo al revés de como se lee una libreta.
 *
 * Por eso ambas fechas se reducen primero a [LocalDate] en [BUSINESS_ZONE] (vía
 * [AppTime.toBusinessDate] / [AppTime.todayInBusinessZone]) y la resta se hace
 * sobre el calendario. La zona es la del negocio y **no la del dispositivo**:
 * un teléfono en otro huso —o mal configurado— no puede mover el corte de la
 * medianoche. Es la misma regla que `docs/standards/timezones.md` le impone a
 * todo lo demás de este paquete.
 *
 * ## El "ahora" entra por parámetro
 *
 * Nunca [Instant.now] ni `LocalDate.now()` acá adentro: el ahora viene de
 * [AppClock], igual que en [AppTime.isToday] y compañía, y por eso un test
 * puede fijar la medianoche que quiera con un `FakeClock` sin tocar estática
 * global. El parámetro se llama `clock` —y no `reloj`— a propósito: es el mismo
 * nombre que usan las funciones vecinas de [AppTime] en este mismo paquete, y
 * que dos puntos de inyección del mismo reloj se escriban distinto según el
 * archivo es exactamente la clase de detalle que hace dudar en el call site.
 *
 * ## Los escalones, y por qué están donde están
 *
 * | Distancia en días | Texto |
 * |---|---|
 * | 0 (y cualquier fecha futura) | `hoy` |
 * | 1 | `ayer` |
 * | 2–6 | `hace N días` |
 * | 7 en adelante, mientras no cierre un mes de calendario (máx. 30) | `hace N semanas` |
 * | 1–11 meses de calendario | `hace N meses` |
 * | 12 meses o más | `hace N años` |
 *
 * - **`hoy` / `ayer` por nombre y no por número.** "Hace 0 días" y "hace 1 día"
 *   no son español de nadie. Son además los dos únicos días que el cobrador
 *   recuerda sin contar.
 * - **El día exacto se mantiene hasta el 6.** Dentro de la misma semana el
 *   número sí es accionable: *"hace 3 días"* ubica la visita en la ruta de esa
 *   semana. El corte en 6/7 es el último día en que eso sigue siendo cierto.
 * - **De 7 días en adelante se pasa a semanas** porque la cobranza de esta
 *   cartera corre por semana (los plazos se cuentan en abonos semanales, ver
 *   `ReglasDeLaVisita.HORIZONTE_DIAS`): *"hace 2 semanas"* es la unidad en la
 *   que el cobrador ya piensa, y *"hace 16 días"* obliga a dividir mentalmente.
 * - **Los meses son de CALENDARIO, no bloques de 30 días.** *"hace 1 mes"* un
 *   15 de mayo quiere decir el 15 de abril, que es lo que cualquiera encuentra
 *   al mirar un calendario. Con bloques de 30 días diría "hace 1 mes" el 14 de
 *   mayo y "hace 1 mes" otra vez el 20, y la palabra dejaría de significar algo.
 * - **Los años igual, de calendario**, por la misma razón y para que los
 *   bisiestos no corran el aniversario un día.
 *
 * ## Lo que esto NO promete
 *
 * - **Los cortes no están medidos.** No hay telemetría de lectura ni una sola
 *   prueba de campo detrás de 6/7 o de "semanas antes que meses": son un juicio
 *   sobre cómo se habla en la ruta, escrito acá para que el próximo que lo
 *   cambie sepa que cambia una opinión y no un dato. Lo que sí está fijado por
 *   pruebas es que la escalera no tiene huecos ni saltos raros.
 * - **No hay horas ni minutos**, a propósito (ver arriba). Una nota de hace
 *   diez minutos dice `hoy`.
 * - **No hay "anteayer".** Cabría, pero mete una tercera palabra irregular que
 *   hay que reconocer para ganar un caso: *"hace 2 días"* ya se entiende solo.
 *   Decisión de gusto, no medida.
 * - **Nunca dice "en 3 días".** Una fecha futura colapsa a `hoy`, toda, hasta
 *   un año adelante. No es un caso real: una nota que aún no existe no se está
 *   leyendo, así que una fecha adelantada es reloj del teléfono mal puesto o
 *   dato sucio del backend. Ante eso `hoy` es lo menos falso que se puede
 *   decir, y sobre todo garantiza que nunca salga un `hace -3 días` a la
 *   pantalla.
 * - **No es i18n.** Las palabras son literales en español porque el idioma de
 *   la app está fijado por [BUSINESS_LOCALE] y el texto tiene que salir idéntico
 *   en todos los teléfonos. No se usa `DateTimeFormatter` ni `Locale` alguno:
 *   no hay nada que un locale pudiera decidir acá.
 *
 * ## Devuelve MINÚSCULA, y no es un descuido
 *
 * Esto es un **fragmento**, no un texto de usuario terminado: el llamador lo
 * incrusta en una frase más larga (*"Nota de hace 3 días"*, *"Visitado ayer"*).
 * La norma del repo —mayúscula inicial en texto de usuario— se mide sobre la
 * frase completa que arma el llamador, y esa frase ya abre con mayúscula.
 * Capitalizar acá produciría *"Nota de Hace 3 días"*.
 *
 * **La fuente de verdad de la norma es el brief (principio 10) y `CLAUDE.md`,
 * nunca este KDoc** — ya pasó una vez que un KDoc dictara la regla al revés y
 * un test la fijara así (`EtiquetasDeFicha`), y el release salió en minúsculas.
 * Acá no se está redefiniendo nada: se está diciendo que esta función no emite
 * texto terminado. Si alguna pantalla llegara a necesitar el fragmento solo,
 * como etiqueta completa, la mayúscula la pone el call site
 * (`texto.replaceFirstChar { it.uppercase(BUSINESS_LOCALE) }`) y no esta
 * función, porque cambiarla acá rompería la frase de todos los demás.
 */
object TiempoRelativo {

    /** El mismo día de negocio. También el techo de cualquier fecha futura. */
    const val HOY: String = "hoy"

    /** Exactamente un día de calendario atrás. */
    const val AYER: String = "ayer"

    /**
     * El ancho del escalón de días. Es 7 porque la semana de cobranza es 7, no
     * porque "una semana son siete días" — si la ruta cambiara de cadencia,
     * este número cambia con ella.
     */
    const val DIAS_POR_SEMANA: Long = 7

    /**
     * El texto para un instante guardado (Room, wire, Firestore), leído contra
     * el día de negocio de [clock].
     *
     * Es sólo la traducción del instante a fecha de negocio más la función de
     * abajo: toda la decisión vive en [de] `(fecha, hoy)`, que es pura.
     */
    fun de(instante: Instant, clock: AppClock = AppClock.System): String =
        de(AppTime.toBusinessDate(instante), AppTime.todayInBusinessZone(clock))

    /**
     * El núcleo puro: dos fechas de negocio, cero reloj, cero zona, cero
     * dependencia del entorno. Úsese directo cuando el llamador ya tiene la
     * fecha (una columna `DATE`, por ejemplo) y no un timestamp.
     *
     * [hoy] es el día contra el que se compara, y sale de
     * [AppTime.todayInBusinessZone] en producción. Que sea un parámetro y no una
     * lectura de reloj es lo que hace a esta función probable sin trucos.
     */
    fun de(fecha: LocalDate, hoy: LocalDate): String {
        val dias = ChronoUnit.DAYS.between(fecha, hoy)
        val meses = ChronoUnit.MONTHS.between(fecha, hoy)
        val anios = ChronoUnit.YEARS.between(fecha, hoy)

        // El orden importa: días primero (el escalón más fino), después el más
        // grueso que aplique. `anios` va antes que `meses` porque a los 12 meses
        // ambos son ciertos y el que manda es el grueso.
        return when {
            dias <= 0 -> HOY
            dias == 1L -> AYER
            dias < DIAS_POR_SEMANA -> hace(dias, "día", "días")
            anios >= 1 -> hace(anios, "año", "años")
            meses >= 1 -> hace(meses, "mes", "meses")
            // Sin hueco con el escalón de arriba: si no cerró un mes de
            // calendario, la distancia es de 30 días como mucho, así que acá
            // `dias / 7` cae siempre entre 1 y 4. "hace 5 semanas" es inalcanzable.
            else -> hace(dias / DIAS_POR_SEMANA, "semana", "semanas")
        }
    }

    /**
     * El plural, en un solo lugar.
     *
     * Existe como función y no como un `+ "s"` porque *mes* → *meses* no es
     * agregar una letra, y porque el bug que esto previene —*"hace 1 días"*— es
     * de los que nadie ve en code review pero todos ven en el teléfono. El
     * singular de días no es alcanzable (1 día es [AYER]) y aun así pasa por
     * acá: la regla se escribe una vez para los cuatro sustantivos, en lugar de
     * razonar caso por caso cuál la necesita.
     */
    private fun hace(cantidad: Long, singular: String, plural: String): String =
        "hace $cantidad ${if (cantidad == 1L) singular else plural}"
}

package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import java.time.LocalDate

/**
 * **Qué significa tocar un renglón de la línea de contactos.**
 *
 * Hasta hoy el toque tenía un solo significado: abrir el mapa en el punto de ese
 * abono o de esa visita. El dueño pidió además poder **reimprimir el ticket**
 * del último cobro del día — la impresora falló, o el cliente pidió otra copia.
 *
 * ## Por qué no se pregunta en todos los renglones
 *
 * Porque si el toque cambiara de significado en **todos** los pagos, el gesto
 * dejaría de ser predecible por una función que sólo sirve en un caso: un
 * cobrador que toca un abono de marzo para ver dónde fue se toparía con una
 * pregunta cuya segunda opción no tiene ningún sentido ahí — el ticket de marzo
 * no se puede imprimir. Se pregunta **sólo donde la reimpresión tiene sentido**:
 * el pago de HOY que además es el ÚLTIMO de esa cuenta. En cualquier otro
 * renglón el toque sigue abriendo el mapa directo, como siempre.
 *
 * ## "El último" es de la CUENTA, no del cliente
 *
 * Un cliente puede tener varias ventas y el cobrador puede cobrarle a dos el
 * mismo día. Con "el último del cliente" el ticket de la primera cuenta quedaría
 * inalcanzable —lo taparía el cobro de la segunda—, justo en el caso real que
 * esto viene a resolver: *acabo de cobrar y quiero el papel*. Con "el último de
 * la cuenta" los dos papeles siguen a un toque de distancia.
 *
 * Las **visitas no compiten**: una visita registrada después del abono no le
 * quita a ese abono el ser el último cobro de su cuenta. Sólo los contactos de
 * [TipoDeContacto.COBRO] entran a la comparación.
 *
 * ## Dónde NO se comprueba nada de esto
 *
 * Aquí no vive la regla de "sólo se imprime el día del cobro". Ésa ya está
 * tomada y se comprueba **al imprimir**, con una lectura fresca del reloj, dentro
 * de `PrintTicketUseCase` — ver el KDoc de
 * [com.example.msp_app.feature.pagos.ui.TicketDePagoViewModel]. Abrir el ticket
 * de un pago viejo está permitido; imprimirlo no. Esta función decide **qué
 * abre un toque**, nada más.
 */
enum class ToqueDelContacto {

    /**
     * El renglón no se puede tocar: no hay punto medido y tampoco hay ticket que
     * ofrecer. Es el estado de casi todo contacto registrado sin señal o sin
     * permiso, y de todo el histórico anterior a que se guardara la ubicación —
     * ver [ContactoDeCobranza.ubicacion]. La excepción es [TICKET].
     */
    NADA,

    /** Abre el mapa en el punto de ESE contacto, sin preguntar. Lo de siempre. */
    MAPA,

    /**
     * Abre el ticket directo, sin preguntar: es el cobro de hoy que cierra su
     * cuenta **y se capturó sin punto medido**.
     *
     * Sin punto no hay nada que elegir, así que una hoja con una sola opción
     * útil sería peor que ir derecho. Y es justo el renglón que más se va a
     * querer reimprimir: si el teléfono no tenía señal al cobrar, es probable
     * que la impresora también haya fallado.
     *
     * **Aquí el renglón se vuelve tocable donde antes no lo era**, y ése es el
     * cambio de fondo, no la rama: hasta ahora "sin punto" y "no se toca" eran
     * la misma cosa. Dejaron de serlo porque el toque ya no significa sólo
     * *ver dónde fue*.
     */
    TICKET,

    /** Pregunta qué abrir: la ubicación o el ticket. Sólo el cobro de hoy que cierra su cuenta. */
    PREGUNTAR;

    companion object {

        /**
         * Qué hace el toque sobre [contacto].
         *
         * @param contactos la línea de tiempo en la que ese renglón se está
         *   pintando. Se filtra aquí a los cobros de la MISMA cuenta: los
         *   llamadores pasan la lista que ya tienen —la del cliente entero— y no
         *   una lista preparada, para que ninguno pueda pasar una lista
         *   distinta de la que está pintando.
         * @param hoy el día de negocio, tomado del `AppClock` inyectado del caso
         *   de uso. Nunca `LocalDate.now()`: un borde de día es justo donde
         *   alguien alcanza el reloj del sistema, y con el reloj adentro esta
         *   función no se podría probar.
         *
         * **Sobre listas recortadas.** El detalle de cliente pinta sólo los tres
         * contactos más recientes ([BitacoraDelCliente.VISIBLES_EN_EL_DETALLE]).
         * No pasa nada: la lista viene ordenada de lo más reciente a lo más
         * viejo, así que recortarla sólo quita filas VIEJAS — cualquier renglón
         * que se esté pintando conserva a todos sus posteriores, que es lo único
         * que esta comparación mira.
         *
         * ## Sin punto medido: el cobro del día sí se alcanza, el resto no
         *
         * La primera versión de esta función dejaba fuera al cobro de hoy
         * capturado **sin señal**: su renglón no era tocable, así que era el
         * único pago del día que no se podía reimprimir — y es el que más se va
         * a querer, porque un teléfono sin señal suele ser un teléfono cuya
         * impresora también falló. El dueño lo cerró: ese caso es [TICKET], y
         * abre el papel directo. No hay hoja porque no hay nada que elegir.
         *
         * Para todo lo demás, sin punto sigue siendo [NADA] — un renglón que no
         * lleva a ninguna parte no se vuelve tocable sólo por existir.
         */
        fun de(
            contacto: ContactoDeCobranza,
            contactos: List<ContactoDeCobranza>,
            hoy: LocalDate
        ): ToqueDelContacto = when {
            esElUltimoCobroDeHoy(contacto, contactos, hoy) ->
                if (contacto.ubicacion != null) PREGUNTAR else TICKET

            contacto.ubicacion != null -> MAPA
            else -> NADA
        }

        /**
         * ¿Este contacto es un cobro de hoy y el último de su cuenta?
         *
         * El desempate por [ContactoDeCobranza.id] hace **total** el orden: dos
         * abonos de la misma cuenta al mismo instante existen de verdad (ver el
         * KDoc de [ContactoDeCobranza.id]), y sin desempate "cuál es el último"
         * dependería del orden en que llegó la lista.
         */
        private fun esElUltimoCobroDeHoy(
            contacto: ContactoDeCobranza,
            contactos: List<ContactoDeCobranza>,
            hoy: LocalDate
        ): Boolean {
            if (contacto.tipo != TipoDeContacto.COBRO) return false
            if (AppTime.toBusinessDate(contacto.fecha) != hoy) return false
            val cuenta = contacto.ventaId ?: return false
            val ultimo = contactos
                .filter { it.tipo == TipoDeContacto.COBRO && it.ventaId == cuenta }
                .maxWithOrNull(compareBy({ it.fecha }, { it.id }))
            return ultimo?.id == contacto.id
        }
    }
}

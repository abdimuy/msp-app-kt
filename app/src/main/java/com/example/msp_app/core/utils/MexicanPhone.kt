package com.example.msp_app.core.utils

/**
 * Canonicalización y validación de teléfonos mexicanos. Es el **espejo exacto**
 * del value object `platform/domain.NewTelefono` del API Go (`msp-api`,
 * `internal/platform/domain/telefono_vo.go`): mismo algoritmo, mismo veredicto.
 *
 * Algoritmo (idéntico al del servidor):
 * 1. Se descarta TODO carácter que no sea dígito (espacios, guiones, paréntesis,
 *    puntos y el `+`), así el vendedor puede teclear el número como quiera.
 * 2. Si quedan exactamente 12 dígitos y empiezan con `52`, se recorta el código
 *    de país — guardamos siempre el número nacional de 10 dígitos.
 * 3. Si el resultado NO mide exactamente 10 dígitos, el número es inválido.
 *
 * **Por qué existe este archivo (incidente 2026-08-13):** la app tenía DOS
 * reglas de teléfono y ninguna coincidía con el servidor. El validador de la
 * pantalla daba por buena cualquier cadena en ventas de CONTADO, y el mapper
 * de red anteponía `+52` a lo que fuera sin contar dígitos. Un vendedor tecleó
 * `000000`, la app lo aceptó, el mapper emitió `+52000000` y el API lo rechazó
 * con `telefono_invalid`. La venta quedó rebotando en la cola de pendientes
 * TODO el día sin que la pantalla hubiera mostrado jamás el error. Con una sola
 * implementación compartida por pantalla y mapper, ese desfase ya no es posible:
 * si la captura lo acepta, el servidor también.
 */
object MexicanPhone {

    /** Código de país de México, tal como lo recorta el VO del servidor. */
    private const val COUNTRY_CODE = "52"

    /** Longitud canónica del número nacional mexicano. */
    private const val NATIONAL_LENGTH = 10

    /** Longitud del número con código de país incluido (`52` + 10 nacionales). */
    private const val E164_DIGITS_LENGTH = 12

    /**
     * Devuelve los 10 dígitos nacionales de [raw], o `null` si [raw] no es un
     * teléfono mexicano válido. Ésta es la única función que decide qué es un
     * teléfono válido en la app; [isValid] y [toE164OrNull] se derivan de ella
     * para que no puedan divergir entre sí.
     */
    fun nationalDigitsOrNull(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val national = if (digits.length == E164_DIGITS_LENGTH && digits.startsWith(COUNTRY_CODE)) {
            digits.drop(COUNTRY_CODE.length)
        } else {
            digits
        }
        return national.takeIf { it.length == NATIONAL_LENGTH }
    }

    /**
     * `true` si [raw] es un teléfono mexicano de 10 dígitos, con o sin `+52`.
     * Ojo: la cadena vacía es INVÁLIDA aquí. "Sin teléfono" es una decisión de
     * negocio de cada formulario (el servidor lo trata como campo opcional), no
     * de este validador de formato.
     */
    fun isValid(raw: String): Boolean = nationalDigitsOrNull(raw) != null

    /**
     * Normaliza [raw] a E.164 (`+52##########`) o devuelve `null` si el número
     * no es válido.
     *
     * **Devolver `null` ante basura es intencional, no una omisión:** para el
     * API un teléfono ausente es válido (campo opcional en todos los tipos de
     * venta) mientras que un teléfono inválido es un rechazo duro. Mandar nada
     * siempre entra; mandar `+52000000` nunca. Esto además desatasca las filas
     * viejas que ya viven en Room con teléfonos malos: al reintentarse salen sin
     * teléfono y la venta pasa, en vez de rebotar para siempre.
     */
    fun toE164OrNull(raw: String): String? = nationalDigitsOrNull(raw)?.let { "+$COUNTRY_CODE$it" }
}

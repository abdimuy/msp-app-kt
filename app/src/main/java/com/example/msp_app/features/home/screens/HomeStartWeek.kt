package com.example.msp_app.features.home.screens

import com.example.msp_app.core.common.time.AppTime
import java.time.Instant

/**
 * Inicio de semana del tablero, en wire RFC3339 UTC — o `null` cuando **todavía no se sabe**.
 *
 * ## Por qué ya no hay fallback (defecto D5)
 *
 * `Home` resolvía esto como `initialDate?.toDate()?.toInstant() ?: AppClock.System.now()`: si el
 * documento de usuario de Firestore no estaba disponible, la semana empezaba **ahora** y ningún
 * pago pasado calificaba. El cobrador veía $0.00 cobrado en la semana con la tabla de pagos
 * llena. Que la causa fuera la ventana de fechas y no una resincronización pendiente quedó
 * probado en el mismo tablero: el contador de VENTAS (103) —que no filtra por fecha— sobrevivía
 * intacto mientras los pagos daban 0. Sólo un rango malo produce `0/103`.
 *
 * Devolver `null` obliga al llamador a las dos cosas correctas: **no** consultar con una ventana
 * inventada, y decir en pantalla que falta el dato ([START_WEEK_UNKNOWN_LABEL]) en vez de pintar
 * un $0 que se lee como cifra real.
 *
 * Cuando el dato llega tarde, esto pasa de `null` a un valor y el `LaunchedEffect(startWeekDate)`
 * de `Home` se re-dispara solo — la auto-reparación no necesita que el usuario salga y vuelva.
 */
internal fun resolveStartWeekDate(fechaCargaInicial: Instant?): String? =
    fechaCargaInicial?.let { AppTime.toWireFormat(it) }

/**
 * Texto es-MX para las cifras semanales cuando no se sabe dónde abre la semana. Minimalista
 * (4 palabras, minúsculas, sin punto final) y dice **semana**, nunca "ciclo".
 */
internal const val START_WEEK_UNKNOWN_LABEL = "sin inicio de semana"

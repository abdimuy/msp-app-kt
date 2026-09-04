package com.example.msp_app.feature.visitas.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.LocalDate
import java.time.LocalTime

/** `testTag` del calendario de "otro día". */
const val CALENDARIO_TAG: String = "visitas_calendario"

/** `testTag` del reloj de "otra hora". */
const val RELOJ_TAG: String = "visitas_reloj"

/** Milisegundos de un día. El `DatePicker` de Material habla en millis UTC. */
private const val MILLIS_POR_DIA: Long = 86_400_000L

/**
 * El calendario de "otro día".
 *
 * [minimo] apaga los días anteriores **en el propio calendario**, en vez de
 * dejar elegir una fecha que el CTA rechazaría después: un control que ofrece
 * algo y luego lo rechaza es la misma mentira que un botón vivo que no responde.
 *
 * La conversión va por [LocalDate.toEpochDay] y no por una zona horaria: los
 * millis del `DatePicker` son medianoche UTC del día elegido, así que el día del
 * calendario y el día que se guarda son el mismo sin importar dónde esté el
 * teléfono.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarioDeVisita(
    inicial: LocalDate,
    minimo: LocalDate?,
    onElegir: (LocalDate) -> Unit,
    onCerrar: () -> Unit
) {
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial.toEpochDay() * MILLIS_POR_DIA,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                minimo == null || utcTimeMillis / MILLIS_POR_DIA >= minimo.toEpochDay()
        }
    )
    DatePickerDialog(
        onDismissRequest = onCerrar,
        modifier = Modifier.testTag(CALENDARIO_TAG),
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = estado.selectedDateMillis
                    if (millis == null) {
                        onCerrar()
                    } else {
                        onElegir(
                            LocalDate.ofEpochDay(millis / MILLIS_POR_DIA)
                        )
                    }
                }
            ) {
                Text(text = "elegir día")
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) { Text(text = "cancelar") }
        }
    ) {
        DatePicker(state = estado)
    }
}

/**
 * El reloj de "otra hora". Formato de 24 horas, el mismo en que se guarda
 * `CITA_HORA` y el mismo que ya usa el diálogo de hoy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelojDeLaCita(inicial: LocalTime, onElegir: (LocalTime) -> Unit, onCerrar: () -> Unit) {
    val estado = rememberTimePickerState(
        initialHour = inicial.hour,
        initialMinute = inicial.minute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onCerrar,
        modifier = Modifier.testTag(RELOJ_TAG),
        confirmButton = {
            TextButton(onClick = { onElegir(LocalTime.of(estado.hour, estado.minute)) }) {
                Text(text = "elegir hora")
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) { Text(text = "cancelar") }
        },
        text = { TimePicker(state = estado) }
    )
}

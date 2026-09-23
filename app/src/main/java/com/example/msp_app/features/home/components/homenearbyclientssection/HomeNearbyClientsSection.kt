package com.example.msp_app.features.home.components.homenearbyclientssection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.common.location.SaleDistance
import com.example.msp_app.core.common.location.label

/** Encabezado de la sección. Es también con lo que los tests la buscan. */
const val NEARBY_CLIENTS_TITLE: String = "CLIENTES CERCANOS"

/** Lectura para TalkBack del hueco que deja una puerta sin ubicación. */
private const val NO_LOCATION_READING = "Sin ubicación"

/**
 * **Las puertas más cercanas, en la pantalla principal.**
 *
 * Es la lista con la que el cobrador decide a qué puerta ir. Estuvo fuera entre
 * el 2026-09-04 (commit `d76d8f69`, la migración de listas-por-venta a
 * listas-por-cliente) y esta rama: se retiró junto con la pantalla de ventas que
 * la alimentaba, sin que hubiera una razón medida para quitarla.
 *
 * Es una sección hermana de [HomeHeader][com.example.msp_app.features.home.components.homeheader.HomeHeader],
 * `HomeSummarySection`, `HomeWeeklyPaymentsSection`, `HomeStartWeekSection` y
 * `HomeFooterSection`: recibe datos ya resueltos y un callback, no toca
 * ViewModels ni el `NavController`. Quién calcula [clients] y a dónde lleva
 * [onClientClick] lo decide `HomeScreen`, que es donde la regla del origen está
 * cableada.
 *
 * **Con [clients] vacío no pinta nada** —ni encabezado, ni tarjeta vacía, ni
 * indicador de carga—. Ése es el caso normal, no el excepcional: sin permiso de
 * ubicación, con el GPS apagado o antes de que entre el primer fix, la lista no
 * tiene nada honesto que decir, y un hueco con título sería peor que su
 * ausencia. La pantalla sigue entera; las demás secciones no dependen de esto.
 *
 * Cada renglón lleva al **cliente**, no a una de sus ventas. El porqué, y qué
 * pasa cuando un cliente debe más de una cuenta, está en [nearbyClientsFrom].
 */
@Composable
fun HomeNearbyClientsSection(
    clients: List<NearbyClient>,
    isDark: Boolean,
    onClientClick: (NearbyClient) -> Unit,
    modifier: Modifier = Modifier
) {
    if (clients.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = NEARBY_CLIENTS_TITLE,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(8.dp))

        clients.forEach { client ->
            NearbyClientRow(
                client = client,
                isDark = isDark,
                onClick = { onClientClick(client) }
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Un renglón: quién, dónde y a qué distancia.
 *
 * El "N cuentas" sólo aparece cuando de verdad se colapsó más de una venta en
 * esta puerta — si no, sería ruido en el 90 % de los renglones.
 */
@Composable
private fun NearbyClientRow(
    client: NearbyClient,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasLocation = client.distance is SaleDistance.Known

    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) Color.Gray else Color.LightGray
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(NAME_WEIGHT)) {
                Text(
                    text = client.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (client.address.isNotEmpty()) {
                    Text(
                        text = client.address,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }
                if (client.accounts > 1) {
                    Text(
                        text = "${client.accounts} cuentas",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = client.distance.label(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    !hasLocation -> MaterialTheme.colorScheme.onSurfaceVariant
                    isDark -> Color.White
                    else -> MaterialTheme.colorScheme.primary
                },
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(DISTANCE_WEIGHT)
                    // Un guion no se lee solo: TalkBack necesita saber que ese
                    // hueco significa que la puerta no tiene ubicación.
                    .then(
                        if (hasLocation) {
                            Modifier
                        } else {
                            Modifier.semantics { contentDescription = NO_LOCATION_READING }
                        }
                    )
            )
        }
    }
}

/**
 * El nombre y la dirección se quedan con dos tercios del renglón y la distancia
 * con uno: el texto más largo que la distancia puede producir es `"20000 km"`
 * (ocho caracteres, techo fijado por `SaleDistance.MAX_PLAUSIBLE_METERS`), así
 * que no puede robarle el ancho al nombre ni partir la fila.
 */
private const val NAME_WEIGHT = 2f
private const val DISTANCE_WEIGHT = 1f

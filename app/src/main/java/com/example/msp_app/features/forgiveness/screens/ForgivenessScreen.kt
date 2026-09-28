package com.example.msp_app.features.forgiveness.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.forgiveness.components.NewForgivenessDialog
import com.example.msp_app.features.sales.viewmodels.SaleDetailsViewModel
import com.example.msp_app.navigation.Screen

/**
 * El destino de la condonación ([com.example.msp_app.navigation.Screen.Forgiveness]).
 *
 * Resuelve la [com.example.msp_app.data.models.sale.Sale] por su id —el
 * `DOCTO_CC_ACR_ID`, vía [SaleDetailsViewModel.loadSaleDetails]— y monta
 * [NewForgivenessDialog] tal cual, con `show = true`. No reescribe nada de la
 * captura: sólo es la puerta que un llamador sin acceso al detalle legado
 * (`:feature:pagos`) puede navegar sin conocer ese diálogo.
 *
 * **Sin cascarón vacío.** Si la venta no resuelve —`ResultState.Error` o un
 * `Success` con dato `null`, que es lo que devuelve `loadSaleDetails` cuando
 * `SaleDao.getById` no encuentra la fila— la pantalla vuelve atrás en vez de
 * pintar un diálogo sin datos. Mismo criterio que
 * [com.example.msp_app.navigation.destinoDeLaUbicacion] aplica a una
 * coordenada ilegible: una venta a medias es un dato falso, no uno
 * incompleto.
 *
 * **Cerrar y guardar son dos salidas distintas.**
 *
 * - `onDismissRequest` —cancelar— es `popBackStack()`: volver por donde se
 *   entró, igual que el resto de los destinos de este grafo.
 * - `onGuardada` —la condonación YA quedó escrita— es
 *   [irAlTicketDeLaCondonacion]: el ticket legado **reemplaza** a esta pantalla
 *   en la pila, y atrás desde el ticket vuelve al detalle desde el que se entró.
 *
 * Antes las dos eran la misma: el diálogo empujaba el ticket y después llamaba a
 * `onDismissRequest`, cuyo `popBackStack()` sacaba **el ticket** y dejaba esta
 * pantalla con la venta vieja — el cobrador no veía confirmación, reintentaba, y
 * cada reintento era otra condonación (E-APP-031, E-APP-029).
 */
@Composable
fun ForgivenessScreen(saleId: Int, navController: NavController) {
    val viewModel: SaleDetailsViewModel = viewModel()
    val state by viewModel.saleState.collectAsState()

    LaunchedEffect(saleId) {
        viewModel.loadSaleDetails(saleId)
    }

    LaunchedEffect(state) {
        val resultado = state
        val sinVenta = resultado is ResultState.Error ||
            (resultado is ResultState.Success && resultado.data == null)
        if (sinVenta) {
            navController.popBackStack()
        }
    }

    when (val resultado = state) {
        is ResultState.Success -> {
            val sale = resultado.data
            if (sale != null) {
                NewForgivenessDialog(
                    show = true,
                    onDismissRequest = { navController.popBackStack() },
                    onGuardada = { pagoId -> navController.irAlTicketDeLaCondonacion(pagoId) },
                    sale = sale
                )
            }
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * **A dónde va una condonación ya escrita**: al ticket legado
 * ([Screen.PaymentTicket]), sacando [Screen.Forgiveness] de la pila.
 *
 * Es el mismo molde que el abono nuevo ya usaba
 * (`DestinosDeCobranzaGraph`, `onRegistrado` →
 * `popUpTo(PagosRutas.REGISTRAR_ABONO) { inclusive = true }`): la pantalla de
 * captura sale, el ticket se queda, y atrás desde el ticket lleva a la venta
 * — nunca a un diálogo de condonación con el monto ya aplicado detrás.
 *
 * `internal` y fuera del `@Composable` para que la prueba de navegación la
 * ejerza sobre el grafo real sin montar Firebase.
 */
internal fun NavController.irAlTicketDeLaCondonacion(pagoId: String) {
    navigate(Screen.PaymentTicket.createRoute(pagoId)) {
        popUpTo(Screen.Forgiveness.route) { inclusive = true }
    }
}

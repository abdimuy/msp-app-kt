@file:Suppress("TooManyFunctions") // una pieza por bloque del formulario.

package com.example.msp_app.feature.garantias.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.garantias.domain.model.ClienteDeGarantia
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import com.example.msp_app.feature.garantias.ui.components.AvisoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.CampoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.EncabezadoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.EtiquetaDeSeccion
import com.example.msp_app.feature.garantias.ui.components.FilaDeDato

/** Mínimo tocable del repo; cada fila de cliente lo respeta. */
private val ALTO_DE_FILA = 50.dp

/**
 * El contenido de "abrir folio": puro, sin ViewModel ni Hilt.
 *
 * El origen decide qué se pide. Un folio de **piso** no lleva cliente ni
 * domicilio; uno de **cliente** exige elegir a alguien del padrón y copia su
 * domicilio. El GPS del cobrador no aparece acá: el domicilio se escribe a mano
 * (D5).
 *
 * Los dos botones de origen son la misma pieza con [PrimaryFieldButtonVariant]:
 * el elegido es `Primary` y el otro `Ghost`. El cobrador ve cuál está activo sin
 * leer el texto dos veces.
 */
@Composable
fun AbrirFolioContenido(
    state: AbrirFolioUiState,
    onOrigen: (OrigenDeFolio) -> Unit,
    onBusqueda: (String) -> Unit,
    onCliente: (ClienteDeGarantia) -> Unit,
    onDomicilio: (CampoDelDomicilio, String) -> Unit,
    onDescripcion: (String) -> Unit,
    onClave: (String) -> Unit,
    onDescripcionDelArticulo: (String) -> Unit,
    onVigencia: (String) -> Unit,
    onGuardar: () -> Unit,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        EncabezadoDeGarantia(titulo = TextosDeGarantias.TITULO_ABRIR, onAtras = onAtras)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
        ) {
            Origen(origen = state.origen, onOrigen = onOrigen)
            if (state.origen == OrigenDeFolio.CLIENTE) {
                SeccionDeCliente(
                    state = state,
                    onBusqueda = onBusqueda,
                    onCliente = onCliente,
                    onDomicilio = onDomicilio
                )
            }
            SeccionDelFolio(
                descripcion = state.descripcion,
                clave = state.claveDelArticulo,
                descripcionDelArticulo = state.descripcionDelArticulo,
                vigencia = state.vigencia,
                onDescripcion = onDescripcion,
                onClave = onClave,
                onDescripcionDelArticulo = onDescripcionDelArticulo,
                onVigencia = onVigencia
            )
            state.fallo?.let { fallo ->
                AvisoDeGarantia(texto = TextosDeGarantias.textoDe(fallo), esError = true)
            }
            if (state.falloTecnico) {
                AvisoDeGarantia(
                    texto = TextosDeGarantias.NO_SE_PUDO_GUARDAR,
                    esError = true
                )
            }
        }
        MspPrimaryFieldButton(
            text = if (state.guardando) {
                TextosDeGarantias.GUARDANDO
            } else {
                TextosDeGarantias.GUARDAR_FOLIO
            },
            onClick = onGuardar,
            enabled = !state.guardando,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** El origen del folio: dos botones, uno activo y el otro fantasma. */
@Composable
private fun Origen(origen: OrigenDeFolio, onOrigen: (OrigenDeFolio) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        EtiquetaDeSeccion(texto = TextosDeGarantias.ORIGEN)
        Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
            OrigenDeFolio.entries.forEach { candidato ->
                MspPrimaryFieldButton(
                    text = TextosDeGarantias.textoDe(candidato),
                    onClick = { onOrigen(candidato) },
                    modifier = Modifier.weight(1f),
                    variant = if (candidato == origen) {
                        PrimaryFieldButtonVariant.Primary
                    } else {
                        PrimaryFieldButtonVariant.Ghost
                    }
                )
            }
        }
    }
}

/** El padrón: búsqueda por nombre, coincidencias y el cliente ya elegido. */
@Composable
private fun SeccionDeCliente(
    state: AbrirFolioUiState,
    onBusqueda: (String) -> Unit,
    onCliente: (ClienteDeGarantia) -> Unit,
    onDomicilio: (CampoDelDomicilio, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        EtiquetaDeSeccion(texto = TextosDeGarantias.NOMBRE_DEL_CLIENTE)
        CampoDeGarantia(
            etiqueta = TextosDeGarantias.BUSCAR_CLIENTE,
            valor = state.busquedaDeCliente,
            onValor = onBusqueda
        )
        when {
            state.clienteElegido != null -> FichaDelCliente(cliente = state.clienteElegido)
            state.clientes.isNotEmpty() -> state.clientes.forEach { cliente ->
                CoincidenciaDeCliente(cliente = cliente, onCliente = onCliente)
            }
            state.busquedaDeCliente.isNotBlank() -> Text(
                text = TextosDeGarantias.SIN_CLIENTES,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
        EtiquetaDeSeccion(texto = TextosDeGarantias.DOMICILIO)
        CampoDeGarantia(
            etiqueta = TextosDeGarantias.CALLE,
            valor = state.domicilio.calle,
            onValor = { onDomicilio(CampoDelDomicilio.CALLE, it) }
        )
        CampoDeGarantia(
            etiqueta = TextosDeGarantias.NUMERO,
            valor = state.domicilio.numeroExterior,
            onValor = { onDomicilio(CampoDelDomicilio.NUMERO, it) },
            teclado = KeyboardType.Number
        )
        CampoDeGarantia(
            etiqueta = TextosDeGarantias.COLONIA,
            valor = state.domicilio.colonia,
            onValor = { onDomicilio(CampoDelDomicilio.COLONIA, it) }
        )
        CampoDeGarantia(
            etiqueta = TextosDeGarantias.LOCALIDAD,
            valor = state.domicilio.localidad,
            onValor = { onDomicilio(CampoDelDomicilio.LOCALIDAD, it) }
        )
        CampoDeGarantia(
            etiqueta = TextosDeGarantias.CIUDAD,
            valor = state.domicilio.ciudad,
            onValor = { onDomicilio(CampoDelDomicilio.CIUDAD, it) }
        )
        CampoDeGarantia(
            etiqueta = TextosDeGarantias.CODIGO_POSTAL,
            valor = state.domicilio.codigoPostal,
            onValor = { onDomicilio(CampoDelDomicilio.CODIGO_POSTAL, it) },
            teclado = KeyboardType.Number
        )
    }
}

/** Una coincidencia del padrón, tocable para elegirla. */
@Composable
private fun CoincidenciaDeCliente(
    cliente: ClienteDeGarantia,
    onCliente: (ClienteDeGarantia) -> Unit
) {
    MspCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_DE_FILA),
        onClick = { onCliente(cliente) }
    ) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Text(
                text = cliente.nombre,
                style = MspTheme.type.listTitle,
                color = MspTheme.colors.onSurface
            )
            Text(
                text = TextosDeGarantias.textoDe(cliente.estadoDeCuenta),
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            if (!cliente.sePuedeUsar) {
                Text(
                    text = TextosDeGarantias.SIN_VENTA,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.danger
                )
            }
        }
    }
}

/** El cliente ya elegido, con lo que el servidor espera de él. */
@Composable
private fun FichaDelCliente(cliente: ClienteDeGarantia) {
    MspCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            EtiquetaDeSeccion(texto = TextosDeGarantias.CLIENTE_ELEGIDO)
            FilaDeDato(etiqueta = TextosDeGarantias.NOMBRE_DEL_CLIENTE, valor = cliente.nombre)
            FilaDeDato(
                etiqueta = TextosDeGarantias.ESTADO_DE_CUENTA,
                valor = TextosDeGarantias.textoDe(cliente.estadoDeCuenta)
            )
            if (!cliente.sePuedeUsar) {
                AvisoDeGarantia(texto = TextosDeGarantias.SIN_VENTA, esError = true)
            }
        }
    }
}

/** La descripción del folio y su primer artículo. */
@Composable
private fun SeccionDelFolio(
    descripcion: String,
    clave: String,
    descripcionDelArticulo: String,
    vigencia: String,
    onDescripcion: (String) -> Unit,
    onClave: (String) -> Unit,
    onDescripcionDelArticulo: (String) -> Unit,
    onVigencia: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        CampoDeGarantia(
            etiqueta = TextosDeGarantias.DESCRIPCION_DEL_FOLIO,
            valor = descripcion,
            onValor = onDescripcion,
            marcador = TextosDeGarantias.DESCRIPCION_DEL_FOLIO_MARCADOR,
            lineas = MIN_LINEAS_DESCRIPCION
        )
        EtiquetaDeSeccion(texto = TextosDeGarantias.PRIMER_ARTICULO)
        CampoDeGarantia(
            etiqueta = TextosDeGarantias.CLAVE_DEL_ARTICULO,
            valor = clave,
            onValor = onClave
        )
        CampoDeGarantia(
            etiqueta = TextosDeGarantias.DESCRIPCION_DEL_ARTICULO,
            valor = descripcionDelArticulo,
            onValor = onDescripcionDelArticulo
        )
        CampoDeGarantia(
            etiqueta = TextosDeGarantias.VIGENCIA,
            valor = vigencia,
            onValor = onVigencia
        )
    }
}

/** La descripción del folio se lee mejor en varias líneas. */
private const val MIN_LINEAS_DESCRIPCION = 3

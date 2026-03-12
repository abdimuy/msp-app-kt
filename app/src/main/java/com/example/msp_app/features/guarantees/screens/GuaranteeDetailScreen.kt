package com.example.msp_app.features.guarantees.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.features.guarantees.screens.components.GuaranteeStatusBadge
import com.example.msp_app.features.guarantees.screens.viewmodels.GuaranteeListViewModel
import com.example.msp_app.features.guarantees.screens.viewmodels.GuaranteesViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuaranteeDetailScreen(guaranteeId: Int, navController: NavController) {
    val listViewModel: GuaranteeListViewModel = viewModel()
    val guaranteesViewModel: GuaranteesViewModel = viewModel()
    val guarantee by listViewModel.selectedGuarantee.collectAsState()

    var showRecolectarDialog by remember { mutableStateOf(false) }
    var showEntregarDialog by remember { mutableStateOf(false) }

    LaunchedEffect(guaranteeId) {
        listViewModel.loadGuaranteeById(guaranteeId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Garantía") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        guarantee?.let { g ->
            val statusColor = when (g.ESTADO) {
                "NOTIFICADO" -> Color(0xFFFFC107)
                "RECOLECTADO" -> Color(0xFF2196F3)
                "ENTREGADO" -> Color(0xFF4CAF50)
                else -> Color.Gray
            }

            val statusLabel = when (g.ESTADO) {
                "NOTIFICADO" -> "Notificado"
                "RECOLECTADO" -> "Recolectado"
                "ENTREGADO" -> "Entregado"
                else -> g.ESTADO
            }

            val formattedDate = try {
                DateUtils.formatIsoDate(
                    iso = g.FECHA_SOLICITUD,
                    pattern = "dd MMM yyyy, hh:mm a",
                    locale = Locale("es", "MX")
                ) ?: g.FECHA_SOLICITUD
            } catch (_: Exception) {
                g.FECHA_SOLICITUD
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Estado
                GuaranteeStatusBadge(label = statusLabel, color = statusColor)

                // Cliente
                DetailField(label = "Cliente", value = g.NOMBRE_CLIENTE ?: "Cliente desconocido")

                // Producto
                if (!g.NOMBRE_PRODUCTO.isNullOrBlank()) {
                    DetailField(label = "Producto", value = g.NOMBRE_PRODUCTO)
                }

                // Descripción de falla
                DetailField(label = "Descripción de falla", value = g.DESCRIPCION_FALLA)

                // Observaciones
                if (!g.OBSERVACIONES.isNullOrBlank()) {
                    DetailField(label = "Observaciones", value = g.OBSERVACIONES)
                }

                // Fecha
                DetailField(label = "Fecha de solicitud", value = formattedDate)

                Spacer(modifier = Modifier.height(8.dp))

                // Botones de acción según estado
                when (g.ESTADO) {
                    "NOTIFICADO" -> {
                        TextButton(
                            onClick = { showRecolectarDialog = true },
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .heightIn(min = 48.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                "Recolectar producto del cliente",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    "LISTO_PARA_ENTREGAR" -> {
                        TextButton(
                            onClick = { showEntregarDialog = true },
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .heightIn(min = 48.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                "Entregar producto al cliente",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRecolectarDialog && guarantee != null) {
        AlertDialog(
            onDismissRequest = { showRecolectarDialog = false },
            title = {
                Text("Confirmar Recolección", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("¿Está seguro de que desea recolectar el producto del cliente?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        guaranteesViewModel.onRecolectarProductoClick(guarantee!!) {
                            listViewModel.loadGuaranteeById(guaranteeId)
                        }
                        showRecolectarDialog = false
                    }
                ) {
                    Text("Sí, recolectar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecolectarDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showEntregarDialog && guarantee != null) {
        AlertDialog(
            onDismissRequest = { showEntregarDialog = false },
            title = {
                Text("Confirmar Entrega", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("¿Está seguro de que desea entregar el producto al cliente?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        guaranteesViewModel.onEntregarProducto(guarantee!!) {
                            listViewModel.loadGuaranteeById(guaranteeId)
                        }
                        showEntregarDialog = false
                    }
                ) {
                    Text("Sí, entregar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEntregarDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

package com.example.msp_app.features.guarantees.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.data.local.entities.GuaranteeEntity
import java.util.Locale

@Composable
fun GuaranteeListItem(guarantee: GuaranteeEntity, onClick: () -> Unit = {}) {
    val statusColor = when (guarantee.ESTADO) {
        "NOTIFICADO" -> Color(0xFFFFC107)
        "RECOLECTADO" -> Color(0xFF2196F3)
        "ENTREGADO" -> Color(0xFF4CAF50)
        else -> Color.Gray
    }

    val statusLabel = when (guarantee.ESTADO) {
        "NOTIFICADO" -> "Notificado"
        "RECOLECTADO" -> "Recolectado"
        "ENTREGADO" -> "Entregado"
        else -> guarantee.ESTADO
    }

    val formattedDate = try {
        DateUtils.formatIsoDate(
            iso = guarantee.FECHA_SOLICITUD,
            pattern = "dd MMM yyyy",
            locale = Locale("es", "MX")
        ) ?: guarantee.FECHA_SOLICITUD
    } catch (_: Exception) {
        guarantee.FECHA_SOLICITUD
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: nombre + sync icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = guarantee.NOMBRE_CLIENTE ?: "Cliente desconocido",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (guarantee.UPLOADED == 1) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Sincronizado",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Pendiente",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Producto
            if (!guarantee.NOMBRE_PRODUCTO.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = guarantee.NOMBRE_PRODUCTO,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Falla
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = guarantee.DESCRIPCION_FALLA,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Footer: badges + fecha
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuaranteeStatusBadge(label = statusLabel, color = statusColor)

                    GuaranteeStatusBadge(
                        label = if (guarantee.DOCTO_CC_ID != null) "Con venta" else "Sin venta",
                        color = if (guarantee.DOCTO_CC_ID != null) {
                            Color(0xFF2196F3)
                        } else {
                            Color(0xFF9E9E9E)
                        }
                    )
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

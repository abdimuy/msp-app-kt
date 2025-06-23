package com.example.msp_app.features.sales.components.salecontactactions

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.msp_app.R
import com.example.msp_app.data.models.sale.Sale

@Composable
fun SaleContactActions(sale: Sale) {
    val context = LocalContext.current
    val telephone = sale.TELEFONO
    val validPhone = !telephone.isNullOrBlank()
    IconButton(
        onClick = {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = ("tel:" + sale.TELEFONO).toUri()
            }
            context.startActivity(intent)
        },
        enabled = validPhone,
        modifier = Modifier
            .size(56.dp)
            .then(
                if (validPhone)
                    Modifier.background(Color(0xFFADD8E6), shape = RoundedCornerShape(12.dp))
                else
                    Modifier.background(Color.Gray, shape = RoundedCornerShape(12.dp))
            )
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Telefono",
            tint = if (validPhone) MaterialTheme.colorScheme.primary else Color.DarkGray,
            modifier = Modifier.size(34.dp)
        )
    }
    IconButton(
        onClick = {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
            }
            context.startActivity(intent)
        },
        modifier = Modifier
            .size(56.dp)
            .background(Color(0xFF90EE90), shape = RoundedCornerShape(12.dp))
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = "Calendar",
            tint = Color(0xFF008000),
            modifier = Modifier.size((34.dp))
        )
    }
    IconButton(
        onClick = {
            val number = "521" + sale.TELEFONO.replace("\\s".toRegex(), "")
            val url = "https://wa.me/$number"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = url.toUri()
                setPackage("com.whatsapp")
            }

            context.startActivity(intent)
        },
        enabled = validPhone,
        modifier = Modifier
            .size(56.dp)
            .then(
                if (validPhone)
                    Modifier.background(Color(0xFF90EE90), shape = RoundedCornerShape(12.dp))
                else
                    Modifier.background(Color.Gray, shape = RoundedCornerShape(12.dp))
            )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.whatsapp),
            contentDescription = "WhatsApp",
            modifier = Modifier.size(28.dp),
            tint = if (validPhone) Color(0xFF008000) else Color.DarkGray
        )
    }
}
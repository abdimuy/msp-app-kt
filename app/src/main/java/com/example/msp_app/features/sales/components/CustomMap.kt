package com.example.msp_app.features.sales.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.msp_app.R


@Composable
fun CustomMap() {
    Image(
        painter = painterResource(id = R.drawable.map_layout),
        contentDescription = "Mapa ilustrado",
        modifier = Modifier
            .fillMaxWidth()
    )
}

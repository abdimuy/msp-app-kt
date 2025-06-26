package com.example.msp_app.features.sales.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.msp_app.R


@Composable
fun CustomMap(onClick: () -> Unit) {
    Image(
        painter = painterResource(id = R.drawable.map_layout),
        contentDescription = "Mapa ilustrado",
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    )
}

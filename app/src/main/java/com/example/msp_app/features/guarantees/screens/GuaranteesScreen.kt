package com.example.msp_app.features.guarantees.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuaranteeScreen(navController: NavController) {
    var defectDescription by remember { mutableStateOf(TextFieldValue("")) }
    var observations by remember { mutableStateOf(TextFieldValue("")) }
    var imageUris by remember { mutableStateOf(listOf<Uri>()) }
    var showError by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUris = imageUris + it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Iniciar Garantía") }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = defectDescription,
                    onValueChange = {
                        defectDescription = it
                        if (it.text.isNotBlank()) showError = false
                    },
                    label = { Text("Defecto del artículo *") },
                    isError = showError,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 2,
                    shape = RoundedCornerShape(15.dp)
                )
                if (showError) {
                    Text(
                        "Este campo es obligatorio",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = observations,
                    onValueChange = { observations = it },
                    label = { Text("Observaciones (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 10,
                    shape = RoundedCornerShape(15.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Agregar imágenes",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Button(onClick = {
                    imagePickerLauncher.launch("image/*")
                }) {
                    Text("Seleccionar imagen")
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(imageUris) { uri ->
                        Box(modifier = Modifier.size(80.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(uri),
                                contentDescription = "Imagen",
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.Gray, shape = RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Text(
                                "✕",
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .clickable {
                                        imageUris = imageUris.filterNot { it == uri }
                                    }
                                    .background(
                                        Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (defectDescription.text.isBlank()) {
                        showError = true
                    } else {
                        
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar garantía")
            }
        }
    }
}

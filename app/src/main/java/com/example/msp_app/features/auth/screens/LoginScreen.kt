package com.example.msp_app.features.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.ui.theme.ThemeController
import com.google.firebase.auth.FirebaseAuth

/**
 * Mensaje cuando falta el correo o la contraseña. Sin esta validación el botón
 * mandaba "" a `signInWithEmailAndPassword`, que lanza
 * `IllegalArgumentException` ("Given String is empty or null") y tumba la app.
 */
internal const val MENSAJE_FALTAN_CREDENCIALES = "escribe tu correo y contraseña"

/** Verdadero si el correo o la contraseña vienen vacíos o sólo con espacios. */
internal fun faltanCredenciales(correo: String, contrasena: String): Boolean =
    correo.isBlank() || contrasena.isBlank()

/**
 * Inicia sesión con [correo] y [contrasena] ya recortados, y avisa a
 * [alTerminar] con `null` si entró o con el mensaje de error si no.
 */
fun interface IniciarSesion {
    fun iniciar(correo: String, contrasena: String, alTerminar: (String?) -> Unit)
}

private val iniciarSesionConFirebase = IniciarSesion { correo, contrasena, alTerminar ->
    FirebaseAuth.getInstance()
        .signInWithEmailAndPassword(correo, contrasena)
        .addOnCompleteListener { task ->
            alTerminar(
                if (task.isSuccessful) null else task.exception?.message ?: "Error al iniciar sesión"
            )
        }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    iniciarSesion: IniciarSesion = iniciarSesionConFirebase
) {
    val isDark = ThemeController.isDarkMode

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black else Color.White)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Iniciar sesión",
            style = TextStyle(fontSize = 24.sp),
            color = if (isDark) Color.White else Color.Black
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Password
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (faltanCredenciales(email, password)) {
                    errorMessage = MENSAJE_FALTAN_CREDENCIALES
                    return@Button
                }
                isLoading = true
                errorMessage = null

                iniciarSesion.iniciar(email.trim(), password.trim()) { error ->
                    isLoading = false
                    if (error == null) {
                        onLoginSuccess()
                    } else {
                        errorMessage = error
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Cargando..." else "Iniciar sesión", color = Color.White)
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = it, color = Color.Red, fontSize = 14.sp)
        }
    }
}

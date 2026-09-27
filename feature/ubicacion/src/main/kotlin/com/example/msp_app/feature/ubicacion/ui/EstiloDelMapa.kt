package com.example.msp_app.feature.ubicacion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.msp_app.core.designsystem.theme.appDarkTheme
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.MapProperties

/**
 * **El estilo de TODOS los mapas de cobranza**: el fondo del detalle de cliente
 * (`MapaDeLaPuerta`, en `:app`) y el mapa de los lugares de esta pantalla.
 *
 * Vive aquí y no en `:app` porque este módulo es el dueño de
 * `play-services-maps` y `:app` ya depende de él; al revés no se puede. Un solo
 * lugar para el estilo es lo que evita que un mapa esconda las tiendas y el
 * otro no — que es exactamente lo que pasaba: el estilo de noche las escondía y
 * el de día no existía.
 *
 * ## Sin negocios ni transporte, en los dos temas
 *
 * Decisión del dueño mirando el aparato: *"que no muestre los nombres e iconos
 * de los establecimientos y tiendas, siento que quedan muy feos"*. `poi` apaga
 * todos los puntos de interés —tiendas, restaurantes, escuelas, iglesias,
 * parques— con su icono y su nombre; lo que se lee con eso es la calle y el
 * marcador, que es lo único que el cobrador vino a buscar. El transporte se
 * apaga por la misma razón y porque el estilo de noche ya lo hacía.
 *
 * Lo que **no** se toca en el de día: los colores y los nombres de las calles.
 * La calle es cómo el cobrador ubica la puerta.
 *
 * Van como constantes de texto y no como `res/raw`: es un archivo menos que
 * mantener por doce reglas de color.
 *
 * El `remember` va **fuera** de cualquier rama condicional: un `remember` dentro
 * de un `if` cambia de posición en la slot table cuando la rama cambia, y
 * Compose lo trata como otro `remember`.
 */
@Composable
fun propiedadesDelMapa(): MapProperties {
    val estiloDeDia = remember { MapStyleOptions(ESTILO_CLARO) }
    val estiloDeNoche = remember { MapStyleOptions(ESTILO_OSCURO) }
    return MapProperties(mapStyleOptions = if (appDarkTheme()) estiloDeNoche else estiloDeDia)
}

/** El mapa de siempre, sin puntos de interés ni transporte. */
internal const val ESTILO_CLARO = """
[
  {"featureType":"poi","stylers":[{"visibility":"off"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]}
]
"""

/**
 * El estilo de noche: geometría, agua, calles y etiquetas en la paleta oscura
 * de la app. Sin él el mapa queda como un rectángulo blanco dentro de una
 * pantalla negra.
 */
internal const val ESTILO_OSCURO = """
[
  {"elementType":"geometry","stylers":[{"color":"#1f2421"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#8d968f"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#1f2421"}]},
  {"featureType":"poi","stylers":[{"visibility":"off"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2b322e"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#9aa39c"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#343c37"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#3d4741"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#16211e"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#5a655e"}]}
]
"""

package com.example.msp_app.feature.compartirproductos.render

import android.graphics.Color

/**
 * Las medidas de la tarjeta «1 · Editorial» aprobada. El diseño se hizo en un
 * lienzo de 450×800; la imagen final es 1080×1920, o sea [ESCALA] = 2.4 veces.
 * Cada constante dice el valor del diseño y su factor, para poder cotejarla
 * con `Main.dc.html` / `EditorialLargo.dc.html` / `EditorialSinFoto.dc.html`.
 */
internal object Medidas {
    const val ANCHO = 1080
    const val ALTO = 1920
    const val ESCALA = 2.4f

    // Zona segura: WhatsApp Estados pone su barra arriba (~6 %) y el botón de responder abajo (~8 %).
    const val ZONA_SEGURA_ARRIBA = 116 // 6 % de 1920 = 115.2
    const val ZONA_SEGURA_ABAJO = 154 // 8 % de 1920 = 153.6

    const val MARGEN_LATERAL = 67 // 28 px del diseño
    const val RELLENO_ARRIBA = 58 // 24 px
    const val RELLENO_ABAJO = ZONA_SEGURA_ABAJO // 64 px del diseño (el de Main es 60: aquí se respeta la zona segura)

    // Foto
    const val FOTO_ALTO_MAXIMO = 1008 // 420 px: 52 % del alto
    const val FOTO_ALTO_SIN_FOTO = 1128 // 470 px
    const val FOTO_ALTO_MINIMO = 768 // 40 %: aunque el texto crezca, la foto no baja de aquí
    const val FOTO_FOCO_X = 0.5f // object-position: 50% 60%
    const val FOTO_FOCO_Y = 0.6f

    // Marca arriba a la izquierda
    const val LOGO_ARRIBA = 134 // 56 px
    const val LOGO_LADO = 62 // 26 px
    const val LOGO_SEPARACION = 19 // 8 px
    const val MARCA_TAMANO = 29f // 12 px
    const val MARCA_ESPACIADO_EM = 0.2f // 2.4 px sobre 12 px

    // Categoría
    const val CATEGORIA_TAMANO = 26f // 11 px
    const val CATEGORIA_ESPACIADO_EM = 0.18f // 2 px sobre 11 px
    const val CATEGORIA_A_TITULO = 14 // 6 px

    // Título
    const val TITULO_MAXIMO = 110f // 46 px
    const val TITULO_MINIMO = 60f
    const val TITULO_PASO = 2f
    const val TITULO_LINEAS = 2
    const val TITULO_ESPACIADO_EM = -0.011f // -0.5 px sobre 46 px
    const val TITULO_INTERLINEA = 1.0f // el diseño usa 0.98 em; 1.0 evita que un acento toque el renglón de arriba

    // Detalle
    const val DETALLE_TAMANO = 34f // 14 px
    const val DETALLE_LINEAS = 3
    const val DETALLE_INTERLINEA = 1.4f
    const val TITULO_A_DETALLE = 10 // 4 px

    // Bloque de precios
    const val BLOQUE_RADIO = 43f // 18 px
    const val BLOQUE_RELLENO_LATERAL = 43 // 18 px
    const val BLOQUE_RELLENO_VERTICAL = 10 // 4 px
    const val FILA_RELLENO = 26 // 11 px
    const val FILA_ETIQUETA = 34f // 14 px
    const val FILA_CONTADO_CIFRAS = 67f // 28 px
    const val FILA_CONTADO_CIFRAS_ESPACIADO_EM = -0.029f // -0.8 px sobre 28 px
    const val FILA_OTRA_CIFRAS = 53f // 22 px
    const val FILA_OTRA_CIFRAS_ESPACIADO_EM = -0.023f // -0.5 px sobre 22 px
    const val SEPARADOR_GRUESO = 2f // 1 px

    // Una sola fila (EditorialLargo): sin bloque blanco, con regla arriba.
    const val SOLA_RELLENO_ARRIBA = 34 // 14 px
    const val SOLA_ETIQUETA = 31f // 13 px
    const val SOLA_CIFRAS = 96f // 40 px
    const val SOLA_CIFRAS_ESPACIADO_EM = -0.03f // -1.2 px sobre 40 px

    // Pie
    const val PIE_TAMANO = 24f // 10 px
    const val SEPARACION_MINIMA = 24

    // Sin foto
    const val MONOGRAMA_TAMANO = 288f // 120 px

    // Colores del diseño
    val FONDO = Color.parseColor("#f4f1ec")
    val TINTA = Color.parseColor("#1c1917")
    val TINTA_SUAVE = Color.parseColor("#57534e")
    val TINTA_TENUE = Color.parseColor("#78716c")
    val BLANCO = Color.parseColor("#ffffff")
    val SEPARADOR_FILAS = Color.parseColor("#ebe6de")
    val REGLA_SOLA = Color.parseColor("#d6d1c8")
    val FONDO_SIN_FOTO = Color.parseColor("#e8e3da")
    val MONOGRAMA = Color.parseColor("#cfc7ba")

    const val JPEG_CALIDAD = 88
}

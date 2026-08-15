package com.example.msp_app.core.appgate.download

import androidx.core.content.FileProvider

/**
 * `FileProvider` propio de la compuerta.
 *
 * Es una subclase vacía y **tiene que serlo**: el merger de manifiestos
 * identifica cada `<provider>` por su `android:name`, y `:app` ya declara uno
 * con `androidx.core.content.FileProvider` (para las fotos de cobranza). Dos
 * declaraciones de la misma clase chocan aunque tengan autoridades distintas.
 * Con un nombre propio, cada módulo conserva su autoridad y su
 * `FILE_PROVIDER_PATHS` sin `tools:replace` de por medio.
 */
class AppGateFileProvider : FileProvider()

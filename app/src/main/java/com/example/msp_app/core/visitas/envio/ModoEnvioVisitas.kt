package com.example.msp_app.core.visitas.envio

/**
 * Kill-switch del envío de visitas (spec visitas §11 paso 2). Documento
 * `config/visitas_envio`, campo `modo`. NUNCA `config/api_settings`: ese
 * `baseURL` es el kill-switch de la flota entera (COBRANZA-SYNC.md §9).
 *
 * V3 = envío blindado (por omisión: el viejo es el que pierde visitas).
 * V2_ACTUAL = el envío de hoy, conservado en el APK solo durante P1.
 */
enum class ModoEnvioVisitas { V3, V2_ACTUAL }

/**
 * Pura. Solo el texto exacto `"v2_actual"` vuelve al envío de hoy; documento
 * ausente, campo ausente, tipo equivocado o cualquier otro valor → V3.
 */
fun modoDesde(datos: Map<String, Any?>?): ModoEnvioVisitas =
    if ((datos?.get("modo") as? String) == "v2_actual") ModoEnvioVisitas.V2_ACTUAL else ModoEnvioVisitas.V3

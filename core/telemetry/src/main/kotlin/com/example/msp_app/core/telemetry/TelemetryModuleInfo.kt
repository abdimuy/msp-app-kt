package com.example.msp_app.core.telemetry

/**
 * Marcador temporal del esqueleto de `:core:telemetry` (Plan 4, Task 1).
 *
 * No contiene lógica de negocio: solo prueba que el módulo compila con
 * Compose + Room + Hilt + KSP conviviendo, antes del store real
 * (`telemetry_db`, entidad + DAO + cola, Task 2), que reemplaza este
 * archivo.
 */
object TelemetryModuleInfo {
    const val NAME: String = "core-telemetry"
}

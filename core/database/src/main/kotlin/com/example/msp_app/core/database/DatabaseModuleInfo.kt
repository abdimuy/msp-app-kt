package com.example.msp_app.core.database

/**
 * Marcador temporal del esqueleto de `:core:database` (Plan 2, Task 1).
 *
 * No contiene lógica de negocio: solo prueba que el módulo compila con
 * Room + Hilt + KSP conviviendo, antes del hoist real de `AppDatabase`
 * (Task 2), que reemplaza este archivo.
 */
object DatabaseModuleInfo {
    const val NAME: String = "core-database"
}

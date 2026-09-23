package com.example.msp_app.core.sync.cobranza

/**
 * Tope de parámetros por `IN (...)` en una sola sentencia.
 *
 * `SQLITE_MAX_VARIABLE_NUMBER` es **999** en el SQLite del framework para todo
 * Android por debajo de API 31, y la app declara `minSdk = 24`. Pasarse lanza
 * *"too many SQL variables"*, que en el sync lo atrapa el `try/catch` externo:
 * el cursor no avanza, **las ventas dejan de sincronizar también** porque
 * comparten corrida, y cada tick de 30 s reintenta y vuelve a fallar. El
 * teléfono se congela en silencio.
 *
 * Se deja margen por debajo de 999 porque los conjuntos que se trocean no son
 * el único parámetro de la sentencia.
 *
 * **Ninguna prueba puede detectar que falta el troceo**: Robolectric corre
 * sobre el SQLite de escritorio, cuyo tope es de decenas de miles. Por eso el
 * porqué vive aquí, en el código, que es la única red que queda. Ver también
 * el §1 de `CLAUDE.md` ("Trocear todo `IN (...)` sobre conjuntos sin cota").
 */
internal const val SQLITE_MAX_IN_PARAMS = 900

# Busqueda de Clientes para Ventas Locales

**Fecha:** 2026-02-22
**Estado:** Aprobado

## Objetivo

Permitir buscar clientes existentes (43k registros) al crear ventas locales, auto-rellenar el nombre del cliente, y mantener la lista sincronizada con el servidor cada 24 horas.

## Decisiones

- **Sync strategy:** Full Replace (DELETE ALL + INSERT ALL en transaccion cada 24h)
- **Auto-fill:** Solo el nombre del cliente
- **Estatus:** Mostrar todos los clientes con indicador visual de estatus
- **UX:** Pantalla de busqueda separada (modal)

## 1. Datos y Almacenamiento

**Nueva entidad Room: `ClienteEntity`**

| Campo | Tipo | Notas |
|-------|------|-------|
| `CLIENTE_ID` | `Int` | Primary Key |
| `NOMBRE` | `String` | Indexado para busqueda |
| `ESTATUS` | `String` | A, B, C, V |
| `CAUSA_SUSP` | `String?` | Nullable |

Nueva tabla en `AppDatabase` (bump version a 20). Se mantiene `fallbackToDestructiveMigration()`.

**DAO: `ClienteDao`**

- `insertAll(clientes: List<ClienteEntity>)` - Insert batch
- `deleteAll()` - Limpia tabla
- `searchByNombre(query: String): List<ClienteEntity>` - LIKE query con `%query%`
- `getCount(): Int` - Para mostrar stats de sync

## 2. Sync Strategy (Full Replace, cada 24h)

**Nuevo endpoint Retrofit:** `GET clientes/` retorna `ClienteResponse`

**Modelo de API:**

```kotlin
enum class EstatusCliente { A, B, C, V }

data class Cliente(
    val CLIENTE_ID: Int,
    val NOMBRE: String,
    val ESTATUS: EstatusCliente,
    val CAUSA_SUSP: String?
)

data class ClienteResponse(
    val error: String,
    val body: List<Cliente>
)
```

**Nuevo Worker: `ClienteSyncWorker`**

- `PeriodicWorkRequest` cada 24 horas
- Constraint: requiere conexion a internet
- Flow: `GET /clientes/` -> `@Transaction { deleteAll() + insertAll() }`
- Guarda `lastSyncTimestamp` en SharedPreferences
- Se registra en `MspApplication.kt`
- Primera carga: se dispara tambien si la tabla esta vacia

## 3. Capa de Datos

- **`ClienteDataSource`** - Wrapper del DAO (patron de `LocalSaleDataSource`)
- **`ClienteRepository`** - Expone busqueda y sync, envuelve DataSource + API

## 4. UX - Pantalla de Busqueda

**Flujo del usuario:**

1. En `NewSaleScreen`, el campo de nombre muestra un boton/icono de busqueda
2. Al tocar, abre pantalla modal `ClienteSearchScreen`
3. La pantalla tiene:
   - `SearchBar` arriba (Material3)
   - Lista de resultados usando `FuzzyClientSearch` existente
   - Cada item: nombre + badge de estatus con color
   - Colores: A=verde, B=amarillo, C=naranja, V=rojo
   - Si tiene `CAUSA_SUSP`, se muestra como subtexto
4. Al seleccionar, regresa a `NewSaleScreen` con nombre rellenado
5. Se guarda `CLIENTE_ID` en `LocalSaleEntity` (nuevo campo nullable)
6. El usuario puede editar el nombre manualmente despues

**Busqueda:** Minimo 2 caracteres. `FuzzyClientSearch` con threshold 60. Max 20 resultados.

## 5. Cambios a LocalSaleEntity

Nuevo campo: `CLIENTE_ID: Int?` (nullable para backwards compat)

## 6. Archivos a Crear/Modificar

| Archivo | Accion |
|---------|--------|
| `data/local/entities/ClienteEntity.kt` | Crear |
| `data/local/dao/ClienteDao.kt` | Crear |
| `data/local/datasource/ClienteDataSource.kt` | Crear |
| `data/repository/ClienteRepository.kt` | Crear |
| `data/api/services/clientes/ClientesApi.kt` | Crear |
| `data/api/models/ClienteResponse.kt` | Crear |
| `workers/ClienteSyncWorker.kt` | Crear |
| `features/sales/viewmodels/ClienteSearchViewModel.kt` | Crear |
| `features/sales/screens/ClienteSearchScreen.kt` | Crear |
| `data/local/AppDatabase.kt` | Modificar (agregar entity + dao + version bump) |
| `data/local/entities/LocalSaleEntity.kt` | Modificar (agregar CLIENTE_ID) |
| `features/sales/screens/NewSaleScreen.kt` | Modificar (agregar boton busqueda) |
| `features/sales/viewmodels/NewLocalSaleViewModel.kt` | Modificar (agregar CLIENTE_ID) |
| `MspApplication.kt` | Modificar (registrar worker periodico) |

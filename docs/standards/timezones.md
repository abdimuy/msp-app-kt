# Time & Timezone Standard

**Regla única:** guarda UTC, compara en UTC, muestra en hora local de negocio.
Zona de negocio: `America/Mexico_City`.

Si tienes dudas en algo que no esté aquí, **no improvises** — pregunta y actualiza este doc.

---

## TL;DR en 5 líneas

1. Todo timestamp se guarda y viaja como `Instant` (ISO 8601 UTC con `Z`).
2. Toda fecha calendario se guarda y viaja como `LocalDate` (`yyyy-MM-dd`), sin hora.
3. Nadie toca `java.time.*` directo — todo pasa por `AppTime` y `AppClock`.
4. `now()` es `AppClock.now()`. Nunca `Instant.now()` / `LocalDate.now()` / `LocalDateTime.now()`.
5. Formatear para UI es la **última** operación. Nunca formatees antes de guardar ni de comparar.

---

## Los tipos — cuándo cada uno

| Tipo | Úsalo para | Ejemplos en la app |
|---|---|---|
| `Instant` | Momento que ya ocurrió o está a punto de ocurrir. Siempre UTC. | `FECHA_VENTA`, `FECHA_HORA_PAGO`, logs, timestamps de captura |
| `LocalDate` | Fecha de calendario sin hora. | `DIA_COBRANZA`, fecha del reporte, cumpleaños |
| `LocalDateTime` | Solo para eventos futuros agendados en hora local (p. ej. "cita 15 jun 10am CDMX"). | Raro en esta app |
| `ZonedDateTime` | Solo si UI necesita mostrar hora + zona explícita. | Casi nunca |

**Prohibido en código de negocio:** `java.util.Date`, `SimpleDateFormat`, `LocalDateTime.now()`.

---

## API oficial: `core/time/`

Todo lo que necesitas está ahí. Si algo te falta, agrégalo **a `AppTime`**, no en el call site.

### `AppClock`

```kotlin
interface AppClock {
    fun now(): Instant
    companion object { val System: AppClock = /* ... */ }
}
```

- Inyéctalo en ViewModels, use cases, repos.
- En tests pasa un fake con tiempo fijo.

### `AppTime`

```kotlin
val BUSINESS_ZONE: ZoneId = ZoneId.of("America/Mexico_City")

object AppTime {
    fun toWireFormat(instant: Instant): String
    fun parseWireFormat(iso: String): Instant
    fun toBusinessDate(instant: Instant): LocalDate
    fun todayInBusinessZone(clock: AppClock = AppClock.System): LocalDate
    fun formatForDisplay(instant: Instant, pattern: String = "dd/MM/yyyy HH:mm"): String
    fun formatDate(date: LocalDate, pattern: String = "dd/MM/yyyy"): String
}
```

---

## Recetas (copia-pega)

### Guardar "cuándo ocurrió esto"

```kotlin
// Creación de venta / pago / log / captura
val timestamp = AppTime.toWireFormat(clock.now())
// se guarda en Room / se manda al backend
```

### Leer un timestamp y mostrarlo en UI

```kotlin
val instant = AppTime.parseWireFormat(sale.FECHA_VENTA)
val displayed = AppTime.formatForDisplay(instant, "dd/MM/yyyy hh:mm a")
```

### "¿Esto pasó hoy (en hora de negocio)?"

```kotlin
val today = AppTime.todayInBusinessZone(clock)
val saleDate = AppTime.toBusinessDate(AppTime.parseWireFormat(sale.FECHA_VENTA))
val isToday = saleDate == today
```

### Filtrar un rango de fechas calendario

```kotlin
// Ventas del 1 al 7 de abril en zona de negocio
val start = LocalDate.parse("2026-04-01")
val end = LocalDate.parse("2026-04-07")

sales.filter {
    val d = AppTime.toBusinessDate(AppTime.parseWireFormat(it.FECHA_VENTA))
    d in start..end
}
```

### Mandar rango al backend

```kotlin
val start = LocalDate.parse("2026-04-01")
api.getTransfers(
    fechaInicio = start.toString(),  // "2026-04-01"
    fechaFin = start.toString()
)
```

### Fecha solo calendario (día de cobranza)

```kotlin
val diaCobranza: LocalDate = LocalDate.parse(sale.DIA_COBRANZA)
if (today.isAfter(diaCobranza)) { /* atrasado */ }
```

### Sumar/restar tiempo

```kotlin
// 30 días desde la venta
val due = AppTime.parseWireFormat(sale.FECHA_VENTA).plus(30, ChronoUnit.DAYS)

// 7 días calendario antes de hoy
val weekAgo = today.minusDays(7)
```

---

## Anti-patterns que harán fallar el review

```kotlin
// ❌ MAL — mezcla local con UTC
val todayPrefix = LocalDate.now().format(dateFormatter)
sales.filter { it.FECHA_VENTA.startsWith(todayPrefix) }

// ✅ BIEN
val today = AppTime.todayInBusinessZone(clock)
sales.filter { AppTime.toBusinessDate(AppTime.parseWireFormat(it.FECHA_VENTA)) == today }
```

```kotlin
// ❌ MAL — LocalDateTime.now() es ambigua (zona del dispositivo)
val timestamp = LocalDateTime.now().toString()

// ✅ BIEN
val timestamp = AppTime.toWireFormat(clock.now())
```

```kotlin
// ❌ MAL — SimpleDateFormat es legacy, thread-unsafe
SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

// ✅ BIEN
AppTime.formatForDisplay(clock.now())
```

```kotlin
// ❌ MAL — comparar strings ISO
if (a.fecha > b.fecha) { /* ... */ }

// ✅ BIEN
if (AppTime.parseWireFormat(a.fecha).isAfter(AppTime.parseWireFormat(b.fecha))) { /* ... */ }
```

```kotlin
// ❌ MAL — LocalDate.now() como key (depende de TZ del device)
val todayKey = LocalDate.now().toString()

// ✅ BIEN
val todayKey = AppTime.todayInBusinessZone(clock).toString()
```

---

## Storage (Room / wire format)

| Columna | Tipo SQL | Contenido | Ejemplo |
|---|---|---|---|
| Timestamp (momento real) | `TEXT` | `Instant.toString()` con `Z` | `"2026-04-16T02:30:15Z"` |
| Fecha calendario | `TEXT` | `LocalDate.toString()` | `"2026-04-16"` |
| Duración | `INTEGER` | segundos o ms (documentar cuál) | `3600` |

Wire API usa exactamente el mismo formato. **No hay "formato de DB" vs "formato de API"** — es uno solo.

---

## Robustez al parsear

`AppTime.parseWireFormat` acepta cualquiera de estos y los normaliza a `Instant`:

- `2026-04-16T02:30:15Z` ✅ preferido
- `2026-04-16T02:30:15.123Z` ✅
- `2026-04-16T02:30:15-06:00` ✅
- `2026-04-16T02:30:15` ⚠️ asume zona de negocio (legacy)
- `2026-04-16` ⚠️ asume medianoche en zona de negocio (legacy)

**Al emitir siempre usamos la primera forma.** Los otros solo se aceptan al leer data histórica o de backends legacy.

---

## Tests obligatorios para lógica de fechas

Cualquier feature que filtre/compare por fecha debe tener al menos estos casos:

1. Evento a las **23:00 CDMX** (UTC del día siguiente) — debe contar en la fecha local correcta.
2. Evento a las **01:00 CDMX** (mismo día en UTC) — debe contar en la fecha local correcta.
3. Evento en **transición DST** (último domingo de octubre / primer domingo de abril en MX antes de 2023; desde 2023 MX eliminó DST, pero si el backend envía datos en UTC con otros orígenes, el cálculo debe seguir siendo correcto).
4. Evento con timestamp **en formato legacy** (sin `Z`) — debe parsear sin lanzar.

Usa `FakeClock` para fijar `now()` y reproducir exactamente el momento del bug.

---

## Enforcement

- `detekt` con `ForbiddenImport` bloquea el uso directo de `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `java.text.SimpleDateFormat`, `java.util.Date`.
- Pre-commit hook corre `detekt` + tests antes del commit.
- Cualquier excepción necesita comentario que explique por qué, aprobado en review.

---

## Migración de código existente

Si tocas un archivo con patrones viejos (`LocalDateTime.now()`, `SimpleDateFormat`, `startsWith` en ISO strings), **migralo al paso por `AppTime`** en el mismo PR. No dejes deuda.

Si el archivo es muy grande y migrarlo escapa del scope, anota un `TODO(tz)` con el número del issue y abre ticket separado.

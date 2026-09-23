# Pagos y visitas — expediente de decisiones

> Todo lo investigado y decidido, para no re-derivarlo. El plan de ejecución vive en
> `2026-09-01-pagos-y-visitas.md`. Fecha: 2026-09-01.
> Informes fuente: `.superpowers/discovery/{pagos-msp,visitas-msp,kollect,huecos-y-mejoras}.md`

## 1. Qué se quiere y por qué

Migrar **pagos** y **visitas** a la arquitectura multi-módulo (`:core:*` / `:feature:*`), igual que
`:feature:collectionReport`. Juntas porque son casi la misma cosa: captura offline, imágenes y sincronización.

1. **Visitas alcanza la paridad de pagos** — sobre todo que sincronice sola, sin botón.
2. **Fotos en ambas.**
3. **Rediseñar la captura de visita** para que alimente una futura sección de **ordenamiento
   inteligente de ruta** — de lo que más podría mover cuánto se cobra.

El usuario del sistema es un **cobrador en la calle**: teléfono de gama baja, sol directo, una mano
ocupada, señal intermitente, y maneja dinero ajeno. Ese contexto manda sobre cualquier decisión de UI.

## 2. Estado actual

### 2.1 La brecha visitas ↔ pagos

| | pagos | visitas |
|---|---|---|
| Disparadores de sync | **6**: guardar, apertura, poll 30s, **SSE**, conectividad, periódico 15 min | ~1, frágil |
| Reconciliador | digest-first + rescate `by-ids` | **ninguno** |
| Encolado al guardar | directo | **indirecto, vía `UpdateLocationService`** |
| Anti-duplicado | 3 capas | UUID solamente |
| Tope de reintentos | sí | **`RETRY` es infinito** |
| Superficie de API | 11 endpoints | **1** (`POST`) |

### 2.2 Superficie de API — el hueco de fondo

Pagos: `POST /v2/cobranza/pagos` · `GET .../pagos/{id}` · `GET .../sync/pagos/zona/{z}` · `.../digest`
· `.../ids` · `.../by-ids` (más equivalentes de saldos y ventas). Visitas: `POST /v2/visitas`. Eso es todo.

El teléfono no puede preguntar *"¿ya tienes esta visita?"*. Por eso visitas no reconcilia y por eso
existe el botón. **No es un hueco de arquitectura en la app: es superficie que falta en el servidor.**
Pero está cerca: `internal/visitas/ports/outbound/repo.go` ya declara `FindByID` y `visitasfb` ya lo
implementa. El `ID` del `CrearVisitaBody` ya es **UUID del cliente y clave de idempotencia end-to-end**,
con `ErrVisitaYaExiste` en colisión.

### 2.3 msp ya tiene push

`internal/cobranza/infra/cobranzahttp/handlers_sse.go:139` sirve **SSE** y el cliente ya lo consume
(`CobranzaSseProvider.kt`). No aparece al listar rutas Retrofit porque es un stream aparte con OkHttp.

### 2.4 Fotos — el contrato ya existe del lado de pagos

`V2PaymentsApi.crearPago` ya es `@Multipart` con `@Part("datos")`. El servidor ya acepta imágenes
(`dto_pago_recibido.go:107`, `Imagen []huma.FormFile`, whitelist de tipos). La app ya captura por intent
(`NewSaleFormViewModel.createCameraUri()` / `processCameraImage()`), tiene `ImageCompressor` en
`core/utils` y arma multipart con `getImageParts()`. **No hay CameraX, pero sí hay pipeline.**
Para visitas falta el multipart del servidor (`V2VisitsApi.kt:11-12` dice *"visitas never carry a
comprobante image"*).

### 2.5 kollect — qué es real y qué es papel

**Real:** cola durable en Room (`sync_queue` + `PushCoordinator`/`PushReconciler`) con lease/reclaim,
backoff, dedup y gate por adjuntos · bloques de folio por dispositivo · revoke remoto · SQLCipher +
PIN/biométrico + Keystore, TTL offline 7 días · `core/media` completo (CameraX, galería, compresión, voz).

**Papel:** Centrifugo y FCM son scaffolds vacíos con `TODO(API-6)` · permisos cacheados (el login manda
`emptySet()`) · MapLibre no existe · "geocerca" es solo captura de GPS, nunca valida radio ·
`feature/corte` retirado (`a6cc06d`).

`core/media` y `core/sync/attachment` son casi puro Android estándar — portar sería mecánico.

### 2.6 Huecos que ninguna app cubre

Corte sin arqueo · sin anulación por el cobrador · sin re-auth en el momento sensible
(`BiometricAuthenticator` sin usar) · `IN (...)` sin trocear en varias DAOs de kollect (bug de 999).

## 3. Decisiones tomadas

| Decisión | Razón |
|---|---|
| **Fotos en pagos y visitas** | Para pagos el contrato ya existe de los dos lados |
| **NO portar la cola durable de kollect** | msp manda la imagen dentro del mismo request que la mutación: atómico, sin staging ni huérfanos. kollect resuelve una escala que msp no tiene |
| **Captura como pantalla de navegación**, no `FullScreenDialog` | La cámara saca de la app; con `SavedStateHandle` propio la muerte de proceso deja de ser riesgo |
| **Promesa estructurada** (venta + fecha + monto) | Hoy es texto libre y por eso no se puede medir el cumplimiento |
| **Contacto = unión en lectura**, no entidad nueva | Ver §5 |
| **El pago sigue soberano** | 3 capas anti-duplicado y 7 defectos de producción detrás. Si el contacto se acopla al pago, un bug de visitas toca dinero |
| **Cercanía se queda**, pero como *"estoy aquí, muéstrame su venta"* | Hoy es el orden principal de Home y eso optimiza gasolina, no cobranza |
| **Visitar a todos, incluidos los que no pagan** | La insistencia es el trabajo. El problema es **secuencia y hora**, no selección |

## 4. Catálogo de ocho estados (mock `86ad3781`)

Uno por venta, según el último evento del periodo. **Color + ícono + texto — nunca solo color.**

| Estado | Significado | Alcance |
|---|---|---|
| **Pagó** | Cobró completo lo de la semana | venta |
| **Abonó (parcial)** · *por confirmar* | Dio algo pero no lo esperado | venta |
| **Visité — vuelvo** | Pasaste, no se resolvió. Regresas este periodo | venta |
| **Prometió: próxima** | Da hasta la siguiente semana | venta |
| **Se negó / problema** | Rojo **sólido** = escalar | venta |
| **Cita a una hora** | Quedaron de verse. Muestra la hora | **cliente → sus ventas** |
| **No estaba** | Fuiste y no había quién atendiera | **cliente → sus ventas** |
| **Sin tocar** | Nadie trabajó esta cuenta este periodo | venta |

1 venta → cuadro grande en el encabezado. 2+ → mini-cluster + badge "N cuentas" + cuadro por renglón.
"Volver a pasar": hoy con hora (reloj) · otro día con hora (reloj+día) · otro día sin hora (calendario).
Candidatos no resueltos: *renegoció*, *domicilio equivocado*.

## 5. Modelo de datos

**El contacto se deriva, no se escribe.** Pago y visita ya comparten `cliente_id`, `cobrador_id`,
fecha/hora, `lat`/`lon` y UUID de idempotencia. En el teléfono es **el mismo `UpdateLocationService`**
el que captura la ubicación de ambos (`payment_id` y `visit_id`, líneas 54-55).

```
contacto = (cliente, cobrador, cuándo, dónde, resultado, referencia)
   ← MSP_PAGOS     resultado = cobro
   ← MSP_VISITAS   resultado = el estado del semáforo
```

**Regalo del `lat`/`lon`:** comparando la ubicación del pago contra la dirección del cliente se distingue
*"le cobré en su puerta"* de *"me pagó en la tienda"* — el primero cuenta para la mejor hora, el segundo no.

**Las diez etiquetas actuales** de `visitConditionForm` se conservan, agrupadas:
- **No estaba** ← no se encontraba · casa cerrada con candado · solo había menores
- **Visité — vuelvo** ← se asomó pero no salió · no responde aunque está · se escuchan ruidos
- **Se negó** ← no pagará esta ocasión · tiene dinero pero no quiso · fue grosero o agresivo
- **Prometió** ← pidió reagendar visita

**Ficha del cliente — decidido: catálogo cerrado + nota libre.** El catálogo es lo único que puede
alimentar el BTTC (el texto libre no se consulta); la nota libre es lo que lee el humano que llega mañana.
Molde: `SignalType` + `SignalFamily` + `SignalLabels` de kollect, donde agregar un valor **no compila**
hasta que se le pone etiqueta en español.

## 6. Diseño

| Pantalla | Link |
|---|---|
| **Cliente y venta** (completas, claro/oscuro) | `claude.ai/code/artifact/c741ddde-ec64-4d1e-a9d0-810346667653` |
| **Registrar abono** (captura, bloqueo, confirmar, monto raro) | `claude.ai/code/artifact/6b772462-71c5-4be0-8b2a-b4a9dc602653` |
| **Registrar visita** (elegir, prometió, no estaba) | `claude.ai/code/artifact/836239c8-0c79-4e3d-9a1e-8f000bec9d6a` |
| Historial de pagos · 4 opciones (A y B elegidas) | `claude.ai/code/artifact/69032026-0c4e-4c4b-a79e-ae2dcf9d3bcd` |

Referencias de kollect aprobadas: `4c5fff33` Plan del Día · `1562ea75` Lista de clientes ·
`1dd8752b` Abono seguro · `890760e0` Detalle de venta/header · `86ad3781` Estado por venta/semáforo.
En archivos: `kollect-app/docs/design/campo-ui-mockup.html` · `docs/design/receipt-print/`.

**Lenguaje visual acordado (mocks):** negro puro OLED, acento esmeralda `#34D399`, superficie `#141A18`,
líneas `#242D2A`. Títulos grandes 700-800, `tabular-nums` en montos, toques ≥50px.

**✅ Resuelto — manda el azul del design system.** `MspColors.kt` (`#2563EB` marca, `#6FE3C2`
heroProgressFill) es la fuente de verdad. Se conserva de los mocks **la composición, la escala y la
jerarquía**, no la paleta. **Botón primario y todo lo protagónico → azul `#2563EB`. Verde `statusPaid`
→ solo estado. El verde nunca es acción.**

## 7. La lista de clientes

| Pantalla | Líneas | Qué es |
|---|---|---|
| `ClienteSearchScreen` | 279 | sin llamadores — candidata a borrar |
| `SalesListScreen` | 418 | sin llamadores — candidata a borrar |
| **`SalesScreen`** | 267 | **la lista de cobranza.** `EstadoCobranza`, `SALDO_REST`, `SaleItem`. Pestañas POR VISITAR / VISITADOS / PAGADOS |
| `UnifiedSalesScreen` | 973 | **NO es lista de clientes** — lista **ventas locales** (`NewLocalSaleViewModel`, `loadPendingSales()`, `key = { it.LOCAL_SALE_ID }`). Filtros *Pendientes/Enviadas* = estado de subida. **No se toca** |

**El problema estructural:** la lista de cobranza itera con `key = { it.DOCTO_CC_ID }` y pinta `SaleItem`.
**Es por venta, no por cliente.** El cobrador toca una puerta, no una venta.

**El orden se copia** de `SalesScreen.kt:201-203`. Ver Task 17 del plan.

## 8. Bugs y basura

**Bugs:** (1) `UpdateLocationService` sin `try/catch` → `SecurityException` · (2) el encolado de la visita
cuelga de `UpdateLocationService` · (3) `VisitUploadClassifier` `RETRY` puro infinito · (4) el botón
"ENVIAR PAGOS PENDIENTES" dispara cuatro cosas y usa `REPLACE` donde el comentario dice `KEEP` ·
(5) una visita pendiente **bloquea "INICIALIZAR SEMANA"** (`AuthViewModel.kt:117-131`).

**Basura:** pipeline de reporte diario huérfano (~350-400 líneas) · `PaymentsList.kt` · `SortingButtons.kt`
· `ClienteSearchScreen` + `SalesListScreen` (~700 líneas).

## 10. El recomendador de visitas

Términos: **Right Party Contact (RPC)** y **Best Time To Contact (BTTC)**.

0. **Hoy:** cercanía. Optimiza gasolina, no cobranza.
1. **Mejor hora de contacto.** El BTTC sale de contar el RPC por ventana horaria — **son cuentas, no ML.**
   Hoy no se puede porque cobrar no deja registro de contacto; el modelo de §5 lo desbloquea.
2. **Propensity to pay** — ordenar por `P(pago) × monto`.
3. **Uplift** — quién paga *porque* fuiste.

**Corrección del usuario:** en este negocio **hay que ir con todos**. El uplift no sirve para excluir;
el problema es de **secuencia y hora**. Eso hace del BTTC la palanca principal.

**Dos cosas que cuestan casi nada ahora y son imposibles de reconstruir después:** guardar **la
recomendación**, no solo el resultado · dejar espacio para un **grupo de control**.

Las familias sirven para decidir **cómo insistir**: ausencia → cambiar de hora · evasión → otro día o
escalar · negativa → llegar sabiendo qué se dijo · compromiso → llegar el día prometido.

Fuentes: Tratta (RPC) · Rezo.ai · Wikipedia (uplift modelling) · Dista · Vymo.

## 13. Los ocho estados — qué se puede derivar hoy

`EstadoCobranza` es **100% local y calculado en Kotlin — nunca viene del API**
(`core/database/.../sale/EstadoCobranza.kt:11-17`). `VisitStatusMapper.kt:23-27` lo deriva de `tipoVisita`
en **tres buckets**. `PaymentsViewModel.kt:464` fija `PAGADO` en **cada** pago, **sin comparar el monto**,
aunque `Sale.PARCIALIDAD` (`Sale.kt:23`, servida por `VentaDto.kt:49/102`) está en los dos lados.

**El periodo de cobro lo define el cobrador.** `AuthViewModel.kt:118-164` escribe `Timestamp.now()` directo
a Firestore (`FECHA_CARGA_INICIAL`, `Constants.kt:26`). **msp-api nunca ve ese botón**; sí lee el campo
read-only en `internal/rutas/infra/rutasfirestore/calendario_client.go:33-60`, pero **solo en reportes**,
no en `internal/cobranza`, que es quien sirve `VentaDTO` (`dto_ventas.go:14-62`, sin estado ni ventana).

**Lógica que ya existe en el módulo equivocado:** `internal/rutas/domain/cobranza.go:12-50` y `aporte.go`
ya calculan `AbonoSemana` contra `Parcialidad` por venta — exactamente lo que separa *Pagó* de *Abonó
parcial*. Vive en reportes de zona y nunca se expone como estado.

**La visita ya es del cliente en el modelo** (`internal/visitas/domain/visita.go:46-61` indexa por
`ClienteID`), pero `VisitsLocalDataSource.kt:58-69` la **colapsa a UNA venta** y no propaga.
`TipoVisita` es **texto libre** (máx 100 chars, `visita.go:15/202`); la app manda 11 literales de
`Constants.kt:31-41`, y **`FUE_GROSERO` cae en el mismo bucket que `PIDE_TIEMPO`**.

**Veredicto: ninguno de los ocho es derivable hoy tal cual, ni en el servidor ni en la app.**

- **Solo con lógica nueva, sin datos nuevos (5):** Pagó · Abonó parcial · Visité — vuelvo · No estaba ·
  Sin tocar.
- **Requieren dato nuevo (2):** **Prometió: próxima** (no hay fecha ni monto comprometido) ·
  **Cita a una hora** (la hora solo existe embebida en el texto libre de `NOTA`,
  `NewVisitDialog.kt:119-125`).
- **Ambiguo (1):** *Se negó / problema* — reclasificable desde texto libre, pero sin bandera de "escalar".

**Lo que esto cierra:** los dos estados imposibles son exactamente los que desbloquea el rediseño de la
captura de visita. El rediseño deja de ser mejora de UI y pasa a ser **prerequisito** de dos octavos del
semáforo — y del BTTC.

**✅ Decidido: los estados se derivan en la app.** El periodo de cobro ya vive en el teléfono, calcular
localmente no necesita plumbing nuevo, y el estado de una visita recién capturada se ve al instante.
**Lo que hay que aceptar:** se reimplementa en Kotlin lógica que ya existe en Go (usarla como referencia
de fórmula) · dos teléfonos podrían mostrar estados distintos del mismo cliente (ventana angosta porque
cada cobrador tiene su ruta) · obliga a arreglar el colapso de `VisitsLocalDataSource.kt:58-69`.

## Pendiente heredado, sin dueño en este plan

Los **3 pares de color bajo AA** parqueados desde el Plan 3, y la allowlist residual de ~14 archivos de
la migración de fechas.

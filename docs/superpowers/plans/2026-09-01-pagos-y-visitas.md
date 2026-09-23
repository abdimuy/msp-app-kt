# Pagos y visitas en `msp-app-kt` — plan de ejecución

> Fecha: 2026-09-01 · Repos: `msp-app-kt` (Android), `msp-api` (Go), `kollect-app` (referencia)
> El expediente completo (investigación y decisiones ya tomadas) vive en `2026-09-01-pagos-y-visitas-EXPEDIENTE.md`.
> Este archivo es SOLO el plan de ejecución: constraints globales + tareas.

---

## Global Constraints (aplican a TODAS las tareas)

Además de `docs/superpowers/plans/DISPATCH-CONVENTIONS.md` (toolchain, commits, contrato de reporte):

- **Fechas:** `AppTime`/`AppClock` en `:core:common.time` es la **única** fuente, zona
  `America/Mexico_City`; `FakeClock` en `:core:testing`. Guardrail `checkNoLegacyDateApi` en `prePushCheck`.
- **Room v27 es INMUTABLE** — hay datos en producción. Solo se reescribe la lógica alrededor.
- **Política de migración:** *auditar y reescribir* con tests de robustez suprema y
  verificación del contrato del API. **No mover a ciegas.**
- **Cada arreglo con una prueba que se ponga ROJA al revertirlo**, verificada de verdad
  (revertir el fix, correr el test, ver el rojo, restaurar).
- **Fakes-only, sin MockK.** Room in-memory o transacciones con rollback. Turbine para Flows.
- **detekt estricto** solo en módulos nuevos (`:core:*`, `:feature:*`); `:app` fuera.
- **Kill-switch de baseURL:** nada `@Singleton` que sostenga un API service de `ApiProvider.create()`.
- **UI:** texto en español, minúsculas, sin punto final, 2-4 palabras. **Nunca "ciclo"**, se dice "semana".
  Toques ≥50px. Roborazzi: regrabar goldens y **revisar las imágenes**.
- **Color:** manda `MspColors.kt`. Azul de marca `#2563EB` para **botón primario y todo lo protagónico**.
  Verde `statusPaid` (`#177245` claro / `#40CB84` oscuro) **solo para estado**, nunca para acción.
  De los mocks se conserva composición, escala y jerarquía — no la paleta esmeralda.
- **La foto nunca bloquea el guardado.** Si la cámara falla, la captura se registra igual.
- `prePushCheck` con el árbol caliente **no prueba nada** — correr `--rerun-tasks` al menos una vez
  antes de afirmar que algo pasa.
- **Regla de control positivo:** una ausencia no es un hallazgo hasta probar que la consulta habría
  encontrado la cosa. Obligatorio antes de cada borrado y antes de reportar "no existe".

### Fuera de alcance (decidido, no relitigar)

Arqueo del corte · anulación por el cobrador · cola durable de kollect · Centrifugo/FCM ·
geocerca · mapa y optimizador de ruta · condonación (se queda igual) · `UnifiedSalesScreen`
(es la lista de **ventas locales**, otro trabajo — **no se toca**) · feed paginado
`/sync/visitas/zona/{z}` en el API.

---

## Task 1 — Borrar lo muerto

**Fase 0a.** Sin dependencias.

Confirmar primero, con **control positivo**, que no se navegan por string de ruta ni se
referencian por reflexión; recién entonces borrar:

- `ClienteSearchScreen` (~279 líneas) — sin llamadores según búsqueda por nombre de clase.
- `SalesListScreen` (~418 líneas) — sin llamadores según búsqueda por nombre de clase.
- `PaymentsList.kt` — sin llamadores.
- `SortingButtons.kt` — sin llamadores.
- El pipeline de reporte diario huérfano tras `:feature:collectionReport`: `ReportModels.kt`,
  la mitad de `ReportFormatters.kt`, `PdfGenerator.generatePdfFromLines` (~350-400 líneas).

**Control positivo obligatorio antes de cada borrado:** demostrar que el método de búsqueda
SÍ encuentra referencias cuando las hay (p. ej. correr la misma búsqueda contra una pantalla
que sabemos viva, como `SalesScreen`, y ver que la encuentra). Sin esa demostración, la
ausencia de resultados no autoriza el borrado.

**NO borrar** nada cuya ausencia de referencias no se pueda probar. Reportar lo que quedó
sin borrar y por qué.

**Verificación:** compila, `prePushCheck` verde. No hace falta test nuevo (es borrado);
sí hace falta que la suite existente siga verde.

---

## Task 2 — Revestir los mocks con `MspColors`

**Fase 0b.** Sin dependencias.

Los cuatro mocks HTML del diseño están pintados en esmeralda `#34D399`. Revestirlos con los
tokens reales de `MspColors.kt`:

- **Azul de marca `#2563EB`** para el botón primario y todo lo protagónico (acciones).
- **Verde `statusPaid`** (`#177245` claro / `#40CB84` oscuro) **solo para estado** — liquidación
  y monto confirmado lo usan porque significan "pagado", no porque sean acciones.
- **El verde nunca es acción.** Si algo es tocable y primario, va azul.
- Se conserva de los mocks **la composición, la escala y la jerarquía**. La paleta esmeralda
  fue el vehículo para acordar el diseño, no el resultado.
- El resto de los estados del semáforo (turquesa parcial, ámbar vuelvo, rojo promesa/negado,
  violeta cita) se mapean a los tokens de `MspColors` que correspondan; si falta un token para
  un estado, **proponerlo** con su valor claro/oscuro y su razón — no inventarlo suelto en el mock.

**No se toca `:feature:collectionReport` ni se regraban sus goldens.**

**DECISIÓN DEL USUARIO (2026-09-01):** los cuatro mocks **NO se republican**. Son artifacts que el
usuario posee, pintados en esmeralda, y se quedan así **como registro de lo que se acordó**. Copia
local sin tocar en `docs/design/mocks/` — es la referencia visual de las Tasks 16-20.

Por lo tanto esta tarea entrega **una sola cosa**:

**`docs/design/paleta-mocks-a-mspcolors.md` — la tabla autoritativa.** Es lo que de verdad gatea a
las Tasks 16-20: el implementador de cada pantalla mira el mock para **composición, escala y
jerarquía**, y mira esta tabla para **color**. Debe listar, fila por fila:

- el color del mock (el hex, y el nombre de la variable CSS: `--ac`, `--teal`, `--amb`, `--red`,
  `--vio`, `--surf`, `--line`, `--ink`, `--mut`, `--faint`, …),
- el token de `MspColors` que lo reemplaza (**leer `MspColors.kt` de verdad** — no inventar nombres),
- su valor en claro y en oscuro,
- y **para qué sirve**: acción / estado / superficie / línea / texto.

Cubrir explícitamente:

- **Acción:** todo lo tocable y protagónico → azul de marca `#2563EB`. El esmeralda `--ac` del mock
  se parte en dos destinos según el uso: **acción → azul**, **estado pagado → `statusPaid`**.
- **Los cinco colores de estado del semáforo:** verde pagado · turquesa parcial (`--teal`) ·
  ámbar vuelvo (`--amb`) · rojo promesa/negado (`--red`) · violeta cita (`--vio`).
- **Los tres montos sugeridos** del mock de abono: verde *esperado hoy* · turquesa *al corriente* ·
  violeta *liquidar*.
- **Superficies y líneas:** `--bg` `#080A09`, `--surf` `#141A18`, `--surf2` `#1B2220`,
  `--line` `#242D2A`, y los tres niveles de texto `--ink` / `--mut` / `--faint`.

**Si falta un token en `MspColors` para algún estado, proponerlo** con nombre, valor claro/oscuro,
razón y **contraste medido contra su fondo** — no inventarlo suelto. Marcar en la tabla cuáles son
propuestas y cuáles ya existen.

**Nota heredada:** hay **3 pares de color bajo AA** parqueados desde el Plan 3 esperando decisión del
usuario (`NIGHT-REPORT.md`, sección B). Si alguno de esos pares aparece en esta tabla, **señalarlo**
en su fila — no resolverlo.

**Verificación:** cada token citado existe de verdad en `MspColors.kt` (o está marcado como
propuesta). Control positivo: si citas un token, demuestra con `grep` que está en el archivo.

**No se toca `:feature:collectionReport` ni se regraban sus goldens.**

**RULING DEL ORQUESTADOR (pre-flight):** los cuatro mocks **NO viven en el repo** — `docs/design/`
solo tiene `reporte-cobranza-mockup.html`. Son artifacts publicados en claude.ai que el usuario posee.
Por eso esta tarea entrega **dos cosas**:

**(a) `docs/design/paleta-mocks-a-mspcolors.md` — la tabla autoritativa.** Es el entregable que gatea
a los Tasks 16-20. Debe listar, fila por fila: el color del mock (hex esmeralda o el que sea), el token
de `MspColors` que lo reemplaza, su valor en claro y en oscuro, y **para qué sirve** (acción / estado /
superficie / línea / texto). Leer `MspColors.kt` de verdad — no inventar nombres de token.
Incluir explícitamente el mapeo de los cinco colores de estado del semáforo: verde pagado ·
turquesa parcial · ámbar vuelvo · rojo promesa/negado · violeta cita. **Si falta un token para algún
estado, proponerlo** con valor claro/oscuro, razón y contraste medido — no inventarlo suelto.

**(b) Los cuatro mocks revestidos.** El orquestador deja los HTML en `docs/design/mocks/` antes de
despachar esta tarea. Editarlos ahí, en su archivo local. El orquestador los republica después —
**el implementador NO usa la herramienta Artifact.**

**Verificación:** revisar cada mock editado y confirmar que **ningún elemento tocable primario quedó
verde** y que el verde solo aparece como estado. Reportar la lista de elementos que cambiaron de verde
a azul, con su selector, para que se pueda auditar.

---

## Task 3 — `try/catch` en `UpdateLocationService.getCurrentLocation`

**Fase 1, bug 1.** Sin dependencias. Afecta pagos **y** visitas.

`UpdateLocationService` llama `client.getCurrentLocation` **sin `try/catch`** →
`SecurityException` si falta el permiso de ubicación, y se cae el flujo que estaba corriendo.

Arreglar: capturar la excepción, registrar el evento por telemetría, y **seguir sin ubicación**
(la ubicación es un adorno del contacto, no un requisito del pago ni de la visita).

**Auditar primero:** verificar qué otras excepciones puede lanzar la ruta (`SecurityException`,
pero también fallos de Play Services) y qué pasa hoy aguas arriba cuando revienta.

**Test que se ponga ROJA al revertir:** con un fake del cliente de ubicación que lance
`SecurityException`, el caso de uso debe completar sin propagar la excepción y con la
ubicación en nulo. Verificar el rojo de verdad.

---

## Task 4 — Tope de reintentos al `RETRY` puro de `VisitUploadClassifier`

**Fase 1, bug 2.** Sin dependencias.

`VisitUploadClassifier`: `RETRY_THEN_DONE` respeta `maxAttempts = 10`, pero el **`RETRY` puro**
(401 / 408 / 409 / 425 / 429) **reintenta indefinidamente**. Un teléfono con un token vencido
reintenta para siempre y quema batería.

**Auditar primero** el clasificador completo y su equivalente de pagos: pagos ya resolvió esto
y la forma correcta probablemente ya existe del lado de cobranza. Copiar esa forma, no inventar otra.

Ojo con el **409**: en visitas significa `ErrVisitaYaExiste` (el `ID` del `CrearVisitaBody` es
UUID de idempotencia end-to-end) — o sea que 409 es **éxito**, no un reintento. Decidir
explícitamente si 409 sale de `RETRY` hacia `DONE` y dejarlo documentado.

**Test que se ponga ROJA al revertir:** para cada código del conjunto `RETRY` puro, tras
`maxAttempts` el clasificador deja de pedir reintento. Verificar el rojo.

---

## Task 5 — Desacoplar el encolado de la visita de `UpdateLocationService`

**Fase 1, bug 3.** Sin dependencias. **Bloquea la Fase 4 (Task 14).**

Hoy `saveVisit()` solo escribe Room; **el encolado de la subida cuelga de `UpdateLocationService`**.
Si el servicio no corre (permiso denegado, Play Services caído, el `SecurityException` del Task 3),
**la visita nunca se encola y nunca sube**.

Arreglar: `saveVisit()` debe **encolar la subida él mismo**, en la misma transacción conceptual
que la escritura local. La ubicación llega después y **actualiza** la fila; nunca es la que
dispara el encolado.

**Auditar primero:** cómo lo hace pagos (encolado directo al guardar) y copiar esa forma.
Verificar que la actualización tardía de ubicación no pise una visita ya subida ni cree una
segunda subida.

**Test que se ponga ROJA al revertir:** guardar una visita con el servicio de ubicación
completamente ausente y comprobar que igual queda encolada para subir. Verificar el rojo.

---

## Task 6 — Renombrar el botón de sync y alinear `KEEP`/`REPLACE`

**Fase 1, bug 4.** Sin dependencias.

Dos defectos en el mismo lugar:

1. El botón dice **"ENVIAR PAGOS PENDIENTES"** pero dispara **cuatro** cosas: visitas, pagos,
   garantías y eventos. Renombrarlo a algo veraz, en español, minúsculas, 2-4 palabras
   (p. ej. "enviar pendientes"). Nunca "ciclo".
2. `SyncAllPendingWorkUseCase.kt:42-46` usa `REPLACE` cuando el comentario del código dice `KEEP`.
   **Auditar cuál de los dos es el correcto** — no alinear el comentario al código por comodidad.
   `REPLACE` cancela un envío en curso y puede duplicar trabajo; `KEEP` deja correr el que ya está.
   Decidir con el contrato real de WorkManager enfrente y documentar la decisión en el commit.

**Test que se ponga ROJA al revertir:** el caso de uso encola con la política decidida.
Verificar el rojo.

---

## Task 7 — `GET /v2/visitas/{id}` en `msp-api`

**Fase 2.** Repo `msp-api`, **worktree aparte**. Sin dependencias.

El puerto `internal/visitas/ports/outbound/repo.go` **ya declara `FindByID`** y `visitasfb`
**ya lo implementa**. Falta la capa de aplicación, el handler y la ruta.

Calcar la forma de la ruta equivalente de cobranza (`GET /v2/cobranza/pagos/{id}`).

**Gates de `msp-api`:** `make lint`, `check-sealed`, cobertura por paquete, y el **test de
composición** que `TESTING_REQUIREMENTS.md:119` exige por ruta nueva.

**A decidir en esta tarea:** si `TipoVisita` deja de ser texto libre (máx 100 chars, sin catálogo,
`visita.go:15/202`) y pasa a catálogo cerrado en el servidor, o si el catálogo vive solo en la app
y el servidor sigue recibiendo string. Hoy la app manda 11 literales de `Constants.kt:31-41`.
Decidir y documentar la razón; la decisión ata a los Tasks 9 y 14.

---

## Task 8 — `GET /v2/visitas/by-ids?ids=…` en `msp-api`

**Fase 2.** Repo `msp-api`, mismo worktree. Depende del Task 7.

El canal que le falta al reconciliador de visitas. Con la forma de
`v2/cobranza/sync/pagos/by-ids` y **tope explícito de lote** — `COBRANZA-SYNC.md` ya documenta
que un `IN (...)` sin cota es una bomba.

El tope debe ser un valor nombrado, devolver un error claro al excederlo, y estar cubierto por
un test que pruebe **el borde exacto** (lote en el tope = OK, tope+1 = error).

**Fuera:** feed paginado tipo `/sync/visitas/zona/{z}`. El teléfono no baja visitas, solo confirma
las que subió; meter paginación traería el problema del cursor-par sin que nadie lo pida.

**Gates:** `make lint`, `check-sealed`, cobertura por paquete, test de composición por ruta nueva.

---

## Task 9 — Contrato multipart para imágenes de visita en `msp-api`

**Fase 2.** Repo `msp-api`, mismo worktree. Depende del Task 7.

`POST /v2/visitas` es JSON puro hoy. Calcar el contrato multipart de cobranza
(`dto_pago_recibido.go:107` declara `Imagen []huma.FormFile`, *"0..N comprobantes"*, con
whitelist de tipos) para que la visita acepte imágenes.

Conservar la **idempotencia end-to-end**: el `ID` del `CrearVisitaBody` es UUID del cliente y
clave de idempotencia, con `ErrVisitaYaExiste` en colisión. Reintentar debe seguir siendo seguro
por construcción **incluyendo** el caso con imágenes.

Respetar la whitelist de tipos y el límite de tamaño que ya usa cobranza — no inventar otros.

**Gates:** `make lint`, `check-sealed`, cobertura por paquete, test de composición por ruta nueva.

---

## Task 10 — Reconciliador de visitas en `:core:common/sync/pendingwork/`

**Fase 3.** Depende de los Tasks 7 y 8.

Visitas **no tiene reconciliador**; pagos tiene digest-first + rescate `by-ids`. Ese es el motivo
por el que visitas necesita un botón manual.

Construir el reconciliador de visitas en **`:core:common/sync/pendingwork/`** — junto a la
infraestructura compartida, **no en `app/`** — para que sobreviva a la migración de módulos.

Como el API de visitas no tiene digest, el reconciliador de visitas es más simple que el de pagos:
toma los IDs locales pendientes y pregunta por `by-ids` cuáles ya existen en el servidor,
marcando esas como sincronizadas.

**Auditar primero** el reconciliador de cobranza y reusar todo lo que sea genérico en vez de
duplicarlo. Respetar el tope de lote del Task 8 troceando la lista de IDs.

**Tests de robustez suprema:** lote vacío, lote justo en el tope, lote sobre el tope (trocea),
respuesta parcial, error de red, IDs que el servidor no conoce (siguen pendientes).

---

## Task 11 — Disparadores de sync de visitas: apertura, periódico y conectividad

**Fase 3.** Depende del Task 10.

Pagos tiene **6** disparadores; visitas ~1 y frágil. Copiar de cobranza los que faltan:

- **Reconciliador al abrir la app.**
- **Periódico** (cobranza usa 15 min — usar el mismo valor salvo razón documentada).
- **Cambio de conectividad** (`ConnectivityMonitor` ya es `@Singleton`).

Todo en `:core:common/sync/pendingwork/`, reusando la infraestructura de cobranza donde sea genérica.

**El botón NO se quita todavía** — se quita al final de la Fase 3, y solo tras verificación en campo.
Recordar que hoy una visita pendiente **bloquea "INICIALIZAR SEMANA"** (`AuthViewModel.kt:117-131`):
quitar el botón antes de que el reconciliador funcione deja al cobrador trabado.

**Tests:** cada disparador dispara; ninguno dispara dos veces por el mismo evento.

---

## Task 12 — SSE de visitas

**Fase 3.** Depende del Task 11.

**Corrección importante de la investigación:** msp **ya tiene push**.
`internal/cobranza/infra/cobranzahttp/handlers_sse.go:139` sirve SSE y el cliente ya lo consume
(`CobranzaSseProvider.kt`). No aparece al listar rutas Retrofit porque es un stream aparte con OkHttp.
**Extender SSE a visitas es mucho más barato que construir transporte.**

Extender el stream existente para que anuncie visitas confirmadas, y que el cliente lo consuma
para marcar sincronizadas sin esperar al poll.

Requiere tocar `msp-api` (worktree de la Fase 2) **y** la app. Si el diseño del stream de cobranza
no admite un segundo tipo de evento sin romper a los clientes viejos, **reportar la restricción y
proponer la forma compatible** antes de implementar.

**Tests:** en el servidor, que el evento se emite; en la app, que el consumidor lo procesa y que
un evento desconocido no rompe el stream (compatibilidad hacia adelante).

---

## Task 13 — Arreglar el colapso de la visita a una sola venta

**Fase 4.** Depende del Task 5.

`internal/visitas/domain/visita.go:46-61` indexa la visita por **`ClienteID`, no por venta** — que
es lo correcto, y encaja con la regla de que *no estaba* y *cita a una hora* se propagan a **todas**
las ventas del cliente.

Pero `VisitsLocalDataSource.kt:58-69` (`insertVisitAndUpdateState(saleId, …)`) **colapsa cada visita
a UNA venta** y no propaga, aunque el join `Sale.CLIENTE_ID` + `Visit.CLIENTE_ID` lo permite.

Arreglar la escritura local para que la visita sea del **cliente** y su efecto se propague a las
ventas que correspondan según el estado (§4 del expediente: *no estaba* y *cita* son de alcance
cliente; *vuelvo*, *se negó*, *prometió* son de alcance venta).

**Room v27 es inmutable** — esto se resuelve con lógica de escritura y queries, no con schema nuevo.

**Tests de robustez suprema:** cliente con 1 venta, con 2, con 3; visita de alcance cliente propaga
a todas; visita de alcance venta toca solo la suya; cliente sin ventas activas no revienta.

---

## Task 14 — El catálogo de ocho estados, derivado en la app

**Fase 4.** Depende del Task 13.

**Decidido: los estados se derivan en la app**, no en el servidor. El periodo de cobro ya vive en el
teléfono (`startWeekDate` + Firestore `FECHA_CARGA_INICIAL`), así que calcular localmente no necesita
plumbing nuevo, y el estado de una visita recién capturada se ve **al instante**, sin esperar sync.
Cero API nueva para estados.

Los ocho estados (color + ícono + texto — **nunca solo color**):

| Estado | Significado | Alcance |
|---|---|---|
| **Pagó** | Cobró completo lo de la semana. No hace falta volver | venta |
| **Abonó (parcial)** · *por confirmar* | Dio algo pero no lo esperado | venta |
| **Visité — vuelvo** | Pasaste, no se resolvió. Regresas este periodo | venta |
| **Prometió: próxima** | Da hasta la siguiente semana. Rojo = no cae este periodo | venta |
| **Se negó / problema** | Rojo **sólido** = escalar, atención especial | venta |
| **Cita a una hora** | Quedaron de verse. Muestra la hora | **cliente → sus ventas** |
| **No estaba** | Fuiste y no había quién atendiera | **cliente → sus ventas** |
| **Sin tocar** | Nadie trabajó esta cuenta este periodo. Base | venta |

Incluye:

- **Mapeo desde `EstadoCobranza`** (5 valores: `VOLVER_VISITAR`, `PENDIENTE`, `VISITADO`,
  `NO_PAGADO`, `PAGADO`, en `core/database/.../sale/EstadoCobranza.kt:11-17`) para no romper el
  histórico ni el orden de la lista.
- **Separar *Pagó* de *Abonó parcial*** comparando la suma de pagos de la ventana contra
  `Sale.PARCIALIDAD` (`Sale.kt:23`, servida por `VentaDto.kt:49/102`). Hoy
  `PaymentsViewModel.kt:464` fija `PAGADO` en **cada** pago guardado, **sin comparar el monto**.
  Usar `internal/rutas/domain/cobranza.go:12-50` y `aporte.go` (que ya calculan `AbonoSemana`
  contra `Parcialidad`) como **referencia de fórmula** para no divergir del servidor.
- **Agrupar las diez etiquetas actuales** de `visitConditionForm` bajo el estado que les toca, para
  que el cobrador no reaprenda nada y el histórico siga comparable:
  - **No estaba** ← no se encontraba · casa cerrada con candado · solo había menores
  - **Visité — vuelvo** ← se asomó pero no salió · no responde aunque está · se escuchan ruidos
  - **Se negó** ← no pagará esta ocasión · tiene dinero pero no quiso · fue grosero o agresivo
  - **Prometió** ← pidió reagendar visita
- **Propagación por `ClienteID`** para *no estaba* y *cita*, apoyada en el Task 13.
- Los dos estados que necesitan **dato nuevo** (*prometió: próxima* con fecha y monto, *cita a una
  hora* con hora) **no se pueden derivar todavía** — llegan con la captura de visita del Task 19.
  Esta tarea deja el catálogo listo para recibirlos, no los inventa desde texto libre.

**Reglas de presentación:** 1 venta → cuadro grande en el encabezado (donde iba el avatar).
2+ → mini-cluster arriba + badge "N cuentas" + cuadro en cada renglón. "Volver a pasar" tiene 3
variantes: hoy con hora (reloj) · otro día con hora (reloj + día) · otro día sin hora (calendario).

**A decidir en esta tarea:** qué hacer con *se negó / problema*, que hoy comparte bucket con
*vuelvo* en `VisitStatusMapper.kt:23-27` (`FUE_GROSERO` cae junto a `PIDE_TIEMPO`) — si se
reclasifica desde el texto libre existente o si solo aplica hacia adelante. Decidir y documentar.

**Tests de robustez suprema:** los ocho estados, el mapeo desde los 5 viejos, el borde exacto de
*Pagó* vs *Abonó parcial* (suma == `PARCIALIDAD`, suma == `PARCIALIDAD` - 1, suma > `PARCIALIDAD`),
la propagación por cliente, y el caso "sin tocar" al abrir semana nueva.

---

## Task 15 — Módulos `:feature:pagos` y `:feature:visitas`

**Fase 5.** Depende de los Tasks 12 y 14.

Crear los dos módulos moldeados en `:feature:collectionReport`: convention plugins (`msp.hilt`,
`msp.detekt`), estructura, cableado en `settings.gradle.kts` y en `prePushCheck` (ktlint + detekt + test).

**Módulos nuevos = detekt estricto sin baseline.**

Esta tarea crea el andamio vacío y verde. Las pantallas llegan en los Tasks 16-20.

**Verificación:** `prePushCheck --rerun-tasks` verde con los dos módulos nuevos cableados.

---

## Task 16 — Pantallas de detalle: cliente y venta

**Fase 5.** Depende del Task 15. Mocks listos:
`claude.ai/code/artifact/c741ddde-ec64-4d1e-a9d0-810346667653` (las dos, completas, claro/oscuro),
más el revestido del Task 2.

Decisiones de diseño ya tomadas:

- **El nombre del cliente es el título** del detalle.
- **El detalle abre por cliente**, con sus ventas dentro — un cliente puede estar vencido en una
  venta y al corriente en otra, y eso se pierde si todo se agrega.
- **Casi todo el dato vive en la venta**: saldo, parcialidad, frecuencia, productos, fecha,
  enganche, contado, vendedor, progreso, pagos, garantía, liquidación.
  Del **cliente**: nombre, teléfono, dirección, aval, zona, ficha, historial de visitas, saldo total.
- **"Hoy liquida con"** existe en ambos niveles y **no es redundancia**: en el cliente cierra la
  conversación completa, en la venta cierra ese mueble.
- **Historial de pagos = ritmo + riel.** Arriba las 12 semanas (a tiempo / tarde / sin pago) con el
  resumen; abajo el detalle. **La agrupación por mes es visible sin colapsar:** el mes interrumpe el
  riel con un nodo cuadrado, su nombre y el subtotal.
- **Condonación va al "⋯"** — es rara y es dinero. **No se toca su lógica.**
- El estado de las ventas se pinta con el catálogo del Task 14.

La captura pasa de `FullScreenDialog` a **destino de navegación con su propio ViewModel** y
`SavedStateHandle` — es lo que hace segura la foto (la cámara saca de la app), no cosmética.

**Verificación:** Roborazzi en claro **y** oscuro, goldens revisados como imagen, y que aguante
`FontSizeLevel`.

---

## Task 17 — Lista de clientes

**Fase 5.** Depende del Task 16.
Referencia visual: `1562ea75` (lista de clientes de kollect), revestida con `MspColors`.

**El problema estructural:** la lista de cobranza itera con `key = { it.DOCTO_CC_ID }` y pinta
`SaleItem`. **Es por venta, no por cliente** — un cliente con dos ventas aparece dos veces, como si
fueran dos personas. El cobrador toca una puerta, no una venta.

**Dos lugares** muestran la lista de cobranza con lógicas distintas: Home (cercanía por centroides)
y `SalesScreen` (3 pestañas). **La lista nueva reemplaza a las dos.**

Construir: una sola lista **por cliente con sus ventas dentro** · **chips de segmento**
(*Todos · Vencidos · Hoy · Sin visitar*) en vez de pestañas · búsqueda sobre el cliente (el dato ya
se concatena hoy: nombre, folio, calle, ciudad, teléfono) · **cercanía relegada** a *"estoy aquí,
muéstrame su venta"*, no como orden principal (hoy optimiza gasolina, no cobranza).

**El orden se copia del que ya existe**, `SalesScreen.kt:201-203`:

```kotlin
compareByDescending<SaleWithProducts> { it.SALDO_REST == it.PRECIO_TOTAL - it.ENGANCHE }
    .thenBy { it.FECHA }
```

1. **Primero los que no han abonado nada** — `SALDO_REST == PRECIO_TOTAL - ENGANCHE` significa que
   el saldo sigue siendo exactamente lo financiado, o sea cero abonos. `compareByDescending` sobre
   Boolean pone `true` primero.
2. **Luego por fecha de venta ascendente** — las más viejas primero.

Tres detalles a **conservar o decidir explícitamente** al portarlo:

- Hoy el orden **solo aplica a la pestaña "POR VISITAR"**; `VISITADOS` y `PAGADOS` van sin orden
  explícito. Con chips en vez de pestañas hay que decidir a qué segmentos aplica.
- El orden **se desactiva al buscar** (`if (query.isBlank())`) — la búsqueda respeta el orden natural
  de la fuente.
- El filtro usa `EstadoCobranza` (5 valores). **Hay que mapearlo al catálogo de ocho del Task 14**,
  o el orden y los segmentos dejan de coincidir.

Orden por cliente: un cliente hereda la posición de su venta mejor rankeada — decidirlo y documentarlo.

**A decidir en esta tarea:** si Home conserva su lista o queda solo con el resumen semanal, ahora
que la lista de clientes es su propia pantalla. **Home y la lista se quedan como pantallas separadas**
(ya decidido); lo abierto es solo si Home sigue duplicando la lista.

**Verificación:** Roborazzi claro/oscuro, `FontSizeLevel`, y un test del orden que cubra el borde de
"cero abonos" (`SALDO_REST` exactamente igual a `PRECIO_TOTAL - ENGANCHE`, y uno peso abajo).

---

## Task 18 — Registrar abono

**Fase 5.** Depende del Task 16. Mock listo con sus 4 estados:
`claude.ai/code/artifact/6b772462-71c5-4be0-8b2a-b4a9dc602653` (captura, bloqueo, confirmar, monto raro).

**El pago sigue soberano.** Tiene 3 capas anti-duplicado y 7 defectos de producción detrás. Si algo
de visitas se acopla al pago, un bug de visitas puede tocar dinero. **No acoplar.**

Se conserva **íntegro** el diseño de seguridad de `1dd8752b`:

- **Bloqueo duro por sobrepago** — no se puede guardar un monto mayor al saldo.
- **Confirmación en dos pasos** mostrando *saldo anterior → saldo nuevo*.
- **Alerta de monto raro** con botón rojo que obliga a afirmar.

Más:

- **Montos sugeridos con color propio:** verde *esperado hoy*, turquesa *al corriente*,
  violeta *liquidar* — revestidos con los tokens del Task 2.
- **Métodos de pago: efectivo y transferencia.** Sin Terminal.
- Destino de navegación con su propio ViewModel y `SavedStateHandle`.

**Tests de robustez suprema:** el borde exacto del sobrepago (saldo, saldo+1), los dos pasos de
confirmación, la alerta de monto raro, y que ninguna ruta guarde dos veces.

---

## Task 19 — Registrar visita

**Fase 5.** Depende del Task 14 y del Task 16. Mock listo con 3 estados:
`claude.ai/code/artifact/836239c8-0c79-4e3d-9a1e-8f000bec9d6a` (elegir, prometió, no estaba).

**Aquí nacen los dos datos que hoy no existen** y que desbloquean dos octavos del semáforo:

1. **Promesa estructurada** — venta + **fecha** + **monto**. Hoy es texto libre con una fecha, y por
   eso el cumplimiento **no se puede medir**. Con estructura, *Prometió: próxima* deja de ser
   inderivable (§13 del expediente).
2. **Cita con hora** — campo estructurado. Hoy la hora solo existe embebida en el texto libre de
   `NOTA` (`NewVisitDialog.kt:119-125`), sin campo ni en `visita.go` ni en `VisitEntity`.

Más:

- Las **diez etiquetas actuales** se conservan, agrupadas bajo su estado (ver Task 14).
- **Persistencia: la da el Task 26.** El orquestador dictaminó en pre-flight que estos campos
  necesitan columnas reales — serializarlos en un texto libre reproduciría exactamente el defecto que
  §13 del expediente denuncia (la promesa estructurada existe **para poder consultarse**; es el insumo
  del BTTC, y un texto libre no se consulta). El Task 26 define **una** migración aditiva v27→v28 con
  todo lo que este plan necesita. **Esta tarea NO define migraciones propias** — consume el schema del
  Task 26. Si falta un campo, reportarlo; no agregarlo por cuenta propia.
- Destino de navegación con su propio ViewModel y `SavedStateHandle`.

**Dos cosas que cuestan casi nada ahora y son imposibles de reconstruir después** (§10 del
expediente — el recomendador de visitas):

- **Guardar la recomendación**, no solo el resultado. Sin *qué sugirió el sistema* junto a *qué hizo
  el cobrador*, nunca se puede evaluar el recomendador.
- **Dejar espacio para un grupo de control.** Sin algo de aleatorización solo se aprende correlación,
  nunca causalidad. **No hace falta implementarlo ya**; sí que el modelo lo permita.

**Tests de robustez suprema:** promesa con fecha pasada, promesa sin monto, cita sin hora, cada
etiqueta cayendo en su estado, y que la visita quede encolada aunque falle la ubicación (Task 5).

---

## Task 20 — Ticket de pago y ticket de visita

**Fase 5.** Depende de los Tasks 18 y 19.

A diseñar en esta tarea, inspirados en los tickets que ya existen y llevados al lenguaje visual de
`:feature:collectionReport`. El usuario **ya autorizó decidirlos durante la ejecución**.

Incluir la **regla de negocio del mock**: *el ticket solo se imprime el día del cobro*, y **cada
impresión queda registrada** para detectar reimpresiones.

Reusar `:core:printing` (hexagonal + DantSu ESC/POS + picker Bluetooth + última-impresora-usada) que
ya se adoptó para el reporte de cobranza. Dinero en **pesos enteros** en todo el stack nuevo.

**Verificación:** la impresión térmica **no se puede probar en emulador** — dejarlo marcado como
release-gate manual con impresora real. Lo que sí se prueba automatizado: la regla del día del cobro
y el registro de reimpresiones.

---

## Task 21 — Navegación

**Fase 5.** Depende de los Tasks 16-20.

**Decidido: depende del origen.**

- Desde la **lista** y el **mapa** se entra al **cliente** (ahí el contexto es la persona).
- Desde un **pago** o un **recibo existente** se entra directo a **su venta** (ahí el contexto ya es
  esa venta).

Puntos de entrada a revisar y cablear: `SaleItem`, `PaymentItem`, `PaymentCard`, `RouteMapScreen`,
`SaleActionsSection`.

Al terminar esta tarea, `SalesScreen` y la lista de Home quedan reemplazadas por la lista del Task 17.
**`UnifiedSalesScreen` no se toca** — es la lista de ventas locales, otro trabajo.

**Verificación:** cada punto de entrada llega al destino correcto; ninguna ruta huérfana.

---

## Task 22 — Fotos en pagos

**Fase 6.** Depende del Task 18.

**El contrato ya existe de los dos lados:**

- `V2PaymentsApi.crearPago` **ya es `@Multipart`** y manda `@Part("datos")`.
- El servidor **ya acepta imágenes**: `dto_pago_recibido.go:107` declara `Imagen []huma.FormFile`,
  *"0..N comprobantes"*, con whitelist de tipos.
- La app **ya captura fotos** por intent (`NewSaleFormViewModel.createCameraUri()` /
  `processCameraImage()`), tiene `ImageCompressor` en `core/utils`, y arma multipart con
  `getImageParts()` en ventas y garantías.

Falta: **UI para adjuntar**, **persistencia** y **pasar las partes al worker**.
La persistencia la da el **Task 26** (migración aditiva v27→v28). Esta tarea **no define migraciones
propias** — consume ese schema.

Reusar `ImageCompressor` y el patrón de `LocalSaleSyncHandler` (`multipartRequest` + `getImageParts()`).
**No portar la cola durable de kollect** — msp manda la imagen dentro del mismo request que la
mutación: atómico, sin staging ni objetos huérfanos.

**La foto nunca bloquea el guardado.** Si la cámara falla, el pago se guarda igual.

**Tests:** guardar sin foto, con una, con varias; cámara que falla; tipo no permitido.

---

## Task 23 — Fotos en visitas

**Fase 6.** Depende de los Tasks 9, 19 y 22.

Lo mismo que el Task 22, sobre el multipart de visita del Task 9.

Nota: `V2VisitsApi.kt:11-12` dice explícitamente *"visitas never carry a comprobante image"* —
agregarlas es **decisión de producto ya tomada**. Actualizar ese comentario, no dejarlo mintiendo.

**La foto nunca bloquea el guardado.**

**Tests:** los mismos bordes del Task 22, más la idempotencia con imágenes (reintentar la misma
visita con foto no crea una segunda).

---

## Task 24 — Ficha del cliente: catálogo cerrado + nota libre

**Fase 7.** Depende de los Tasks 16 y 26.

**RULING DEL ORQUESTADOR (pre-flight):** el plan la declara "independiente, en paralelo desde el
Task 15", pero la ficha **se muestra dentro del detalle de cliente**, que es lo que construye el
Task 16. En paralelo, o duplica la pantalla o entrega un modelo sin dónde vivir. Va después del 16.

Conocimiento **persistente** del cliente, distinto del resultado de una visita: *"trabaja de noche"*,
*"atiende la suegra"*, *"casa azul, portón negro"*. Es lo que hace mejor la **próxima** visita y lo
que hereda un cobrador nuevo.

**Dos campos con trabajos distintos:**

- **Catálogo cerrado** para lo que la máquina va a usar — horario en que se le encuentra, quién
  atiende, señales de riesgo. Es **lo único que puede alimentar el BTTC**, porque el texto libre no
  se consulta.
- **Nota libre** para lo que no anticipamos, que es lo que lee el humano que llega mañana.

**Molde:** `SignalType` + `SignalFamily` + `SignalLabels` de kollect, donde agregar un valor
**no compila** hasta que se le pone etiqueta en español — un guardrail barato que vale la pena copiar.

**El catálogo nace corto y crece con evidencia.**

**A decidir en esta tarea:** qué valores entran al catálogo inicial. Candidatos del campo: horario en
que se le encuentra, quién atiende, referencias de la casa, advertencias. Decidir y documentar por qué
esos y no más.

**Persistencia: la da el Task 26** (migración aditiva v27→v28). Esta tarea **no define migraciones
propias** — consume ese schema.

**Tests:** el guardrail de etiqueta faltante (que no compile / que el test lo detecte), y la
persistencia de ambos campos.

---

## Task 25 — Verificación final

**Fase 8.** Depende de todo.

```bash
./gradlew test detekt ktlintCheck
./gradlew prePushCheck --rerun-tasks     # ~3m40s; sin esto no prueba nada
```

Más, a mano:

- **Tema claro** en las cuatro pantallas nuevas (todo lo diseñado está en oscuro), y que aguanten
  `FontSizeLevel`, que es configurable.
- **Roborazzi**: regrabar goldens y **revisar las imágenes**, no solo el verde.
- Verificar que **el botón de sync manual se puede quitar** — y quitarlo solo si el reconciliador
  quedó verificado. Recordar el bloqueo de "INICIALIZAR SEMANA" (`AuthViewModel.kt:117-131`).

**Release-gates manuales (no automatizables — documentar, no intentar):**

- **En dispositivo:** capturar una visita **sin señal**, matar la app, reabrir y confirmar que
  **sube sola** — sin tocar ningún botón. Es el criterio de que la Fase 3 funcionó.
- **Impresión térmica** con impresora real: no se puede probar en emulador.

**Salida:** un reporte de conformidad que liste, tarea por tarea, qué quedó verde automatizado y qué
quedó como gate manual pendiente del usuario.


---

## Task 26 — Migración aditiva de Room v27 → v28

**Insertada por el orquestador en el pre-flight (Ruling 2).** Se ejecuta **después del Task 15 y antes
del Task 19**. Depende del Task 15.

### Por qué existe esta tarea

Las Tasks 19, 22, 23 y 24 necesitan **datos que hoy no existen** en el schema:

| Task | Dato nuevo | Por qué no puede ser texto libre |
|---|---|---|
| 19 | **Promesa estructurada**: venta + fecha + monto | §13 del expediente: *Prometió: próxima* es inderivable hoy **precisamente porque** es texto libre. La promesa existe para poder **consultarse** — es el insumo del BTTC. Serializarla en `NOTA` reproduce el defecto que el plan vino a arreglar |
| 19 | **Cita con hora** | Hoy la hora vive embebida en el texto de `NOTA` (`NewVisitDialog.kt:119-125`). Mismo argumento: sin campo, *Cita a una hora* sigue siendo inderivable |
| 19 | **La recomendación mostrada** (§10) | "Guardar la recomendación, no solo el resultado" — sin esto el recomendador nunca se puede evaluar, y es **imposible de reconstruir después** |
| 22 | Referencia(s) de imagen del pago | Hoy no hay dónde guardarlas entre el guardado y la subida |
| 23 | Referencia(s) de imagen de la visita | Igual |
| 24 | **Catálogo cerrado** de la ficha + **nota libre** | El catálogo es lo único que puede alimentar el BTTC porque el texto libre no se consulta (§5) |

### La regla, con precisión

La constraint del repo dice **Room v27 es INMUTABLE**, y su razón declarada —en la constraint misma y
en `DISPATCH-CONVENTIONS.md`— es que **reescribir** el schema **corrompería datos en teléfonos reales**.
Esta tarea **no reescribe nada**. Es estrictamente aditiva:

- **PERMITIDO:** columnas nuevas **nullable** (o con `DEFAULT`) sobre tablas existentes; **tablas nuevas**.
- **PROHIBIDO, sin excepción:** alterar el tipo, el nombre, la nulabilidad o el default de una columna
  existente · borrar una columna o una tabla · cambiar una llave primaria · cambiar un índice existente
  de forma no aditiva · cualquier `DROP` o `ALTER COLUMN`.
- Un objeto `Migration(27, 28)` **real y escrito a mano**, no `fallbackToDestructiveMigration()`
  (que borraría los datos — es exactamente el desastre que la constraint previene).
- El JSON del schema v28 se **exporta y se commitea**, igual que v27.

### Qué entrega

1. El diseño del schema v28 — **una sola** versión que cubre **toda** la persistencia nueva del plan
   (las seis filas de la tabla de arriba). Ninguna otra tarea define migraciones.
2. El objeto `Migration(27, 28)` escrito a mano.
3. El JSON de schema exportado y commiteado.
4. **En el reporte: el DDL exacto**, sentencia por sentencia. El usuario lo va a leer.

### Diseño — decisiones a tomar en esta tarea

- **Promesa y cita:** ¿columnas nuevas en la tabla de visitas, o tabla nueva? Decidir con la
  cardinalidad enfrente (¿una visita puede llevar más de una promesa?) y documentar la razón.
- **Imágenes:** ¿columna con una referencia, o tabla hija que admita 0..N? El contrato del servidor
  dice **"0..N comprobantes"** (`dto_pago_recibido.go:107`) — que el schema no lo contradiga.
- **Ficha del cliente:** catálogo cerrado y nota libre son **dos campos con trabajos distintos** (§5) —
  que el schema lo refleje, no los junte.
- **Recomendación mostrada:** dejar espacio para un **grupo de control** (§10). No hace falta
  implementar la aleatorización; sí que el modelo la permita después sin otra migración.

### Verificación

- **Test de migración de Room** que parte de una base **v27 con datos reales representativos**,
  corre la migración y comprueba que **ningún dato preexistente se perdió ni cambió** — y que las
  columnas nuevas quedan en su default esperado. Este test es el corazón de la tarea.
- Un test que se ponga **ROJA** si se revierte la migración, verificado de verdad.
- `prePushCheck --rerun-tasks` verde.
- **Control positivo:** demostrar que el test de migración SÍ falla si se le mete un cambio destructivo
  (p. ej. borrar una columna en la migración) — si no falla, el test no está probando nada.

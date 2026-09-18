# Principios de cobranza 2026 — lo que no puede perderse con el worktree

> Los 28 principios del rediseño de cliente, visita y dictado; las decisiones que ya
> se cerraron y qué las reabre; y los cuatro defectos que esta sesión encontró, con su
> lección. Rama `feat/pagos-y-visitas`. Fecha: 2026-09-17.
>
> Fuentes: `.superpowers/sdd/2026-09-01-pagos-y-visitas/brief-rediseno-2026.md` (2026-09-15),
> `.superpowers/sdd/2026-09-01-pagos-y-visitas/global-constraints.md`, `CLAUDE.md` de la raíz,
> y los mensajes de los commits `0a0d1ca6`, `aef68824`, `cbd12444`, `c59bd728`.

## 0. Por qué este archivo está en `docs/` y no en `.superpowers/`

`.gitignore:32` ignora `.superpowers/` entero, y `git ls-files .superpowers` no devuelve
nada: **todo el razonamiento de esta sesión vive en un directorio que git no guarda**. El
brief, los reportes de tarea, los controles de reversión pegados y los 28 principios se
borran junto con el worktree, y lo que quedaría sería el código sin el porqué.

`docs/superpowers/plans/` sí está versionado. Aquí van **los principios y las decisiones**.
**No** van los datos de negocio: este repositorio es **público** (`CLAUDE.md` §5, a propósito
de las credenciales en texto plano de `CollectionReportDeviceSmokeTest.kt`). La frontera está
escrita en §5 de este documento.

---

## 1. Los 28 principios

No se relitigan. Cada uno salió de un error real, no de una preferencia.

### Diseño (1–14)

1. **Medir, nunca opinar.** Todo número sale de un golden o del código, con `archivo:línea`.
2. **La forma dice la verdad.** Un cliente = una tarjeta. Un segmentado = solo uno activo. Si
   la forma sugiere algo que el comportamiento no hace, la forma está mal.
3. **El aire antes de un separador es mayor que el aire entre dos líneas del mismo bloque.**
4. **Una tarjeta, un destino.** Nada de un `clickable` dentro de otro.
5. **No repetir un dato.** Repetir no jerarquiza: delata que la pantalla no sabe qué importa.
6. **El dato que ya existe se pinta antes de inventar uno nuevo.**
7. **Vocabulario del cobrador, no del programador.** `CLIENTE`/`VENTA` → "Toda la puerta"/"Una
   cuenta". Un folio como `V-5021` → el nombre del producto. Nadie sabe qué es un folio parado
   en una puerta.
8. **El color del estado no es el color de la selección.** La selección va en `brand`. El rojo
   es `statusOverdue`: usarlo para "elegiste esto" convierte una captura en una alarma.
9. **Un número a medias es un dato FALSO, no incompleto.** Antes de truncar, apilar.
10. **Mayúscula inicial** en todo texto de usuario. Español, sin punto final, 2–4 palabras.
    **Nunca digas "ciclo"** en la UI — se dice "semana". El nombre interno sí puede ser ciclo.
11. **Toques ≥ 50 dp** — regla del repo, más estricta que los 48 de Material. **No se baja
    nunca:** si un test la cobra, sube la implementación, no bajes el test.
12. **Nada salta.** Una animación que empuja el contenido de abajo se siente barata.
13. **Respetar movimiento reducido** (`LocalReduceMotion`) en toda animación nueva.
14. **El aviso ámbar, nunca rojo, cuando no se perdió trabajo.** El precedente vive en
    `feature/visitas/src/main/kotlin/com/example/msp_app/feature/visitas/ui/components/PiezasDelComprobanteDeVisita.kt`
    — hoy es `FALLO_FOTO_TAG`, línea 63; el rango `60-65` que cita el brief ya se corrió con
    el archivo, así que se nombra la constante y no el número.

### Arquitectura (15–20)

15. **Contrato hexagonal, sin excepciones:** `domain/` puro (cero imports de `android.*`, Room
    o Retrofit) · `application/` casos de uso · `data/adapter/` la única capa con Room,
    Retrofit o Android · `ui/` importa `domain` y `application`, **jamás** `data/adapter` ·
    `di/` el único que ve las dos caras.
16. **Un puerto se justifica con UNA de tres** (Ruling BF): ≥2 implementaciones, cruza módulo,
    o **el contrato de capas lo exige** — el consumidor vive en una capa que no puede importar
    la capa de la implementación. Uno que no cumple ninguna es un defecto, igual que el
    triple-map ritual entre DTO, entidad y modelo.
17. **Cuando el adaptador necesita algo que solo vive en `:app`**, el puerto se queda en el
    módulo y el adaptador (con su `@Module`) se va a `:app`. Precedentes: `TemaDeLaAppPort`,
    `LiquidacionPort`, `UserCyclePort`, `AuthTokenProvider`.
18. **Módulos nuevos siguen el patrón `:core:printing`**: `domain/` (con el puerto),
    `application/`, `adapters/`, `di/`. Operaciones falibles devuelven `Result<T>`.
19. **Dispatchers:** no existe `DispatcherProvider` y no se crea. `CoroutineDispatcher` por
    constructor o `@Qualifier` de Hilt cuando cruza módulo. Tests: `StandardTestDispatcher` +
    `advanceUntilIdle()`, **nunca** `UnconfinedTestDispatcher`.
20. **Norma de errores (vinculante en `:core:*` y `:feature:*`; `:app` es legacy y no se
    retrofitea):** ningún `Throwable` capturado se traga. Cada camino de captura emite
    `Telemetry.error(code, message, props)` con un `code` constante, único y grepeable.
    **Anti-PII (LFPDPPP):** nada de nombres, teléfonos, direcciones ni montos exactos en
    `code`/`message`/`props`; se usa el nombre de la clase de excepción, nunca `e.message`
    crudo. Cada camino de error nuevo lleva su test con `RecordingTelemetry` afirmando el
    `code` — si se borra la línea de telemetría, un test se pone rojo.

    El caso que esta norma existe para prevenir está en `CLAUDE.md` §1: un `IN (...)` sin
    trocear lanza *"too many SQL variables"*, lo atrapa el `try/catch` externo y se vuelve
    error silencioso en cada tick. Nadie lo vio hasta que costó horas.

### Verificación (21–28)

21. **Control de reversión con el rojo PEGADO.** Por cada cambio de comportamiento: revierte el
    arreglo, corre, pega el rojo en el reporte, restaura, y **verifica que no quedaron restos**.
    Sin eso, "está probado" es una afirmación sin evidencia. Ya ocurrió que un marcador de
    mutación se quedara puesto y el reporte dijera "verde".
22. **Control positivo.** Una ausencia no es un hallazgo hasta probar que la consulta habría
    encontrado la cosa. Antes de afirmar "no existe", prueba que el método SÍ lo habría hallado.
23. **Lo que un golden no puede probar, va en test de geometría.** En Robolectric los insets
    valen cero: áreas tocables y barras del sistema se miden en
    `app/src/test/java/com/example/msp_app/navigation/CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest.kt`,
    que barre **todo destino del grafo** y **todo control tocable** (≥50 dp).
24. **Prohibido subir umbrales de golden.** `RoborazziConfig.CHANGE_THRESHOLD = 0.01f`
    (`core/testing/src/main/kotlin/com/example/msp_app/core/testing/roborazzi/RoborazziConfig.kt:13`).
    Se regraban con `./gradlew :<módulo>:recordRoborazziDebug` y **se MIRAN como imagen**, en
    claro y oscuro y en las tres escalas (`FontSizeLevel` NORMAL 1.0 / GRANDE 1.5 /
    MUY_GRANDE 2.0 — **no** el 1.3 crudo de `CollectionReportMatrixScreenshotTest`; esa
    discrepancia se reporta, no se replica). El porqué de mirarlos está en §4.
25. **Los goldens ajenos no se mueven.** Si se movieron, el cambio se salió de alcance. Ni uno
    de `:core:designsystem` ni de `:feature:collectionReport`. Reporta **cuáles** se mueven y
    por qué.
26. **Nunca un script de regex sobre archivos no listados.** Un script de ordenación de imports
    tocó 26 archivos ajenos una vez (revertidos uno por uno, commit `d285b3cc`). Lista los
    archivos y edítalos uno por uno.
27. **Fakes-only, escritos a mano. NUNCA MockK ni Mockito.** Estado público + lista pública que
    graba las llamadas. Room in-memory (`RoomTestBase`) o transacciones con rollback. Turbine
    para `Flow`.
28. **Tests flaky conocidos, ajenos a este trabajo** (`ProductDetailsViewModelTest`,
    `AdjustedPaymentPercentageReactiveTest`, `ElToggleDeLaListaCambiaElTemaDeLaAppTest`): pasan
    aislados. Antes de culpar a tu cambio, corre el test solo.

### Commits

```
<type>(pagos-y-visitas): <subject en español, imperativo, minúscula>
```

Cuerpo: **por qué**, no qué. Menciona el control de reversión y su resultado. **Sin**
`Co-Authored-By`, **sin** atribución de agente. Nunca `--no-verify`, nunca push.

### La compuerta

`./gradlew prePushCheck --rerun-tasks` sobre la variante `devlocalDebug`. **Con el árbol
caliente `prePushCheck` sale en segundos y no prueba nada** — correrla con `--rerun-tasks` al
menos una vez antes de afirmar que algo pasa. Ninguna compuerta compila ni corre `androidTest`:
las tareas `connected*` están excluidas.

---

## 2. Decisiones cerradas, y qué las reabre

Una decisión sin disparador de reapertura no es una decisión, es una opinión que envejece.

### 2.1 Whisper no se vendora

**Estado:** el dictado corre sobre `SpeechRecognizer` de Android, que también es en el
dispositivo y por lo tanto también funciona sin señal. `MotorWhisperNativo` existe, intenta
cargar `libwhisper_msp.so` y se declara ausente: hoy no hay `externalNativeBuild`, ni CMake, ni
fuentes de whisper.cpp en el repo, y eso fue una decisión. Meter el toolchain nativo haría que
la compuerta dependa del NDK sin que ninguna compuerta pueda ejecutar el resultado, y son
~200 mil líneas de C++ de terceros en un repo público.

La descarga del modelo (`ggml-tiny-q8_0.bin`, **43 537 433 B ≈ 43.5 MB**, medido con un `HEAD`
sobre Hugging Face — commits `177cc56b` y `9a482cd5`) **no se ofrece** mientras el motor no
exista: ofrecer una descarga que nada puede cargar no es una función a medias, es una mentira
que le cuesta datos al cobrador. Y no se apaga con una bandera: se pregunta
`ModeloDeDictadoPort.motorDisponible`, para que el renglón aparezca solo el día que el `.so`
viaje en el APK, sin que nadie tenga que acordarse de voltear una constante.

**Disparador de reapertura:** que un teléfono de la flota llegue **sin español instalado** en
su reconocedor. Ahí whisper deja de ser peso y pasa a ser la única forma de dictar.

**Cómo se mide:** `app/src/androidTest/java/com/example/msp_app/e2e/QueSabeDictarEsteTelefonoTest.kt`
(301 líneas). No es compuerta —mide el aparato que tiene enfrente, así que su resultado es
distinto en cada teléfono, y ninguna tarea `connected*` entra al gate—; es la medición
reproducible que contesta con un hecho lo que se estaba contestando con un folleto: si hay
reconocedor, si lo hay **en el dispositivo**, y qué idiomas tiene **instalados** frente a los
que dice soportar.

### 2.2 MapLibre: fuera

**Estado:** `:core:mapas` se borró entero en `c59bd728`. Medido antes y después con el mismo
comando, sobre `devlocalDebug`:

| | Antes | Después |
|---|---|---|
| APK | **76.42 MB** | **27.90 MB** |
| Nativo (`.so`) | **47 981 KB** | **59 KB** |

Son **−48.53 MB, el −63.5 %**. Los 47.9 MB se repartían en cuatro ABIs (12.7 arm64-v8a,
9.2 armeabi-v7a, 26.0 de x86/x86_64 que ningún teléfono de cobrador ejecuta) y **R8 no toca los
`.so`**, así que viajaban enteros también a release — y esta app se reparte por **descarga
directa**, no por Play Store: el dueño baja el APK completo cada vez.

Peor que el tamaño era el reparto: el renderizador viajaba **siempre**, aunque nadie bajara las
teselas, y el extracto ni siquiera está publicado en un servidor. El peso obligatorio compraba
exactamente cero función. Un mapa dentro de la app tampoco navega; la app de mapas del teléfono
sí, y ya se abría por el intent que `IntentAccionesExternasAdapter` arma desde antes de esta rama.

Se fue el módulo entero y no solo la dependencia: sin renderizador, el lector de PMTiles, la
descarga con `Range`, el worker, la pantalla de descarga y su DI se quedaban sin un solo
consumidor. El historial lo conserva en `40852f4d`.

Los `abiFilters` **no se ponen**, y ahora está medido en vez de supuesto: lo que queda de nativo
son **58.9 KB**, de los cuales x86 + x86_64 son **30.7 KB**. Filtrar ABIs ahorraría treinta
kilobytes a cambio de una variante más que mantener y de romper el emulador de escritorio.

**Lo que NO se borró**, porque es lo que hace útil a "cómo llegar": `DetalleCliente.ultimoCobroAqui`
y todo el camino que lo alimenta (`Payment.LAT/LNG` → `UbicacionDelCobro` → `DestinoEnElMapa`).
Con él, el `geo:` abre la app de mapas en la coordenada donde de verdad se cobró y no en una
dirección de texto geocodificada; en una colonia sin numeración esa es la diferencia entre
llegar a la puerta y llegar a la calle.

**Disparador de reapertura:** que aparezca un caso que exija **renderizar teselas DENTRO de la
app**. Querer ver un mapa no lo es: el intent ya lo cubre.

### 2.3 El mapa entra por un slot, no por dentro

Mientras `:core:mapas` existió, el Composable del bloque de mapa recibía **el suelo por
parámetro** (el slot `lienzo` de `SueloDeLaRuta`, `40852f4d`): los goldens fotografiaban todo
menos el motor. MapLibre renderiza con GL y Robolectric no lo tiene, así que sin esa costura el
golden habría dependido del renderizador — y un golden que depende de la red o de la GPU es una
moneda al aire que un día se pone roja sola.

La pieza ya no existe en esa forma, pero **la regla sobrevive al módulo**: cualquier superficie
que dependa de red, GPU o caché entra a la UI por un slot que el test llena con un doble
determinista.

**2026-09-17 — el slot se volvió a usar, y ahora con un mapa de verdad.** El dueño pidió que el
hueco llevara "un mapa o un dibujo". La vía se decidió midiendo, no opinando:

- `play-services-maps` y `maps-compose` **ya viajan** en el APK que se reparte:
  296 rutas de clase distintas bajo `com/google/android/gms/maps/` sobreviven a R8 en
  `app-prod-release.apk` (11.59 MB), y sus AAR no traen **ni un `.so`**. El total de nativo del
  APK son 58.9 KB, todos de `libandroidx.graphics.path` y `libdatastore_shared_counter`.
  Costo incremental en bytes: **cero**.
- Es el modelo de distribución **opuesto** al de MapLibre, que traía su renderizador adentro
  (§2.2). "Mapas" no es una categoría con un peso: el peso depende de dónde vive el motor.
- Se usa en `liteMode`: bitmap estático, sin gestos y sin zoom. Es el modo que Android
  documenta para un mapita dentro de un detalle. **El zoom vive en otra pantalla**
  (`UbicacionDelClienteScreen`, en `:app`), y el cuadro la abre al tocarlo.
- **Eso NO reabre los dos caminos que `5417e65e` cerró.** "Cómo llegar" sale de la app a
  navegar por un `geo:`; el cuadro abre el mapa DENTRO de la app para mirar la puerta antes de
  arrancar. Son dos trabajos y dos destinos, no dos caminos al mismo intent. El `geo:` lo
  sigue armando un solo lugar, `IntentAccionesExternasAdapter`, por el mismo puerto.
- **El mapa arranca invisible y solo se deja ver cuando el SDK avisa que pintó** (`onMapLoaded`,
  con `alpha` y no con un `if`, para que llegue a componerse y poder avisar). Debajo está
  siempre el dibujo. No es precaución: instalado en el SM-A256E el mapa **no pintó ni una
  tesela** —`Authorization failure … StatusCode=INVALID_ARGUMENT`, la llave no autorizaba el
  paquete de esa build— y lo que se veía era la retícula gris con el logo de Google, o sea *un
  mapa que no cargó*. El mismo estado se da en la calle sin señal. Con el dibujo debajo, el
  peor caso es el estado aceptable y no el prohibido.
- **La atribución de Google no se tapa.** La hoja del mapa grande cae justo donde el SDK dibuja
  su logo, así que el mapa recibe `contentPadding` con el alto de la hoja. Taparla viola los
  términos del SDK.
- **Un pago o una visita concretos abren su propio punto**, no el del último cobro del cliente:
  la fila de la bitácora se toca **si y solo si** ese contacto trae coordenada. La regla de qué
  par no es un lugar tiene un solo dueño, `UbicacionDelCobro.medida`, y descarta el **par** en
  cero —no el cero suelto: una latitud `0.0` con longitud real es el ecuador y es un lugar.
- **Costo por uso:** las cargas de mapa del SDK nativo de Android van a 0 USD en el tarifario
  publicado de Google Maps Platform, al revés de la Static Maps API por HTTP y de la Geocoding
  API REST que la app ya consume. El tarifario lo fija Google: conviene confirmarlo en la
  consola antes de dar el número por bueno.

Lo que **no** cambió es la costura: `:feature:pagos` no declara ninguna de esas dependencias.
Deja la ranura con un default que funciona —el dibujo— y quien la cierra con el mapa real es
`:app`, que es donde viven la dependencia y la llave. Principio 17, el mismo reparto de
`TemaDeLaAppPort` y `LiquidacionPort`. Por eso los goldens del módulo siguen fotografiando algo
que no depende de la red.

Y el respaldo **se lee como ilustración**: sin retícula, sin calles rotadas y sin pin. Las tres
cosas las pedía el mock original (`docs/design/mocks/cliente-y-venta.html:151-156`) y las tres
son dato falso dibujado — un pin en el centro dice "es aquí" sobre una puerta que nadie midió.

---

## 3. Los cuatro defectos de esta sesión, y su lección

Esto es lo que de verdad no debe perderse: no el arreglo, sino cómo se escondió.

### 3.1 `cuentaQueEncabeza` — el abono entraba a la primera cuenta sin decirlo

`cuentaQueEncabeza` devolvía `ventas.firstOrNull()`. Con dos cuentas abiertas, el dinero podía
caer en la equivocada y **nadie se enteraba hasta que cuadraban**. Estaba documentado como
decisión —"la fila de arriba de sus ventas"— pero el cobrador nunca eligió esa fila: la eligió
un `sortedWith` del caso de uso.

No se arregla ordenando mejor: se arregla **preguntando**, y preguntando solo cuando hay algo que
preguntar. Con UNA cuenta cobrable el flujo es idéntico al de siempre y no cuesta ni un toque;
con dos o más abre una hoja de selección única —el dinero entra completo a una cuenta, así que
es un radio y no casillas— con la de atrasos preseleccionada.

`CuentaQueEncabezaTest` **se retiró en vez de ajustarse**: estaba bien escrito y afirmaba lo
equivocado, o sea que blindaba el defecto.

> **La lección:** un KDoc que llama "decisión" a un comportamiento no lo convierte en decisión.
> Si nadie eligió, es un defecto. Y un test que protege un defecto se retira, no se corrige.

*(Commit `0a0d1ca6`. Control de reversión: volver `singleOrNull` a `firstOrNull`, o el
`maxByOrNull` de atrasos a la primera de la lista, pone **seis** pruebas en rojo.)*

### 3.2 `getByClientId` sin unir `overdue_payments_view` — atrasos siempre cero

`SaleDao.getAll` une `overdue_payments_view` y `getByClientId` **no lo hacía**, así que
`NUM_PAGOS_ATRASADOS` caía al default `null` y `RoomVentasAdapter` contestaba `atrasos = 0`
para toda venta leída por cliente.

No era cosmético. `CuentaDelAbono.preseleccionada` marca la cuenta con más atrasos; con todas en
cero, `maxByOrNull` devuelve **la primera de la lista** — exactamente el defecto que la hoja del
abono acababa de cerrar en §3.1. La hoja seguía preguntando, que es lo que impide que el dinero
entre mudo a la cuenta equivocada, pero la cuenta que venía marcada era otra vez la de arriba.

**Por qué no se vio:** las pruebas de la hoja **siembran `atrasos` en el fixture**. Probaban la
FUNCIÓN y no la TUBERÍA: el fixture entregaba precisamente lo que el adaptador no podía traer.
El test nuevo va sobre Room de verdad y compara las dos lecturas — leer por cliente y leer la
ruta completa no pueden dar atrasos distintos.

Detalle que se paga una vez: las tres columnas que la vista comparte con `sales` (`DOCTO_CC_ID`,
`NUM_IMPORTES`, `FECHA_ULT_PAGO`) van calificadas, o Room rechaza la consulta por ambigüedad
**en tiempo de compilación**, que es donde se quiere ese error.

> **La lección:** es el principio 22 cobrándose solo. Un fixture que siembra el dato es un
> control positivo fallido: prueba que la función sabe sumar, no que el dato llega. Cuando el
> valor que se afirma lo puso el propio test, no se probó la tubería.

*(Commit `aef68824`. Control de reversión: quitar el `LEFT JOIN overdue_payments_view` de
`getByClientId` pone en rojo `los atrasos llegan tambien leyendo por cliente, no solo por la
ruta`. Se regrabaron y miraron los **24** goldens de `:feature:pagos`; ninguno ajeno.)*

### 3.3 `es-MX` fijo — el dictado no arrancaba en el único teléfono que importa

Medido en vidrio, en el **SM-A256E (Android 15, API 35)**, que es el aparato para el que se
construyó la app:

```
es-MX  →  NO arranca — error 12 (ERROR_LANGUAGE_NOT_SUPPORTED)
es-US  →  SÍ arranca (escuchando)
```

`SpeechRecognizerDeAndroid` fijaba `EXTRA_LANGUAGE = "es-MX"`. Ese teléfono trae `[es-US]`
instalado y `es-MX` no está **ni entre los 30 idiomas que dice soportar**. La función entera
estaba muerta, y ninguna prueba podía verlo: la compuerta no corre `androidTest` y en la JVM no
hay reconocedor.

El KDoc tenía razón en no usar el idioma del sistema —un teléfono en inglés no cambia el idioma
en que la gente habla en la puerta—; lo que estaba mal no era preferir español, era **asumir
cuál español**. `IdiomaDelDictado` (dominio puro) elige entre los que el motor reporta
**instalados**, y devuelve la etiqueta **tal como el motor la reportó**: si el aparato dice
`es_US`, se le pide `es_US`. "Normalizarla" sería repetir el mismo defecto con otro disfraz.

**Y el primer diagnóstico no probaba nada.** `checkRecognitionSupport` contesta **lo mismo** para
`es-MX` que para `es-US` —también medido—, así que preguntarle no distingue. Lo único que
distingue es **abrir la sesión**. Por eso el adaptador reintenta en vez de preguntar antes: ante
un fallo de idioma le pregunta al motor qué tiene instalado, cachea el que sirve y vuelve a
arrancar; el rodeo se paga una vez por proceso y el error llega antes de que nadie hable.

> **La lección:** control positivo sobre el **método de diagnóstico**, no solo sobre el hallazgo.
> Una API que contesta igual en los dos casos no es evidencia de nada, por más que su nombre
> prometa lo contrario. Y el filtro de español lleva su propio control positivo, porque un
> `startsWith("es")` crudo diría que el estonio es español.

*(Commit `cbd12444`. Control de reversión: devolver `PREFERIDO` a secas pone en rojo
`el telefono del dueno trae es-US y es lo que hay que pedirle`.)*

### 3.4 MapLibre — el 63 % del APK por una función opcional

Los números y el razonamiento están en §2.2. Lo que importa como defecto, y no como decisión de
producto, es la **forma**: un renderizador **obligatorio** montado sobre una función **opcional**,
que además no podía ejercerse porque el extracto no estaba publicado en ningún lado.

> **La lección:** el costo de una función se mide en el artefacto que se reparte, no en el
> diagrama. Una dependencia opcional que viaja en el binario obligatorio dejó de ser opcional el
> día que se enlazó. El principio 1 aplica también al tamaño: medir el APK antes y después, con
> el mismo comando.

---

## 4. La regla de los goldens: mirarlos no es trámite

`RoborazziConfig.CHANGE_THRESHOLD = 0.01f`
(`core/testing/src/main/kotlin/com/example/msp_app/core/testing/roborazzi/RoborazziConfig.kt:13`).
**No se sube nunca.** Se regraba con:

```
./gradlew :<módulo>:recordRoborazziDebug     # p. ej. :feature:pagos:recordRoborazziDebug
```

…y se **miran como imagen**, en claro y oscuro, en las tres escalas (1.0 / 1.5 / 2.0).

El brief de 2026-09-15 decía "tres defectos". Al cierre de la sesión son **dieciséis defectos que
ningún assert vio**, contados por el commit en cuyo cuerpo están descritos:

| Commit | Cuántos | Qué destapó la imagen |
|---|---|---|
| `b4641031` | 3 | un resumen sin derivar, una pastilla flotando, el botón del mapa encimado a 2.0 |
| `4678a4d2` | 1 | la pastilla de atrasos cortada a "Al" |
| `fec135f6` | 2 | la etiqueta de alcance saliendo "UNA CUENT", el atajo "todas/ninguna" invisible |
| `177cc56b` | 4 | el glifo del botón encendido decía "grabar" mientras ya grababa; en oscuro el control quedaba invisible; a 2.0 "Descargando" se partía y los megas se encimaban; el micrófono flotaba 18 dp bajo el primer renglón |
| `40852f4d` | 2 | el pin 21 dp arriba del centro de la cámara (~24 m a zoom 17), el botón "cómo llegar" tapándolo a 2.0 |
| `2b31ae3c` | 1 | el título de la sección reducido a una columna de tres letras a 2.0 |
| `aef68824` | 2 | la pastilla "último cobro aquí" recortada ya a 1.0, el pin debajo del botón a 1.0 y 1.5 |
| `5417e65e` | 1 | "whatsapp" partido a mitad de palabra a 1.5 y 2.0 |
| | **16** | |

Ninguno tenía un assert que lo cazara, y **ninguno era cosmético**: una letra comida en silencio
(principio 9), un control invisible, un dato con pinta de exacto que estaba 24 metros fuera. Dos
patrones se repiten tanto que valen como reglas propias:

- **La escala 2.0 es donde se rompe casi todo.** Un texto que cabe a 1.0 no prueba nada.
- **Una caja de alto fijo con contenido que escala es un encimamiento esperando.** Aparece dos
  veces en la tabla, en dos módulos distintos.

Y el corolario de disciplina: **los goldens ajenos no se mueven** (principio 25). Si se movieron
uno de `:core:designsystem` o de `:feature:collectionReport`, el cambio se salió de alcance.

---

## 5. Dónde quedó lo que NO se versiona

`.superpowers/sdd/2026-09-01-pagos-y-visitas/huella-geografica-medida.md` **se queda en
`.superpowers/`**, fuera de git, y así debe seguir: contiene conteos de clientes por población,
nombres de localidades de la ruta y las coordenadas exactas de la región donde se cobra. **Este
repositorio es público** (`CLAUDE.md` §5). Un mapa de dónde vive la cartera no se publica.

Se regenera en un rato, y por eso no hace falta guardarlo: es un **histograma por cuadros de
medio grado** sobre `MSP_VISITAS.LAT/LNG` en la base Firebird de producción —`GROUP BY` sobre
`FLOOR(LAT*2), FLOOR(LNG*2)`, ordenado por conteo—, más el cruce de `DIRS_CLIENTES` con
`ESTADOS` para el desglose por entidad. El SQL exacto está en ese archivo.

Dos cosas de ese documento sí son método y por eso se anotan aquí, sin los datos:

- **Se mide sobre las visitas, no sobre las direcciones.** Las coordenadas de visita las tomó el
  teléfono parado en la puerta; una dirección de texto habría que geocodificarla, y eso mete el
  error del geocodificador dentro de la medición. (Las coordenadas de la tabla de pagos no
  sirven: casi ninguna fila las trae pobladas.)
- **Por histograma y no por `MIN`/`MAX`.** Un puñado de filas de una corrida de pruebas con la
  ubicación por defecto del emulador habría estirado la caja a otro continente. El histograma es
  lo que permite ver que son un puñado y recortarlas a conciencia; dos agregados crudos no lo
  habrían dicho.

El extracto de teselas generado a partir de esa caja vive **fuera del repo**, en el disco del
dueño, por la misma razón. Hoy no lo consume nada: el módulo que lo leía se borró (§2.2).

# La fila de contactos — plan de ejecución

> Rama `feat/pagos-y-visitas`. Mock aprobado por el dueño el 2026-09-20:
> `docs/design/mocks/fila-de-contactos.html`. Cuatro decisiones ya cerradas por él —
> no se relitigan:
>
> 1. La etiqueta del abono dice **"Abono"** (no "Cobré", no "Pago").
> 2. La fila lleva **día y hora**, en ese orden, en la columna de la izquierda.
> 3. El filtro lleva **conteos** en cada opción.
> 4. La cuenta se identifica **siempre con el nombre del producto** (nunca el folio).
>
> Y una que queda fuera: el nombre del cobrador (`"RUTA 25 - NOE CORTERO"`) **se deja
> como está**. Dicho por el dueño: "en el 3 por ahora déjalo así".

## Contexto

Las dos superficies que se tocan son la misma fila en dos lugares:

- La tarjeta **"Últimos contactos"** del detalle de cliente (3 filas) —
  `DetalleClienteScreen.kt`.
- La pantalla dedicada **"Ver los N contactos"** — `BitacoraScreen.kt`.

Ambas pintan `ContactoEnLinea` (`ui/components/LineaDeContactos.kt`) sobre
`ContactoDeCobranza` (`domain/model/DetalleCliente.kt:224`), que arma
`BitacoraDelCliente.de(visitas, pagos)`.

## Global Constraints (aplican a TODAS las tareas)

- **Principio 10, que es el origen de la tarea 1:** mayúscula inicial en todo texto de
  usuario; español; sin punto final; 2–4 palabras. Se mide sobre la primera **letra**,
  no el primer carácter.
- **Nada de listas a mano.** Si una compuerta necesita saber el alcance, lo descubre.
  Es la lección de `d752c9a0`/`68590a24` en esta misma rama.
- **Los goldens se regraban y se MIRAN**, no basta el verde. Roborazzi corre en
  `:feature:pagos` (94 goldens) y `:feature:visitas` (44).
- **Tres escalas de letra**: `NORMAL`, `GRANDE` (1.5), `MUY_GRANDE` (2.0). Toda medida
  que alinee contra una línea de texto escala con la letra o se rompe a 2.0 — es el
  defecto que esta tarea viene a cerrar.
- **La compuerta es `./gradlew prePushCheck --rerun-tasks`** (~4 min). Sin eso no se
  prueba nada.
- Un commit por tarea, mensaje en español, sin atribución de Claude.

---

## Task 1 — Las etiquetas de usuario en minúscula

**Qué está mal, medido:** `MetodoDeCobro` nace con sus etiquetas en minúscula
(`domain/model/HistorialDePagos.kt:207-209`): `EFECTIVO("efectivo")`,
`CHEQUE("cheque")`, `TRANSFERENCIA("transferencia")`. Las leen cuatro superficies:
`LineaDeContactos.kt:179`, `HojaDeConfirmacion.kt:339`, `PiezasDelAbono.kt:208` y
`:460`, y `RitmoYRiel.kt:376`.

Y el mismo defecto en la visita: `BitacoraDelCliente.kt:48` hace
`etiqueta = visita.tipoVisita.lowercase()`, así que la bitácora dice
*"no responde aunque está"* con minúscula inicial.

**Qué entrega:**

- `EFECTIVO("Efectivo")`, `CHEQUE("Cheque")`, `TRANSFERENCIA("Transferencia")`.
- La etiqueta de la visita con mayúscula inicial. **No** con `.lowercase()` seguido de
  un `replaceFirstChar`: el literal de `tipoVisita` viene del catálogo cerrado
  (`TipoVisitaCatalogo`), así que la normalización va donde el catálogo pueda
  defenderla, no repartida en la mezcla.
- Una compuerta que **descubre** las etiquetas de usuario del módulo y exige mayúscula
  inicial, para que el barrido del 18-sep no se vuelva a quedar corto. Si ya existe la
  compuerta de ese barrido, se extiende en vez de duplicarse.
- Goldens regrabados.

**Verificación:** rojo sembrado en la compuerta nueva (una etiqueta en minúscula la pone
en rojo) antes de que pase.

---

## Task 2 — La cuenta llega al contacto: el nombre del producto

**Por qué:** el dueño vio en su teléfono dos cobros al mismo minuto, $400 y $100, y
pensó que era un error. **No lo es, y está verificado contra la base del teléfono:**
el cliente tiene dos ventas abiertas y se le cobran las dos seguidas.

```
ACR 12845224 → folio Y00001786 → "RECAMARA CANTARO KING SIZE CHOCOLATE" (+ base de cama) → $400
ACR 14431255 → folio Y00002103 → "BOCINA PROFESIONAL 8'' AUDIOBAHN"                       → $100
```

Lo que falla es que la fila no dice a cuál cuenta se abonó.

**Qué entrega:** `ContactoDeCobranza` gana la cuenta —el **nombre del producto**— y el
adaptador la resuelve. El camino de datos que existe hoy en Room:
`Payment.DOCTO_CC_ACR_ID` → `sales.DOCTO_CC_ACR_ID` → `sales.FOLIO` → `products.FOLIO`
(varias filas; `products.POSICION` las ordena).

**Decisiones que toma esta tarea, y tiene que dejarlas escritas:**

- **Qué producto nombra una cuenta de varios artículos.** Y00001786 trae recámara y
  base de cama. Decisión del dueño: el nombre del producto, siempre. Elegir cuál —
  el primero por `POSICION`, el de mayor precio— es de esta tarea, y se justifica.
- **Qué pasa cuando no hay producto** (una cuenta sin renglones en `products`, que en
  un teléfono recién sincronizado es posible). `null` y la fila no pinta cuenta;
  **nunca** un texto de relleno como "Sin producto" ni el folio de repuesto.
- **El nombre viene de Microsip en mayúsculas.** Se normaliza para la UI, y la regla
  queda en un solo lugar.
- **Una visita no lleva cuenta**, igual que no lleva método.

**Verificación:** el par que prueba el join —dos pagos del mismo cliente a cuentas
distintas caen con nombres distintos— y el caso sin producto.

---

## Task 3 — La fila se acomoda sobre una sola línea base

**Qué está mal, medido:** `ContactoEnLinea` no tiene rejilla. La hora se empuja con
`padding(top = sm + xs)`, el punto de estado con `padding(top = md × escala)` y el pin
con `padding(top = sm)` — tres números cocinados por separado que persiguen a mano la
primera línea del título (`LineaDeContactos.kt:137-201`). Cuando el título crece o se va
a dos renglones, los tres se despegan.

**Qué entrega** (la forma está en el mock, sección 03 y 04):

- Cuatro pistas: **Cuándo · Estado · Qué pasó · Cuánto**.
- **Cuándo**: el día (`18 feb`, en negrita) y debajo la hora (`14:30`), alineados a la
  derecha, con `tabular-nums`. Hoy sólo hay hora, y dentro de un grupo mensual eso no
  contesta cuándo fue.
- **Estado, importe y pin alineados contra la primera línea del título**, no contra el
  borde de arriba de la fila. Una sola regla, no tres paddings.
- **Qué pasó**: título —`"Abono"` para un cobro; para una visita sigue siendo su
  etiqueta— y debajo `cuenta · método` para el cobro.
- El ancho de la columna del día escala con la letra, como ya lo hace
  `anchoDeLaHora()`.
- La marca de "esto es de la venta abierta" (`deEstaVenta`) se conserva.
- Goldens regrabados **y mirados**, en las tres escalas y en los dos temas.

**La clase de prueba que falta y que esta tarea sí puede escribir:** que los cuatro
elementos compartan línea base a `NORMAL`, a `GRANDE` y a `MUY_GRANDE`, y con el título
en dos renglones. Un golden no lo demuestra: lo demuestra una medición de posición.

---

## Task 4 — El filtro deja de ser cuatro pastillas

**Qué está mal:** `FiltroDeContactos` se pinta como cuatro pastillas grandes
(`BitacoraScreen.kt`), mientras la lista de clientes ya filtra con **un solo control
segmentado** (`ListaDeClientesScreen.kt` / `PiezasDeLaLista.kt`, commit `368bd2e2`). La
misma app filtra de dos maneras.

**Qué entrega:**

- El filtro de contactos adopta el control segmentado de la lista. Si el control se
  puede compartir sin deformarlo, se comparte; si compartirlo obliga a parametrizar de
  más, se dice por qué y se queda en su módulo.
- **Conteos** en cada opción, derivados de los contactos cargados — nunca de una
  consulta aparte que pueda discrepar de lo que la lista enseña.
- El toque sigue siendo de 50 dp o más, con su compuerta (la que ya existe en la rama).

**Cuidado con lo que ya se midió en esta rama:** el control va en una pantalla que
consume el inset de la barra de estado. No re-introducir el defecto de `aa5281c2`.

---

## Task 5 — Verificación final

```bash
./gradlew prePushCheck --rerun-tasks
```

Más, a mano:

- **Mirar** los goldens que se movieron, no sólo el verde: los dos temas y las tres
  escalas.
- Un recorrido en el emulador: detalle de cliente → "Ver los N contactos" → filtrar por
  cada una de las cuatro opciones.

**Salida:** un reporte que diga, tarea por tarea, qué quedó verde automatizado y qué
quedó como gate manual en el teléfono del dueño.

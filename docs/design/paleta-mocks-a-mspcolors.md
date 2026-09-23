# Paleta: mocks (esmeralda) → `MspColors`

**Task 2 del plan `pagos-y-visitas`. Entrega única (Ruling C): esta tabla, nada más.**
Los cuatro mocks HTML en `docs/design/mocks/` se quedan pintados en esmeralda `#34D399` como
registro de lo acordado — **no se editan, no se republican**. Task 16-20 lee el mock para
composición, escala y jerarquía, y lee esta tabla para color.

Fuente de los hex del mock: los cuatro archivos locales `docs/design/mocks/*.html` (depositados
por el orquestador antes de la Ruling C — copia sin tocar de los artifacts de claude.ai, ver
`docs/design/mocks/README.md` para los links). Se citan directo de ahí (`grep` verificable), no
hizo falta releer los artifacts por red.

Fuente de los tokens: `core/designsystem/src/main/kotlin/com/example/msp_app/core/designsystem/theme/MspColors.kt`
(`mspLightColors()` / `mspDarkColors()`, líneas 73-147). Cada token citado abajo tiene su `grep`
en el reporte (`task-2-report.md`).

**Regla dura (no se relitiga):** azul de marca `brand` (`#2563EB` claro / `#3B82F6` oscuro) para
botón primario y todo lo protagónico/tocable. Verde `statusPaid` (`#177245` claro / `#40CB84`
oscuro) solo para estado "pagado" — nunca para acción. El `--ac` esmeralda del mock se reparte
entre los dos según el uso; ver §3.

---

## 1. Superficies

| Var. mock | Hex mock | Token `MspColors` | Light | Dark | Uso |
|---|---|---|---|---|---|
| `--bg` | `#080A09` | `background` | `#F4F6F5` | `#000000` | fondo de pantalla |
| `--surf` | `#141A18` | `surface` | `#FFFFFF` | `#141917` | tarjeta / superficie elevada 1 |
| `--surf2` | `#1B2220` | `surface2` | `#FBFCFC` | `#1C2320` | superficie elevada 2 (chips, fondos internos) |
| `--line` | `#242D2A` | `outline` | `#E4E8E6` | `#28322C` | bordes, separadores, `border-top` |

Mapeo directo, sin ambigüedad — los cuatro mocks usan estas cuatro variables con el mismo valor.

## 2. Texto (tres niveles)

| Var. mock | Hex mock | Token `MspColors` | Light | Dark | Uso |
|---|---|---|---|---|---|
| `--ink` | `#F4F7F6` | `onSurface` | `#141A18` | `#E9EFEC` | texto principal |
| `--mut` | `#8E9995` | `onSurfaceMuted` | `#5C6863` | `#8B968F` | texto secundario (subtítulos, metadatos) |
| `--faint` | `#5D6864` | **`onSurfaceFaint` — PROPUESTA, no existe hoy** | `#7C857F` | `#6B746F` | texto terciario (labels de 9-11px, timestamps, "vigente hasta", pie de tarjeta) |

**Propuesta `onSurfaceFaint`** (§7 tiene el detalle completo y el contraste medido). `MspColors`
solo trae dos niveles de texto (`onSurface`/`onSurfaceMuted`); el mock usa consistentemente un
tercer nivel, más apagado, en las cuatro pantallas (`.faint`, `.k`, `.mt`, `.od`, `.fk`, etc. —
decenas de usos). No hay token existente que lo cubra: `onSurfaceMuted` es el candidato más
cercano pero ya está ocupado con su propio contraste objetivo (AAA-normal 7:1) y reemplazarlo
aplanaría la jerarquía de tres niveles que el mock sí tiene.

---

## 3. Acción vs. estado "pagado" — el reparto del `--ac` esmeralda

El mock usa `--ac` (`#34D399`, con las variantes `--acDeep` `#0F2A22` fondo y `--acInk` `#6EE7B7`
texto-sobre-acDeep) para dos cosas que en la app real **no pueden compartir color**:

### (a) Acción — botón primario y todo lo protagónico/tocable → `brand`

| Token | Light | Dark |
|---|---|---|
| `brand` (relleno) | `#2563EB` | `#3B82F6` |
| `onBrand` (contenido) | `#FFFFFF` | `#FFFFFF` |
| `brandTint` (contenedor de chip seleccionado, sin estado asociado) | `#EAF0FE` | `#0E2440` |

Ejemplos en el mock (todos pintados hoy en `--ac`, deben ir azules):

- `.btn.pri` / `.cta.p` — el botón/pill primario de las cuatro pantallas ("Guardar visita",
  "Cobrar", CTA de venta). Inequívoco: es literalmente "botón primario".
- `.liq .use` ("Usar") — botón secundario para aplicar el monto de liquidación sugerido. Tocable,
  protagónico dentro de su tarjeta. Azul, no verde — **este es el caso donde más fácil es
  equivocarse**: usa `acDeep`/`acInk` en el mock (estilo "chip suave"), pero es un botón real.
- `.kick`, `.cap b` — texto de marca/eyebrow ("msp · cobranza en campo", "**Venta** · estado...").
  Decorativo, no es estado de cobro; se agrupa con lo protagónico porque es identidad de marca,
  no información de negocio.
- `.m.on` (método de cobro seleccionado, "Efectivo") en `registrar-abono.html` y `.opt.sel` en
  `historial-de-pagos.html` (selector de layout, sin significado de estado) — selección genérica
  sin estado asociado → `brandTint` de fondo, `brand` de borde/texto. Ver nota de selección abajo.

⚠️ **Par bajo AA parqueado** (`NIGHT-REPORT.md` §B.2): `onBrand` sobre `brand` en dark = **3.68:1**
— no llega a AA-normal (4.5:1), sí al piso UI-component (3:1). Aparece en cualquier fila de esta
tabla que use `brand`/`onBrand`. **No se resuelve aquí** — decisión pendiente del usuario.

### (b) Estado "pagado" → `statusPaid`

| Token | Light | Dark |
|---|---|---|
| `statusPaid` (contenido) | `#177245` | `#40CB84` |
| `statusPaidTint` (fondo/tint) | `#E4F1E9` | `#0F2A1C` |

Ejemplos en el mock (mismo `--ac`, pero es información de negocio "esto está pagado / al
corriente / confirmado", nunca algo que se toca):

- `.st.pago` (ícono de estado semáforo — el primero de los 5, ver §4).
- `.sale.s-pago::before`, `.s-pago .ssub` — filas de venta marcadas "pago".
- `.ct .t .p.g` — punto de la bitácora de contactos para "Cobré" (el punto base sin clase es
  rojo = no contestó; `.p.v` violeta = "Prometió"; `.p.g` verde = "Cobré").
- `.sync` — "Saldo al 1 sep 9:41 · actualizado". Coincide 1:1 con el precedente real
  `SyncBandState.Ok → colors.statusPaid` (`core/designsystem/.../component/SyncBandState.kt:23`).
- `.capa .v` / `.sbig .v` — el monto grande que se está capturando/confirmando (verde por
  defecto, rojo en `.capa.err`/`.sbig.warn`). Es el estado "captura válida", análogo a pagado.
- `.flowb .new .fv` — el saldo nuevo tras aplicar el abono (mejora = verde).
- `.two .st.done` — paso completado en el indicador "1 Revisado · 2 Confirmar".
- `.weeks span` (segmento "a tiempo") y `.ti::before` en `historial-de-pagos.html` — confirmado
  por la leyenda explícita del propio mock: `<i style="background:var(--ac)"></i>a tiempo`
  (línea 108).
- `.av` (avatar) en el sheet de confirmación de `registrar-abono.html`: por defecto `acDeep`/
  `acInk` (captura sana); cambia a `redDeep`/`red` en la variante de anomalía (línea 240) — el
  avatar **sí** lleva estado, no es decorativo.

### (c) Caso aparte, más preciso que "pagado" genérico — barra de progreso de abonos

`.prog i` (`background:var(--ac)`) sobre `.prog` (`background:var(--surf2)`) es la barra "9 de 20
abonos" — un progreso continuo, no un estado discreto. `MspColors` ya trae un par dedicado a esto,
más preciso que reusar `statusPaid`:

| Token | Light | Dark |
|---|---|---|
| `heroProgressFill` | `#6FE3C2` | `#6FE3C2` (mismo valor en los dos temas) |
| `progressTrack` | `#E2E8E5` | `#28322C` |

### Selección genérica vs. selección con estado — la regla para chips

El mock **no** usa un color fijo para "esto está seleccionado". En `registrar-visita.html`, una
vez elegido un resultado, el anillo de selección toma el color **del propio estado elegido**:
`.opt.sel-prom` → `var(--red)`, `.opt.sel-nadie` → `var(--mut)`, `.opt.sel` (default, "vuelvo") →
`var(--amb)` (líneas 60-62); los chips de sub-detalle dentro de esos folds heredan el mismo color
de su padre (`.ch.on` rojo dentro del fold "Prometió", `.ch.onm` mut dentro del fold "No estaba").
`.ch.ong` (verde) está definido en el CSS pero **no se usa** en ningún elemento del body —
verificado por `grep -c 'class="ch ong"' docs/design/mocks/registrar-visita.html` → `0`.

**La regla para Task 16-20:** si el chip/opción seleccionado representa uno de los cinco estados
del semáforo, usa el par contenido/tint de **ese estado** (§4) — no azul. Si la selección es
genérica y no tiene un estado de cobro asociado (método de pago, opción de layout), usa
`brand`/`brandTint` como cualquier control de selección.

---

## 4. Los cinco colores de estado del semáforo (`TipoVisita`)

Cada estado en el mock sigue el mismo patrón visual: ícono en `--<color>` sobre fondo
`--<color>Deep`. Eso mapea limpio al patrón existente en `MspColors` — el contenido va en el
token base, el fondo en su `*Tint` — **excepto que el nombre del token no siempre coincide con
el nombre del estado del mock.** Ver notas por fila.

| # | Estado (mock) | Var. mock (icon / fondo) | Hex mock | Token contenido | Token fondo | Light | Dark | Nota |
|---|---|---|---|---|---|---|---|---|
| 1 | Pagado | `--ac` / `--acDeep` | `#34D399` / `#0F2A22` | `statusPaid` | `statusPaidTint` | `#177245` / `#E4F1E9` | `#40CB84` / `#0F2A1C` | mismo par que §3(b) |
| 2 | Parcial | `--teal` / `--tealDeep` | `#3BC9DB` / `#0D2830` | `statusTeal` | `statusTealTint` | `#0E7C8A` / `#DFF0F2` | `#33B6C9` / `#0B2A30` | ⚠️ **trampa de nombre:** NO es `statusPartial` — ese token es ámbar (fila 3). `statusTeal` no tiene consumidores reales hoy (solo tests); esta es su primera aplicación real. ⚠️ **par bajo AA parqueado** (`NIGHT-REPORT.md` §B.2): `statusTeal` sobre su tint en light = 4.19:1, bajo AA-normal (4.5:1) |
| 3 | Vuelvo | `--amb` / `--ambDeep` | `#E3AC4E` / `#2A2210` | `statusPartial` | `statusPartialTint` | `#B26A00` / `#FBEEDC` | `#E3AC4E` / `#2C220F` | dark hace match exacto de hex con el mock. ⚠️ **trampa de nombre inversa a la de arriba:** `statusPartial` está nombrado por "parcial" pero el color que trae es el ámbar de "vuelvo". ⚠️ **par bajo AA parqueado**: `statusPartial` sobre su tint en light = 3.71:1 |
| 4 | Promesa / negado | `--red` / `--redDeep` | `#F0736A` / `#2A1613` | `statusOverdue` | `statusOverdueTint` | `#B42318` / `#FBE7E4` | `#F26A5C` / `#2E1613` | ver desglose abajo — promesa y negado usan el mismo par pero distinto arreglo |
| 5 | Cita | `--vio` / `--vioDeep` | `#A78BFA` / `#1E1A33` | `promise` | `promiseTint` | `#7A5AF8` / `#EEEAFD` | `#AD9BFB` / `#1E1A33` | ⚠️ **trampa de nombre grande** — ver nota debajo de la tabla |

**Fila 4, desglose promesa vs. negado** (mismo hue, dos tratamientos distintos en el mock):

- **Promesa** (`.st.prom`) — estilo "outline": fondo `statusOverdueTint`, ícono/texto
  `statusOverdue`. Igual al resto de los chips de estado.
- **Negado** (`.st.nego`) — estilo "relleno sólido invertido": `.st.negado{background:var(--red)}
  svg{stroke:#1A0D0C}` (fondo rojo plano, ícono casi negro fijo, no una variable). Esto tiene
  precedente exacto en el código real: `PrimaryFieldButtonVariant.Danger` (`fillColor = colors.
  statusOverdue`, `contentColor = colors.onDanger` —
  `core/designsystem/.../component/PrimaryFieldButtonVariant.kt:26,33`). Se usa ese mismo par:
  fondo `statusOverdue` sólido, contenido `onDanger` (`#FFFFFF` claro / `#210A07` oscuro) — el
  `onDanger` oscuro (`#210A07`) es casi idéntico al `#1A0D0C` fijo del mock.
  `danger`/`dangerTint`/`onDanger` **también existen** en `MspColors` (líneas 54-56, 96-98,
  138-140) pero no se usan aquí — se prefirió el precedente ya cableado (`statusOverdue` +
  `onDanger`) sobre inventar un segundo par rojo.

**Fila 5, la trampa de nombre:** `MspColors.promise` **ya está cableado en la app** a "Promesa de
pago" — `ChipStatus.Promise → colors.promise` (`core/designsystem/.../component/ChipStatus.kt:26-27,40,49`,
comentario: "Promesa de pago — violeta + reloj"). Ese es un concepto **distinto** al "cita" (visita
agendada) de este mock. Se reutiliza aquí solo por coincidencia de matiz — violeta — reforzada por
el propio mock: en `cliente-y-venta.html`, el punto `.ct .t .p.v` de la bitácora de contactos
("Prometió el 15 · $350") **sí** es una promesa de pago real y también es violeta. Es decir:
`promise`/`promiseTint` tiene dos usos legítimos y coincidentes en este dominio (promesa de pago
real, y cita agendada) — no hay pérdida de significado, pero un implementador que use
`ChipStatus.Promise` para pintar el chip de "cita" de una visita debe saber que el nombre del
enum no describe lo que está pintando.

### Estados neutros adicionales (fuera de los cinco oficiales, presentes en los mocks)

| Estado (mock) | Var. mock | Hex mock | Token contenido | Token fondo |
|---|---|---|---|---|
| Nadie / sin dato (`.st.nadie`, `.st.sin`) | `--mut`/`--faint` sobre `--surf2`/`--surf` | — | `statusPending` (`#5C6863`/`#96A19A`) | `statusPendingTint` (`#EDF0EF`/`#1D2521`) |

Consistente con el precedente `ChipStatus.Pending → colors.statusPending`.

---

## 5. Los tres montos sugeridos (`registrar-abono.html`, chips `.sug`)

| Sugerencia | Var. mock | Hex mock | Token contenido | Token fondo |
|---|---|---|---|---|
| Esperado hoy | `--ac`/`--acDeep`/`--acInk` | `#34D399`/`#0F2A22`/`#6EE7B7` | `statusPaid` | `statusPaidTint` |
| Al corriente | `--teal`/`--tealDeep` | `#3BC9DB`/`#0D2830` | `statusTeal` | `statusTealTint` |
| Liquidar | `--vio`/`--vioDeep` | `#A78BFA`/`#1E1A33` | `promise` | `promiseTint` |

Nota: el mock usa dos tonos de verde distintos para "esperado hoy" (`--ac` en el borde, `--acInk`
más claro en el texto — un matiz de énfasis). `MspColors` solo trae un par `statusPaid`/
`statusPaidTint`; se colapsan los dos a uno solo sin pérdida de legibilidad (mismo criterio que
el resto de los chips de estado, que ya usan un único par contenido/tint).

---

## 6. Bandas de advertencia (hallazgo adicional, no pedido explícitamente pero presente en los mocks)

| Banda | Var. mock | Token contenido | Token fondo |
|---|---|---|---|
| `.aband` (riesgo de abandono, `registrar-abono.html`) | `--red`/`--redDeep`, texto fijo `#FFA79E` | `statusOverdue` | `statusOverdueTint` |
| `.wband` (advertencia, `registrar-abono.html`) | `--amb`/`--ambDeep` | `statusPartial` | `statusPartialTint` |

`.aband` usa un tono de texto manual (`#FFA79E`) más claro que `statusOverdue` puro, para
legibilidad sobre el fondo rojo oscuro. No hay token equivalente en `MspColors`; se usa
`statusOverdue` igual (mismo criterio de la regla dura de accesibilidad de los chips: el color
nunca es el único portador de significado, siempre va con ícono + texto).

---

## 7. Propuesta de token nuevo — `onSurfaceFaint`

`MspColors` no tiene un tercer nivel de texto. El mock sí lo usa consistentemente (`--faint`,
decenas de ocurrencias en las cuatro pantallas: timestamps, "vigente hasta", pies de tarjeta,
labels de 9-11px). Se propone:

| Campo | Light | Dark |
|---|---|---|
| `onSurfaceFaint` | `#7C857F` | `#6B746F` |

**Contraste medido** (fórmula WCAG 2.x exacta, la misma de
`core/designsystem/.../theme/ContrastAAATest.kt:179-210`, recalculada a mano en Python — ver
metodología abajo):

| Par | Ratio |
|---|---|
| `onSurfaceFaint` light `#7C857F` sobre `background` light `#F4F6F5` | 3.51:1 |
| `onSurfaceFaint` light `#7C857F` sobre `surface` light `#FFFFFF` | 3.81:1 |
| `onSurfaceFaint` dark `#6B746F` sobre `background` dark `#000000` | 4.35:1 |
| `onSurfaceFaint` dark `#6B746F` sobre `surface` dark `#141917` | 3.69:1 |

**Piso objetivo:** UI-component (3:1), no AA-normal (4.5:1) — mismo piso que el precedente ya
aceptado para `statusPartial`/`statusTeal` (`ContrastAAATest.kt:148-153`, comentario líneas 49-60:
"la mitigación real es la regla dura de nunca-color-solo, no un contraste perfecto"). Justificación
igual: `--faint` en el mock nunca es el único portador de la información (siempre acompaña un
label o valor de nivel superior), y el propio mock solo alcanza 3.05-3.63:1 con su hex original
sobre sus propios fondos (medido: `#5D6864` sobre `#080A09` mock = 3.43:1, sobre `#141A18` mock =
3.05:1) — el valor propuesto iguala o mejora ese piso contra los fondos reales de `MspColors`.

**Metodología del cálculo:** luminancia relativa sRGB por canal linealizado
(`L = 0.2126·R + 0.7152·G + 0.0722·B`, gamma WCAG §1.4.3) y ratio `(L_claro + 0.05)/(L_oscuro +
0.05)` — fórmula idéntica a `wcagContrastRatio()` en `ContrastAAATest.kt:179`. Script Python usado
para el cálculo (no forma parte del entregable, solo la evidencia):

```python
def lin(c):
    c = c/255
    return c/12.92 if c <= 0.03928 else ((c+0.055)/1.055)**2.4
def lum(hex_color):
    h = hex_color.lstrip('#')
    r,g,b = int(h[0:2],16), int(h[2:4],16), int(h[4:6],16)
    return 0.2126*lin(r)+0.7152*lin(g)+0.0722*lin(b)
def ratio(fg,bg):
    l1,l2 = lum(fg), lum(bg)
    return (max(l1,l2)+0.05)/(min(l1,l2)+0.05)
```

**Quien implemente Task 5** (`MspTheme.kt`) o el módulo que agregue el campo a `MspColors` debe
añadir `onSurfaceFaint: Color` al data class, a `mspLightColors()`/`mspDarkColors()`, a
`lerpMspColors()` y a `MspColorsTest.kt` (ese archivo ya recorre todos los campos por nombre,
`MspColorsTest.kt:182-196`) — no es trabajo de esta tarea, se deja anotado para quien lo consuma.

---

## 8. Pares bajo AA parqueados que aparecen en esta tabla (`NIGHT-REPORT.md` §B.2 — no se resuelven aquí)

| Par | Ratio medido | Fila de esta tabla |
|---|---|---|
| `onBrand` / `brand` (dark) | 3.68:1 | §3(a) — todas las filas de acción |
| `statusPartial` / `statusPartialTint` (light) | 3.71:1 | §4 fila 3 (Vuelvo) |
| `statusTeal` / `statusTealTint` (light) | 4.19:1 | §4 fila 2 (Parcial) |

Los tres pares tocan esta tabla porque los tres tokens (`brand`, `statusPartial`, `statusTeal`)
son justamente los que el mapeo de los mocks activa. Ninguno se resuelve en esta tarea —
decisión pendiente del usuario, ya parqueada desde el Plan 3.

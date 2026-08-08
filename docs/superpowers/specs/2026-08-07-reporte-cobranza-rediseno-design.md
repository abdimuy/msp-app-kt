# Spec de diseño: Rediseño del Reporte de cobranza + Design System (azul, derivado de kollect)

> **Fecha:** 2026-08-07
> **Estado:** rumbo visual aprobado (brainstorm). Compañero del [spec de migración](2026-08-07-migracion-arquitectura-msp-app-kt.md); es el detalle de UI del **piloto** (reporte de cobranza) y del **design system** que hereda toda la app.
> **Mockup de referencia (fuente de verdad):** `docs/design/reporte-cobranza-mockup.html` — interactivo (toggle Día/Semana, sheets, reveal de tema, ocultar cifras).
> **Referencias estudiadas:** kollect-app `core/designsystem` (código real), `docs/design/campo-ui-mockup.html`, y artifacts de Claude "Inicio — 3 variantes", "Detalle de venta · header", "Plan del Día — cobranza", "Lista de clientes — cards".

## 1. Contexto

La pantalla del reporte de cobranza (`payments/screens/DailyReportScreen`, ruta `daily_reports`) es hoy un **generador de PDF**: selector de fecha → lista plana de pagos → botón "GENERAR PDF" + impresión térmica. Los números valiosos (total, efectivo vs transferencia, condonaciones) se **calculan pero solo aparecen en el PDF**, nunca en pantalla. El cobrador no puede responder "¿cuánto llevo cobrado?" de un vistazo.

**Objetivo:** convertirla en un **tablero** que responde eso en <5s, con la identidad y calidad de kollect remapeada a azul. Es el **piloto** de la migración (primera pantalla en su módulo, estrena el design system + telemetría + tests).

## 2. Design System — azul derivado de kollect (`:core:designsystem`)

Se adopta el sistema de tokens de kollect (`CampoTheme`: Colors/Type/Shapes/Spacing/Motion), **remapeado solo en la marca a azul**. Todos los tokens semánticos y neutros se heredan 1:1 de kollect.

### 2.1 Color
- **Marca = "Azul A"** (elegido sobre índigo y océano por legibilidad/frescura al sol):
  | token | light | dark |
  |---|---|---|
  | `brand` | `#2563EB` | `#3B82F6` |
  | `brand2` (fin de gradiente) | `#1D4ED8` | `#1D5FB0` |
  | `brandTint` (fondos avatar/chip) | `#EAF0FE` | `#0E2440` |
- **`heroProgressFill` = mint-teal `#6FE3C2`** (NO el verde `#7FE0A6` de kollect): el verde sobre hero azul creaba un choque frío/cálido; el mint-teal puentea azul↔verde y mantiene la semántica "cobrado". El **verde `statusPaid`** se conserva para el dot de "efectivo"/pagado.
- Resto de tokens (background, surface, onSurface, muted, outline, statusPaid/Partial/Overdue/Pending/Info/Teal, danger, promise, tracks) = **iguales a kollect** (ver `CampoColors.kt`). Dark = OLED (background negro puro).
- **Regla:** el estado siempre se comunica **color + ícono + texto**, nunca solo color.

### 2.2 Tipografía
- **Manrope** (TTF variable, pesos 400/500/600/700/800), empaquetado en `res/font/` (sin depender de internet) — idéntico a kollect.
- **Cifras tabulares + lining** (`tnum, lnum`) donde los montos se alinean en columna; **proporcionales lining** en montos display (hero) para que la coma miles kerne pegado. Escala = `CampoType.kt` (hero 36–37sp/800, tiles 22sp, etc.).

### 2.3 Formas y motion
- Radios: card 20 (hero 22), tile 16, control/chip-btn 12, button/field 14/16, chip 999.
- Sombra hairline + 1dp (superficies casi planas).

## 3. Componentes (adoptados de kollect)

`HeroTodayCard` (adaptado), `BentoTile`, `WeeklyBarsCard`, `CarteraCard`, `MoneyText` (+`formatMoneyMxn` es-MX, `MASKED_MONEY = "$••••"`), `PrivacyEyeToggle`, `ThemeToggle`+`ThemeRevealController`, `SegmentChips`, `SyncBand`/`PaymentSyncPill`, `InitialsAvatar`, `PrimaryFieldButton`, `BrandGradient`, `ProgressBar`/`ProgressRing`, `StatusChip`.

### 3.1 Interacciones firma
- **Reveal circular de tema** (estilo Telegram): al tocar el toggle, el tema nuevo se revela en un círculo que crece desde el punto tocado (snapshot del frame viejo + `Animatable` de radio, `tween` ~380–520ms). Fallback crossfade.
- **Ocultar cifras** (`PrivacyEyeToggle`): enmascara **todos** los montos a `$••••` (dinero es sensible en campo).
- **Barra inferior con difuminado** (NO sólida): `verticalGradient(background@alpha0 → background @ ~40%)` anclada abajo; el contenido se desvanece detrás. Copiado de la `ActionBar` de `VentaDetalleHistorial.kt` (solo el estilo, no los botones de venta). El scroll lleva padding inferior para subir el contenido sobre la barra.
- **Transición de tab** (Día↔Semana): crossfade + **slide direccional** (~300ms, `cubic-bezier(.2,.7,.2,1)`) — Día→Semana entra desde la derecha, Semana→Día desde la izquierda.
- **Entrada escalonada** de tarjetas (fade+rise ≤500ms).
- **Todas las animaciones son desactivables**: respetan `prefers-reduced-motion` / "reducir movimiento" de Android (fallback instantáneo/crossfade). Ver §5.

## 4. Pantalla: Reporte de cobranza (piloto)

### 4.1 Un solo reporte, dos periodos
- **Día y Semana unificados** en UNA pantalla con selector de periodo (segmented `Día · Semana`). Mismo pipeline de datos (`getPaymentsByDate(inicio, fin)` sobre Room). → **el reporte semanal deja de ser una migración/pantalla aparte.**
- **La semana = ciclo del cobrador**, no lunes-domingo: rango `[FECHA_CARGA_INICIAL, ahora]`, leído del **doc de Firebase del usuario** (`userData.FECHA_CARGA_INICIAL`), variable en días. El módulo del reporte depende del dato de usuario/auth para ese inicio de ciclo.
  - Etiqueta refleja el ciclo real ("semana · lun 3 – vie 7 ago · 5 días").
  - Tendencia = barras por día del ciclo (no siempre 7), hoy resaltado.

### 4.2 Composición (dirección "B · Aireado", densidad media)
De arriba a abajo:
1. **Header**: menú (☰) + título **"Cobranza"** (una línea) + subtítulo ("Reporte · <cobrador>") + `PrivacyEyeToggle` (👁) + `ThemeToggle` (🌙). *(Título corto a propósito: "Reporte de cobranza" partía en dos líneas y se veía junior; el toggle + contenido ya comunican que es el reporte.)*
2. **Selector de periodo** (Día · Semana).
3. **Range pill** (fecha/ciclo) + **sync pill** discreta ("N por subir").
4. **HERO** (grande, absorbe la tendencia): overline ("Cobrado · <fecha/ciclo>") + **delta chip** (▲% vs ayer/ciclo) + monto display + **frase-insight** ("32 pagos · vas al 91% de tu meta · a este ritmo cierras en $19,800") + barra de progreso (mint) + caption de meta + **sparkline embebida** (por hora en Día / por día en Semana) + **wells** (Efectivo en mano · Ticket prom.).
5. **Duo de tiles**: Efectivo · Transferencia (monto + nº pagos).
6. **Chips**: Condonado · Visitas.
7. **Detalle**: Día → lista de pagos (segment Hora/Nombre, filas con `InitialsAvatar` + method pill + dot "por subir"); Semana → **resumen por día** (cada día → sheet con sus pagos).
8. **Barra difuminada**: Compartir · Imprimir · PDF.

### 4.3 Progressive disclosure — bottom sheets
Cada card es tappable y abre un **`ModalBottomSheet`** con el detalle (nivel 2): Efectivo/Transferencia → lista de esos pagos; Condonado → quién/cuánto/motivo; Visitas → cliente/tipo/nota; Hero → resumen (meta/ritmo/mejor hora/falta); día (Semana) → pagos de ese día; fila de pago → detalle de pago. La lista completa (nivel 3) vive dentro del reporte / los sheets, nunca todo a la vez.

### 4.4 Datos
- **Ya disponibles (piloto, casi cero backend):** total, conteo, efectivo/transferencia, ticket promedio, condonaciones, visitas, timeline por hora, tendencia 7 días / por día del ciclo (todo desde pagos en Room por rango) + **meta sugerida** derivada (promedio/mediana ~14 días).
- **Fase 2 (requiere backend de saldos por zona):** `CarteraCard` "cobrado vs pendiente" (el movimiento de transparencia), insight de zona. Meta fijada por la oficina (config Firestore/msp-api).
- **Otras pantallas (no este reporte):** comisión estimada/bono/racha, promesas de hoy, falta por cobrar en ruta, riesgo de atraso, recompra/winback, efectivo por entregar, anomalías. Van en el home/clientes, no en el reporte.

## 5. Accesibilidad (horneada en el design system, para TODA la app)
- **Default (todos):** contraste AAA en hero/estados críticos, número héroe grande, pesos medios/semibold (nunca hairline), targets 48–56dp, cifras tabulares, nunca solo color, interlineado generoso.
- **Escala con el sistema:** honra tamaño de fuente / tamaño de pantalla / negrita de Android; layouts que **refluyen** (no truncan) el dinero hasta ~200%.
- **Opt-in:** **preferencia por-usuario "Tamaño de texto" (Normal/Grande/Muy grande)** que escala toda la app sin tocar ajustes de Android; **respetar "reducir movimiento"** (todas las animaciones se desactivan → crossfade/instantáneo). *(Read-aloud y TalkBack quedan como plus posteriores.)*
- **Layouts adaptativos Tier 1 / Tier 2 en TODAS las pantallas:** Tier 1 (Normal/Grande) = layout denso responsivo; Tier 2 (Muy grande) = layout alterno **curado** (una idea por vista, targets mayores) sobre el **mismo estado/ViewModel** — solo cambia la capa `ui/`.
- **Tests:** screenshot (Roborazzi) **por tier y por escala de fuente** (1.0x / 1.3x / 2.0x); "terminado" es imposible si el dinero se corta en grande.

## 6. Reglas aprendidas → van al design system
- **`flex-shrink: 0` en los hijos del contenedor con scroll** — un flex-item con `overflow:hidden` en columna con scroll se colapsaba por debajo de su contenido (bug del hero que desaparecía). El scroll maneja el overflow, no la compresión de tarjetas.
- **Hero = gradiente plano** (sin "beam"/glow radial inventado): kollect usa gradiente 150° liso; el glow se leía como glitch.
- **Cifras tabulares/lining** siempre en dinero.

## 7. Implicaciones para el plan de migración
- El piloto (`:feature:dailyReport`) pasa a ser **el reporte de cobranza unificado (Día/Semana)**; `WeeklyReport` se colapsa dentro (ya no es paso aparte).
- El módulo del reporte lee `FECHA_CARGA_INICIAL` del usuario (dependencia de auth/usuario, por contrato).
- Extraer la pantalla de `payments/screens/` a su módulo y fijar el patrón de lectura cross-módulo (transfers/pagos) por contrato — como ya prevé el spec de migración §9.
- Los componentes de §3 se implementan en `:core:designsystem`; la accesibilidad de §5 es responsabilidad del template + tests.

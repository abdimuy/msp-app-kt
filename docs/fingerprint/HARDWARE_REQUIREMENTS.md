# Requisitos de hardware — Lector biométrico

Documento de referencia para decidir **qué lectores** y **qué tablets** son compatibles con la implementación opensource de huella digital en MSP App.

> **TL;DR** — Necesitas un **DigitalPersona U.are.U 4500 clásico** (VID `0x05BA` / PID `0x000A`) + un **dispositivo Android con USB OTG** + un **cable OTG USB-A hembra a USB-C macho** (o micro-USB según el tablet). Cualquier otro modelo de lector o cualquier tablet sin OTG **no funciona** con este stack.

---

## 1. Lector de huella

### 1.1 Modelo soportado

| Campo | Valor |
|---|---|
| **Marca** | DigitalPersona (ahora parte de HID Global / Crossmatch) |
| **Modelo comercial** | U.are.U® 4500 |
| **Silicon interno** | URU4000B |
| **USB Vendor ID** | `0x05BA` (1466 decimal) |
| **USB Product ID** | `0x000A` (10 decimal) |
| **Interfaz** | USB 2.0 Type-A macho (cable fijo) |
| **Consumo típico** | ~180 mA @ 5V |
| **Resolución** | 512 dpi |
| **Tamaño de imagen raw** | 111,040 bytes (355×390 px, 8-bit grayscale) |
| **Descriptor USB** | `"U.are.U® 4500 Fingerprint Reader"` |

### 1.2 Modelos compatibles con trabajo extra mínimo

Si en algún futuro compran una unidad diferente, estos modelos **también son soportados por libfprint** pero **necesitan modificaciones** a la implementación actual porque usan distintas secuencias de firmware internas (la constante `driver_data` del driver de libfprint cambia):

| Modelo | VID | PID | Estado con nuestra implementación |
|---|---|---|---|
| U.are.U 4000 (standalone) | `0x05BA` | `0x0007` | ⚠️ Requiere port del branch `DP_URU4000` (~1 día de trabajo) |
| U.are.U 4000 (keyboard) | `0x05BA` | `0x0008` | ⚠️ Requiere port del branch `DP_URU4000` |
| U.are.U 4000B | `0x05BA` | `0x000A` | ✅ Mismo silicon que 4500, funciona igual |

### 1.3 Modelos **NO compatibles** (evitar comprar)

| Modelo | Razón |
|---|---|
| **U.are.U 4500HD** | Protocolo diferente, no está en el driver opensource. Solo funciona con SDK propietario de HID Global. |
| **U.are.U 5100, 5160, 5300** | Nuevas generaciones con firmware distinto, sin soporte en libfprint. |
| **Microsoft Fingerprint Reader** (VID `0x045E`) | Mismo chip, distinto enclosure, pero descripción USB diferente. Requeriría port adicional. |
| Cualquier lector que no sea DigitalPersona | No soportado por el stack que implementamos. |

> **Al comprar**, confirma con el proveedor que el modelo **exacto** es **U.are.U 4500** (sin "HD" ni otros sufijos) y que la caja/etiqueta del lector dice "**U.are.U 4500**" o "**U.are.U 4500B**". Si dice "4500HD" **no sirve**.

### 1.4 Cómo verificar un lector antes de asumir que sirve

Antes de asignar una unidad a un cobrador, verifica que enumera con el VID/PID correcto:

**En Mac (OS X 10.15+):**
```bash
ioreg -p IOUSB -l -w 0 | grep -B1 -A5 -i "persona"
```
Debe mostrar:
```
+-o U.are.U® 4500 Fingerprint Reader@...
      "idProduct" = 10
      "idVendor" = 1466
      "USB Vendor Name" = "DigitalPersona, Inc."
```

**En Linux:**
```bash
lsusb | grep -i persona
```
Debe mostrar:
```
Bus 001 Device 004: ID 05ba:000a DigitalPersona, Inc. Fingerprint Reader
```

**En Windows:**
1. Administrador de dispositivos → Dispositivos de interfaz humana (HID)
2. Doble clic en el lector → pestaña Detalles → ID de hardware
3. Debe contener `USB\VID_05BA&PID_000A`

**En Android (vía app MSP):**
Al abrir la pantalla de captura de huella (cuando la implementemos), debe aparecer el mensaje "Lector detectado" con el VID/PID en el panel de diagnóstico. Si aparece "Lector no reconocido", el modelo no es compatible.

---

## 2. Dispositivo Android (tablet/teléfono del cobrador)

### 2.1 Requisitos obligatorios

| Requisito | Valor mínimo | Razón |
|---|---|---|
| **Android** | 7.0 (API 24) | SourceAFIS (matching) requiere API 24; la app ya pide `minSdk = 24`. |
| **USB OTG / USB Host** | ✅ obligatorio | El lector se conecta físicamente por USB. Sin OTG no hay comunicación. |
| **Puerto físico** | USB-C **o** micro-USB | Determina qué cable OTG comprar. |
| **Corriente disponible por USB OTG** | ≥ 200 mA sostenidos | El lector consume ~180 mA; con margen. Tablets ultra-baratos pueden no entregar esto. |
| **Permiso USB** | Se pide al usuario al conectar | Ya manejado por la implementación vía `UsbManager.requestPermission()`. |

### 2.2 Cómo verificar USB OTG en un tablet

**Método 1 — specs del fabricante:**
Busca "USB OTG" o "USB Host" en la ficha técnica oficial. Si no lo menciona explícitamente, asume que **no lo tiene** (los fabricantes sí lo anuncian cuando existe).

**Método 2 — apps de diagnóstico:**
Instala "USB OTG Checker" (Play Store, gratis) en el tablet. Te dice en 2 segundos si el hardware soporta OTG.

**Método 3 — prueba directa:**
Conecta un USB stick común con un cable OTG. Si el tablet lo monta y muestra los archivos, OTG funciona. Si no pasa nada, no funciona.

### 2.3 Tablets típicamente compatibles

- Samsung Galaxy Tab A / Tab S (la mayoría de modelos desde 2019)
- Lenovo Tab M / P / Yoga (la mayoría)
- Xiaomi Pad, Redmi Pad
- Huawei MatePad (anteriores a la restricción de Google)
- Tablets con Android 10+ de marcas conocidas

### 2.4 Tablets típicamente **no** compatibles (evitar)

- Tablets baratos genéricos sin marca (sin OTG, o con OTG "roto" de fábrica)
- Amazon Fire Tablets (Fire OS, no es Android completo — la app no corre allí)
- Dispositivos con Android < 7.0
- Algunos Samsung Galaxy Tab A económicos anteriores a 2018 (OTG limitado)

### 2.5 Teléfonos por marca

> **Regla de oro:** OTG es una característica de hardware que el fabricante decide incluir o no, **no se puede agregar por software ni actualización del sistema**. No confíes en el nombre de la serie: dos modelos del mismo año de la misma marca pueden tener uno OTG y otro no. **Valida cada unidad individualmente antes de desplegar al campo.**

#### Matriz de riesgo por marca

| Marca | Riesgo general | Nota |
|---|---|---|
| **Samsung** (Galaxy A, M, S, Note) | 🟢 **Bajo** | La mayoría de modelos desde 2018. Línea A económica (<A20) y sub-$3,500 MXN a veces no. |
| **Xiaomi / Redmi / POCO** | 🟡 **Medio** | La mayoría sí, pero algunos modelos específicos no. Ver lista negra abajo. |
| **Motorola** (Moto G, E, Edge) | 🔴 **Alto** | **Motorola no garantiza OTG oficialmente** desde 2021. Modelos nuevos pueden tenerlo deshabilitado. Valida siempre. |
| **Nokia / HMD** | 🟡 **Medio** | Depende del modelo, la línea económica (C series) típicamente no. |
| **Honor / Huawei** | 🟡 **Medio** | Los de 2019+ sí. Cuidado con modelos post-ban de Google (sin servicios Google, la app MSP no funciona). |
| **Realme / Oppo / Vivo** | 🟡 **Medio** | Mayoría sí; modelos ultra-baratos a veces no. |
| **ZTE / Alcatel / TCL / Blu** | 🔴 **Alto** | Muchos modelos económicos sin OTG. Evitar para este caso de uso. |
| **Telcel AT&T branded (Lanix, Hyundai, Zuum)** | 🔴 **Alto** | Son rebadges de modelos chinos genéricos, OTG inconsistente. Evitar. |
| Genéricos chinos sin marca reconocida | 🔴 **Muy alto** | Asumir que no tienen OTG. |

#### Modelos con OTG funcionando (casos conocidos)

Lista no exhaustiva, solo referencia. **Siempre validar la unidad específica**:

**Samsung:**
- Galaxy A32, A33, A34, A35, A50, A51, A52, A53, A54
- Galaxy A14, A15, A24, A25 (mid-2022+)
- Galaxy M32, M34, M52, M54
- Galaxy S21, S22, S23, S24 y todas las S anteriores desde S3
- Galaxy Note serie (todas desde Note 2)

**Xiaomi / Redmi / POCO:**
- Redmi Note 8, 9 Pro, 11, 12, 13, 14 (series Pro/Ultra)
- Redmi 8, Note 8
- POCO X3 Pro, X4, X5, X6, X7
- POCO M5, M6, M7
- POCO F5, F6, F7

**Motorola (verificar porque Moto no garantiza):**
- Moto G7, G7 Plus (confirmados)
- Moto G Power 2021
- Moto E4, E5, E5 Plus, E5 Supra (de época donde Moto sí soportaba)
- Edge originales (Edge, Edge+)

**Realme / Oppo / Vivo:**
- Realme 8 Pro (confirmado)
- Oppo/Vivo mid-range con USB-C desde 2020

### 2.6 Lista negra — Teléfonos **confirmadamente sin OTG**

Si un cobrador tiene uno de estos, **el lector no va a funcionar con su teléfono**. La única solución es cambiar el teléfono o asignarle un tablet dedicado.

**Xiaomi / Redmi:**
- Redmi 9, 9A, 9T, 9C (la línea Redmi "sin Note" económica)
- Redmi 10 Prime
- Redmi Note 10 y 10S (solo estos dos; Note 11+ sí)
- POCO F3

**Motorola (nuevos, post-2021):**
- Moto G Stylus 2023/2024/2025 y variantes ultra-baratas — **Motorola deshabilita OTG en muchos**
- Moto E desde 2021 en adelante (E7, E13, etc.) — inconsistente
- Cualquier Moto sub-$4,000 MXN 2023+ — **asumir que no tiene hasta verificar**

**Amazon Fire:**
- Cualquier Fire Tablet (el OS no es Android completo)

**Genéricos:**
- Cualquier teléfono marca "Hyundai", "Zuum", "Lanix", "Krip", "Verykool", "M4", "Stylos" — OTG inconsistente o ausente

### 2.7 Estrategia para flotas heterogéneas

Cuando la empresa tiene muchos modelos diferentes de teléfonos, hay 3 caminos prácticos. Ordenados de más recomendado a menos:

#### Camino A — Tablet dedicado por cobrador (**recomendado**)

Comprar **un mismo modelo de tablet barato con OTG verificado** para todos los cobradores. Costo: ~$2,500–$4,500 MXN por unidad.

**Ventajas:**
- Un solo modelo → un solo perfil de soporte, un solo set de pruebas
- Se sabe que funciona con el lector, siempre, sin sorpresas
- La pantalla del tablet es más grande → mejor UX para captura y firma
- Se puede dejar el lector pegado al tablet con velcro y queda todo el kit autocontenido

**Modelos sugeridos (2026):**
- **Samsung Galaxy Tab A9 / A9+** — ~$3,500 MXN, OTG confirmado, buen soporte a largo plazo
- **Lenovo Tab M9** — ~$2,500 MXN, OTG confirmado, económico
- **Xiaomi Redmi Pad SE** — ~$3,500 MXN, OTG confirmado

#### Camino B — Validar y clasificar cada teléfono existente

Inventariar la flota actual y auditar cada teléfono.

**Proceso:**
1. Entregar a cada cobrador un link para instalar **"USB OTG Checker"** (gratis, Play Store)
2. Pedirles que ejecuten la app y manden screenshot del resultado
3. Registrar en una hoja de cálculo: cobrador → marca/modelo → IMEI → OTG sí/no → cable OTG que necesita (USB-C o micro-USB)
4. Cobradores con teléfono **sin OTG** → se les asigna tablet del Camino A, o se los considera excluidos del flujo biométrico hasta renovar equipo

**Template de hoja de cálculo:**

| Cobrador | Marca | Modelo | IMEI | Puerto | OTG validado | Kit asignado | Fecha validación |
|---|---|---|---|---|---|---|---|
| Juan Pérez | Samsung | Galaxy A54 | 35... | USB-C | ✅ | Lector + cable C | 2026-04-20 |
| María López | Motorola | Moto G 2023 | 35... | USB-C | ❌ | Tablet Lenovo M9 | 2026-04-22 |

#### Camino C — Mixto

Cobradores con teléfonos OTG-compatible usan su teléfono; los demás reciben tablet. Es el más barato en la transición pero el más costoso en soporte a largo plazo (multiplicidad de modelos).

#### Recomendación de la implementación

Para un despliegue estable y escalable, **Camino A o Camino C**. Evitar depender de OTG de teléfonos Motorola nuevos — es el mayor factor de riesgo porque la empresa no garantiza la feature y puede desaparecer en un update del firmware.

---

## 3. Cable OTG

### 3.1 Especificación

Necesitas un **cable adaptador OTG** (también llamado "USB Host adapter"):

- **Extremo hembra:** USB-A (donde se enchufa el lector DP4500)
- **Extremo macho:** **USB-C** si el tablet es moderno, o **micro-USB** si es antiguo
- **Longitud:** 10–20 cm es suficiente y más estable (cables largos pueden introducir caídas de voltaje)
- **Marca:** preferentemente Anker, Ugreen o similar — los cables genéricos de $20 MXN a veces no pasan el pin de OTG correctamente

### 3.2 Adaptadores NO recomendados

- ❌ Cables USB-A a USB-C "normales" (sin circuito OTG)
- ❌ Hubs USB baratos sin alimentación externa — pueden no entregar suficiente corriente para el lector + otros periféricos
- ❌ Adaptadores "universales" con múltiples puertos (HDMI, SD, etc.) baratos — suelen fallar en OTG puro

### 3.3 Hub OTG con alimentación (opcional, solo si hay problemas de corriente)

Si el tablet no entrega 180 mA sostenidos por OTG (se nota porque el lector se desconecta solo tras unos segundos), usa un **hub OTG con fuente externa** (powered OTG hub). El hub se conecta al tablet y a la corriente eléctrica, y el lector al hub. Esto resuelve el 99% de los casos de tablets de bajo consumo.

---

## 4. Compatibilidad del entorno físico

### 4.1 Condiciones ambientales recomendadas

| Factor | Rango |
|---|---|
| Temperatura | 0°C – 40°C (el sensor óptico no opera bien fuera de esto) |
| Humedad | 20% – 80% sin condensación |
| Superficie del sensor | Limpia y seca — polvo o grasa reducen la calidad de la imagen |

### 4.2 Limpieza del sensor

- Usar **paño de microfibra seco** para limpieza de rutina
- Para grasa, usar **alcohol isopropílico ≥70%** con un paño, **nunca** rociando directamente al sensor
- No usar solventes fuertes ni toallas abrasivas

---

## 5. Checklist de validación antes de asignar hardware a un cobrador

Marca cada item antes de entregar el kit al personal de campo:

- [ ] **Lector físico:** etiqueta dice "U.are.U 4500" (no "4500HD", no "5100")
- [ ] **Lector verificado:** al conectarlo a una Mac/PC enumera como `05BA:000A`
- [ ] **Tablet:** Android ≥ 7.0, modelo en la lista de compatibles o verificado con USB OTG Checker
- [ ] **Cable OTG:** marca reconocida, longitud ≤ 20cm, tipo correcto (USB-C o micro-USB según tablet)
- [ ] **Prueba integrada:** tablet + cable OTG + lector enchufados → app MSP detecta el lector y hace una captura de prueba exitosa
- [ ] **Prueba de duración:** captura 10 huellas seguidas sin que el lector se desconecte (si falla → tablet con poca corriente OTG → hub con alimentación)

---

## 6. Stack de software que consume este hardware (referencia)

| Capa | Librería | Licencia |
|---|---|---|
| USB I/O con el DP4500 | `shodgson/uareulibrary` (port MIT de libfprint `uru4000.c`) | MIT |
| Extracción de minutiae y matching | `com.machinezoo.sourceafis:sourceafis:3.18.x` | Apache 2.0 |
| API USB de Android | `android.hardware.usb.*` (UsbManager, UsbDevice, UsbDeviceConnection) | Nativa de Android |

Ver `docs/fingerprint/` para documentación de implementación (se irá agregando conforme se desarrolle).

---

## 7. Referencias externas

**Librerías y protocolo:**
- [libfprint — supported devices](https://fprint.freedesktop.org/supported-devices.html)
- [libfprint `uru4000.c` driver](https://gitlab.freedesktop.org/libfprint/libfprint/blob/master/libfprint/drivers/uru4000.c)
- [shodgson/uareulibrary — MIT Android port](https://github.com/shodgson/uareulibrary)
- [SourceAFIS for Java](https://sourceafis.machinezoo.com/java)
- [Android USB Host API](https://developer.android.com/guide/topics/connectivity/usb/host)
- [DigitalPersona U.are.U 4500 datasheet oficial](https://www.neurotechnology.com/fingerprint-scanner-digitalpersona-u-are-u-4500.html)

**Compatibilidad OTG por fabricante:**
- [Samsung — lista de Galaxy con OTG](https://www.samsungsfour.com/tutorials/complete-list-of-samsung-galaxy-smartphones-with-otg-support-usb-on-the-go.html)
- [Mobitrix — versiones Android y soporte OTG](https://www.mobitrix.com/android-support/android-version-compatible-with-usb-otg.html)
- [Motorola Edge/G/E y accesorios OTG](https://en-emea.support.motorola.com/app/answers/detail/a_id/160550/)
- [Xiaomi — lista de modelos con/sin OTG en 2024](https://bestxiaomiproducts.com/does-xiaomi-support-otg/)
- [Beebom — cómo verificar OTG en cualquier Android](https://beebom.com/how-check-usb-otg-support-android-phone/)

**App de validación recomendada:**
- "USB OTG Checker" — buscar en Play Store, editor "FaitAuJapon" o similar, gratis.

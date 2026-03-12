# MSP App - Muebles San Pablo App

Sistema móvil de gestión de ventas y cobranza para vendedores en campo, desarrollado con Kotlin y
Jetpack Compose.

## 📱 Características Principales

### Gestión de Ventas

- Visualización de clientes con sistema de búsqueda difusa
- Estados de cobranza: Pendiente, Visitado, Pagado, No Pagado
- Información detallada de cada venta incluyendo productos y pagos
- Visualización en mapa de ubicaciones de clientes

### Sistema de Pagos

- Múltiples métodos de pago: Efectivo, Cheque, Transferencia
- Generación de tickets de pago en PDF
- Impresión térmica vía Bluetooth
- Historial completo de pagos por cliente

### Gestión de Visitas

- Registro de visitas a clientes
- Múltiples razones de no pago predefinidas
- Tracking GPS automático
- Sincronización en segundo plano

### Reportes

- Reporte diario de cobranza
- Reporte semanal consolidado
- Resumen de desempeño en pantalla principal

## 🛠️ Stack Tecnológico

- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose
- **Arquitectura:** MVVM + Clean Architecture
- **Base de datos local:** Room
- **Backend:** Firebase (Auth + Firestore)
- **API REST:** Retrofit
- **Mapas:** Google Maps Compose
- **Inyección de dependencias:** Manual (ViewModelProvider pattern)
- **Tareas en background:** WorkManager
- **Impresión:** ESCPOS Thermal Printer

## 📂 Estructura del Proyecto

```
app/src/main/java/com/example/msp_app/
├── core/                    # Componentes centrales
│   ├── context/            # Providers y contextos
│   ├── models/             # Modelos de dominio compartidos
│   └── utils/              # Utilidades (fecha, moneda, bluetooth, etc.)
├── data/                    # Capa de datos
│   ├── api/                # Servicios API REST
│   ├── local/              # Base de datos Room
│   └── models/             # Modelos de datos y mappers
├── features/                # Funcionalidades por módulo
│   ├── auth/               # Autenticación
│   ├── home/               # Pantalla principal
│   ├── payments/           # Gestión de pagos
│   ├── products/           # Productos
│   ├── sales/              # Ventas y clientes
│   └── visit/              # Registro de visitas
├── navigation/              # Navegación de la app
├── services/                # Servicios Android
├── ui/theme/                # Tema y estilos
└── workers/                 # Background workers
```

## 🚀 Configuración del Proyecto

### Requisitos Previos

- Android Studio Hedgehog o superior
- JDK 11
- Android SDK 35
- Cuenta de Firebase con proyecto configurado

### Instalación

1. Clona el repositorio:

```bash
git clone [URL_DEL_REPOSITORIO]
cd mspapp
```

2. Configura las API Keys en `local.properties`:

```properties
MAPS_API_KEY=tu_api_key_de_google_maps
```

3. Agrega el archivo `google-services.json` de Firebase en `app/`

4. Sincroniza el proyecto y ejecuta

## 📋 Permisos Requeridos

- **Ubicación:** Para tracking GPS y mapas
- **Bluetooth:** Para impresión térmica
- **Teléfono:** Para realizar llamadas a clientes
- **Internet:** Para sincronización con backend

## 🔧 Configuración de Build

### Debug

```kotlin
versionCode = 7
versionName = "2.0.7"
minSdk = 24
targetSdk = 35
```

### Release

⚠️ **Importante:** Configura tu propio keystore para builds de producción

## 📊 Funcionalidades por Pantalla

### Home

- Resumen de cobranza semanal
- Accesos rápidos a funciones principales
- Indicadores de desempeño

### Ventas

- Lista filtrable de clientes
- Tabs por estado: Por Visitar, Visitados, Pagados
- Búsqueda inteligente

### Detalle de Venta

- Información completa del cliente
- Productos vendidos
- Historial de pagos
- Acciones: Llamar, WhatsApp, Ver en mapa

### Pagos

- Registro de nuevo pago
- Selección de método de pago
- Generación e impresión de ticket

## 🔄 Sincronización

La app funciona offline y sincroniza automáticamente cuando hay conexión:

- Workers periódicos para pagos pendientes
- Workers para visitas registradas
- Caché local ilimitado con Firestore

## 🎨 Tema

Soporte para modo claro/oscuro con tema personalizado basado en Material 3.

## ✅ Calidad de Código

### Linter

Se usa [ktlint](https://pinterest.github.io/ktlint/) vía el plugin de Gradle `org.jlleitschuh.gradle.ktlint`. La configuración de reglas está en `.editorconfig`.

```bash
./gradlew ktlintCheck      # Verificar estilo
./gradlew ktlintFormat     # Auto-corregir estilo
```

### Tests

263 tests unitarios e integración cubriendo validación, ViewModels, Room DB y flujos completos de venta.

```bash
./gradlew testDebugUnitTest   # Ejecutar todos los tests
```

### Pre-commit Hook

Un hook de Git ejecuta lint y tests antes de cada commit. Se instala automáticamente al sincronizar el proyecto (vía tarea Gradle `installGitHook`). También se puede instalar manualmente:

```bash
./gradlew installGitHook
```

## 📝 Notas de Desarrollo

- La app usa desugar para compatibilidad con APIs de Java 8
- Firestore configurado con caché persistente ilimitado
- Búsqueda difusa implementada con Apache Commons Math

Desarrollado con ❤️ usando Kotlin y Jetpack Compose
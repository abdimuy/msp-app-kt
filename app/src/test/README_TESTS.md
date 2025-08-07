# Estructura de Tests - MSP App

## Organización

Los tests están organizados siguiendo la misma estructura del código fuente:

```
test/java/com/example/msp_app/
├── core/               # Tests de utilidades y modelos core
│   ├── utils/         # Tests de utilidades
│   └── models/        # Tests de modelos base
├── data/              # Tests de capa de datos
│   ├── mappers/       # Tests de mappers
│   ├── local/         # Tests de base de datos local
│   └── api/           # Tests de servicios API
├── features/          # Tests por feature/módulo
│   ├── auth/         # Tests de autenticación
│   ├── payments/     # Tests de pagos
│   ├── sales/        # Tests de ventas
│   ├── products/     # Tests de productos
│   └── visit/        # Tests de visitas
└── test-fixtures/     # Datos mock y utilidades de test
```

## Convenciones

1. **Naming**: `[NombreClase]Test.kt`
2. **Ubicación**: Misma estructura que el código fuente
3. **Método de test**: Usar backticks para nombres descriptivos
   ```kotlin
   @Test
   fun `should format currency correctly for Mexican pesos`() { }
   ```

## Ejecutar Tests

### En Android Studio
- Click derecho en la clase/método → Run
- O usar el ícono verde al lado del test

### Desde línea de comandos (Windows)
```bash
# Todos los tests
gradlew test

# Un test específico
gradlew test --tests "com.example.msp_app.core.utils.CurrencyUtilsTest"

# Tests de un paquete
gradlew test --tests "com.example.msp_app.core.utils.*"
```

### Desde línea de comandos (Mac/Linux)
```bash
# Todos los tests
./gradlew test

# Un test específico
./gradlew test --tests "com.example.msp_app.core.utils.CurrencyUtilsTest"
```

## Primer Test Creado

`CurrencyUtilsTest.kt` - Tests para las extensiones de formato de moneda:
- Formato con decimales
- Formato sin decimales
- Manejo de números negativos
- Diferentes locales
- Redondeo correcto

## Datos Mock

En `test-fixtures/` encontrarás:
- `MockData.kt` - Factory methods para crear objetos de prueba
- `TestConstants.kt` - Constantes reutilizables para tests
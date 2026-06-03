# Despliegue a Producción

## 1. Compilar release

```bash
./gradlew assembleRelease
```

El APK queda en: `app/build/outputs/apk/release/app-release.apk`

## 2. Subir a GitHub Releases

```bash
gh release create v{VERSION} app/build/outputs/apk/release/app-release.apk --title "v{VERSION}" --notes "Descripción de cambios"
```

## 3. Actualizar Firestore

En `config/api_settings` actualizar:

| Campo | Valor |
|---|---|
| `LATEST_VERSION` | La nueva versión (ej. `2.9.5`) |
| `APK_URL` | `https://github.com/abdimuy/msp-app-kt/releases/download/v{VERSION}/app-release.apk` |

Los usuarios verán el banner de actualización automáticamente.

## Archivos de versión

Al crear una nueva versión, actualizar estos archivos:

- `app/build.gradle.kts` → `versionCode` y `versionName`
- `app/src/main/java/com/example/msp_app/core/utils/Constants.kt` → `APP_VERSION`

---

## Runbook: Push-channel (SSE + by-ids) — secuencia de despliegue

El push-channel (commits 12–17) requiere que el servidor esté actualizado
**antes** de entregar la app, porque el cliente detecta el endpoint `/by-ids`
con el flag `byIdsAvailable`: si el servidor responde 404, el flag se apaga
para esa sesión y se cae al cursor-sync. Invertir el orden no rompe nada, pero
los cobradores no obtendrán el beneficio de latencia hasta el siguiente reinicio.

### Paso 1 — Servidor (msp-api, Windows Server)

1. Construir el binario para Windows:
   ```bash
   GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -o msp-api.exe ./cmd/api
   ```
2. Copiar `msp-api.exe` al servidor de producción y reiniciar el servicio NSSM.
3. Verificar que los endpoints nuevos responden:
   ```
   GET /v2/cobranza/sync/pagos/by-ids?zona_id=21&ids=1,2,3  → 200 []
   GET /v2/cobranza/sync/saldos/by-ids?zona_id=21&ids=1,2,3 → 200 []
   GET /v2/cobranza/sync/pagos/zona/21/stream               → text/event-stream
   ```
4. Verificar en logcat del servidor que el FbEvent listener está activo
   (`COBRANZA_SSE_ENABLED=true`).

### Paso 2 — Validación en staging (Android)

1. Instalar el APK de release en un dispositivo de prueba.
2. Iniciar sesión con un cobrador de la zona de staging.
3. Confirmar en logcat:
   - `SSE pagos conectando zona=X` / `SSE saldos conectando zona=X`
   - Tras un evento: `SSE pagos sync done: ids=N sync=...ms`
   - Sin `byIdsAvailable=false` en los logs.
4. Crear un pago de prueba en Microsip y verificar que aparece en la app
   en menos de 5 segundos (tolerancia de 2s SSE + 100ms debounce + red).

### Paso 3 — Producción (Android)

1. Subir el APK a GitHub Releases (paso 2 de este doc).
2. Actualizar `LATEST_VERSION` y `APK_URL` en Firestore.
3. Los cobradores verán el banner de actualización automáticamente.

### Rollback

- Si el servidor necesita rollback: revertir al binario anterior. Los clientes
  new detectarán la ausencia del `/by-ids` (404) y caerán al cursor-sync en
  esa sesión. No se pierde consistencia — solo aumenta la latencia.
- Si el APK necesita rollback: publicar un APK anterior en GitHub Releases y
  actualizar `LATEST_VERSION`. El feature SSE estaba disponible desde v2.11.0;
  el rollback a cualquier versión >= 2.11.0 mantiene el polling+reconcile.

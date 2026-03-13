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

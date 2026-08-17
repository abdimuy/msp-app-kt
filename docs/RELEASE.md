# Publicar una versión a la flota

Este documento describe cómo llega un APK nuevo a los teléfonos de los
cobradores. Sirve **aunque el script se rompa**: la sección
[Procedimiento manual](#procedimiento-manual-si-el-script-no-sirve) tiene los
mismos pasos a mano.

La herramienta es `scripts/release_apk.py`. Sus pruebas son
`scripts/test_release_apk.py`.

---

## 1. Los DOS mecanismos de actualización

Los dos viven en el **mismo** documento de Firestore, `config/api_settings`, y
hacen cosas distintas. Confundirlos es caro.

| | **Sugerida** | **Bloqueo** |
|---|---|---|
| Campos | `LATEST_VERSION`, `APK_URL` | `MIN_VERSION_CODE`, `MIN_VERSION_NAME`, `MIN_VERSION_DEADLINE`, `MIN_VERSION_EXEMPT_DEVICES`, `MIN_VERSION_APK_URL`, `MIN_VERSION_APK_SIZE`, `MIN_VERSION_APK_SHA256` |
| Efecto | ofrece actualizar; el cobrador sigue trabajando | **impide usar la app** hasta actualizar |
| Compara | `versionName` (`2.17.1`), componente por componente **como números** | `versionCode` (`58`), como **entero** |
| Código | `app/.../core/updates/UpdateChecker.kt` | `:core:appgate` |
| Estado | es lo que se usa hoy | construido, **nunca activado en producción** |

Consecuencias prácticas:

- **`versionCode` y `versionName` suben juntos, siempre.** Cada mecanismo mira
  uno solo. Subir el `versionName` sin el `versionCode` deja el bloqueo ciego;
  al revés, la actualización sugerida no se ofrece nunca.
- **`LATEST_VERSION` va sin sufijo.** `Constants.APP_VERSION` hace
  `BuildConfig.VERSION_NAME.substringBefore("-")`, así que un APK `devlocal`
  reporta `2.17.0`, no `2.17.0-local+5d28e4de`. Si se publicara la versión con
  sufijo, la comparación no cuadraría y el aviso de actualización quedaría
  pegado. El script publica siempre la base.
- **`baseURL` vive en ese mismo documento y es el kill-switch de la flota
  entera.** Una escritura que lo pise o lo borre deja a todos los teléfonos sin
  API. El script **nunca** lo incluye en el payload —ni con el mismo valor— y
  siempre escribe con `merge`.

---

## 2. Qué decide el humano

El script no decide nada de esto, a propósito:

| Decisión | Por qué no la toma el script |
|---|---|
| **Qué versión sigue** (`2.17.1`? `2.18.0`?) | Depende de qué cambió y de qué se le prometió a quién. Hoy el código dice `versionCode = 57`, `versionName = "2.17.0"` y **`v2.17.0` ya existe en GitHub como pre-release**, así que la siguiente publicación probablemente sea `58` / `2.17.1`. |
| **Cuándo** | Un release a media jornada de cobranza no es lo mismo que uno un domingo. |
| **Commitear el bump** | El tag de GitHub apunta a un commit. Si el bump no está commiteado, el release etiquetaría un árbol que todavía dice la versión vieja. Por eso `--bump` escribe el archivo y **para**: revise, commitee, y vuelva a correr. |
| **Bloquear o no** (`--force-update`) | Ver la sección 5. Casi nunca. |
| **El texto de la fecha límite** (`--deadline`) | Lo lee el cobrador tal cual, y quien lo escribe es la misma persona que avisa por WhatsApp. Viaja como texto, no como marca de tiempo, para que no haya husos horarios de por medio. |

---

## 3. El procedimiento

### 3.1 Antes de empezar

- Árbol limpio y el commit subido a `origin` (el script lo exige).
- `git fetch --tags` — el script necesita los tags locales para leer el
  `versionCode` del release anterior y descartar una colisión. Hoy `v2.17.0`
  existe en GitHub y **el tag no está en la copia local**; sin el fetch el
  script aborta pidiéndolo.
- `keystore.properties` presente en la raíz. **Sin él, `app/build.gradle.kts`
  (línea ~95) usa la firma de _debug_ sin avisar**, y el APK resultante no se
  puede instalar encima del que trae la flota
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). El script aborta antes de compilar.
- `gh` autenticado; `apksigner` en el SDK de Android; `keytool` en el `PATH`
  (`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`).

> El keystore de release existe **en una sola laptop**. Si se pierde, la app
> instalada no se puede volver a actualizar nunca: habría que desinstalar y
> reinstalar en cada teléfono, borrando los pagos pendientes.

### 3.2 Subir la versión

```sh
python3 scripts/release_apk.py --dev --bump 58:2.17.1          # simulacro
python3 scripts/release_apk.py --dev --bump 58:2.17.1 --apply  # escribe el archivo
git add app/build.gradle.kts && git commit -m "chore(release): 2.17.1 (58)"
git push
```

`--bump` exige que **ambos** valores suban. No sigue al release: pare, revise
el diff, commitee.

### 3.3 Simulacro

Sin `--apply` el script no escribe nada — ni Firestore, ni el release, ni
`app/build.gradle.kts` — y muestra el payload exacto que escribiría.

```sh
python3 scripts/release_apk.py --dev
```

### 3.4 Publicar

```sh
# dev
python3 scripts/release_apk.py --dev --apply

# producción: exige teclear la versión, o pasarla en --confirm-prod
python3 scripts/release_apk.py --prod --apply --confirm-prod 2.17.1
```

### 3.5 Qué hace, en orden

1. **Árbol limpio y commit conocido.** Aborta si hay cambios sueltos, o si el
   `HEAD` no está en ninguna rama remota (un release cuyo tag apunta a un
   commit que solo existe en una laptop no se puede reproducir ni revisar).
2. **`keystore.properties` o nada.** Antes de compilar: descubrirlo después
   cuesta minutos de build y, si se sube, una semana de cobradores atorados.
3. **Versión y colisiones.** Lee `versionCode`/`versionName` de
   `app/build.gradle.kts`. Aborta si el tag ya existe (mira **los releases de
   GitHub y los tags locales**, porque un pre-release puede existir sin tag
   local), si el `versionName` no es posterior al último publicado, o si el
   `versionCode` no supera al del release anterior.
4. **Confirmación de producción** — antes de gastar minutos compilando.
5. **`./gradlew :app:assembleProdRelease`.**
6. **Verifica la firma** con `apksigner verify --print-certs` contra la huella
   que sale de `keytool` sobre el keystore que declara `keystore.properties`.
   Dos comprobaciones, no una: la huella tiene que coincidir **y** ningún
   firmante puede ser `CN=Android Debug`. La segunda no es redundante: si
   `keystore.properties` apuntara por error al debug keystore, la primera
   pasaría vacíamente porque ambas huellas saldrían de la misma llave.
7. **SHA-256 y tamaño del APK recién construido**, leídos una sola vez. El
   archivo que se sube es una copia byte a byte del que se midió, y se vuelve a
   medir antes de subirlo.
8. **`gh release create`** con el APK como
   `app-prod-release-<versión>.apk`, apuntando al commit verificado en el paso 1.
9. **Firestore, con `merge`**: solo `LATEST_VERSION` y `APK_URL`. Nunca
   `baseURL`. Aborta si el documento ya anuncia una versión igual o posterior.
10. **Relee el documento** y lo imprime completo, para que se vea que `baseURL`
    sigue ahí.

---

## 4. Interfaz de línea de comandos

```
python3 scripts/release_apk.py (--dev | --prod) [opciones]
```

| Bandera | Qué hace |
|---|---|
| `--dev` / `--prod` | **Obligatorio, nunca por defecto.** Elige el proyecto de Firebase. |
| `--apply` | Escribe de verdad. Sin ella, simulacro. |
| `--bump CODE:NAME` | Solo sube la versión en `app/build.gradle.kts` y termina. |
| `--version-name X` / `--version-code N` | Asegura que el archivo diga eso. Aborta si no. |
| `--force-update` | Escribe además los 7 `MIN_VERSION_*`. Ver sección 5. |
| `--deadline "vie 22"` | Texto de la fecha límite. Obligatorio con `--force-update`. |
| `--exempt a@x.com,b@x.com` | Dispositivos exentos del bloqueo. |
| `--confirm-prod 2.17.1` | Confirma producción sin terminal interactiva. |
| `--apk RUTA` | Usa ese APK en vez de compilar. |
| `--skip-build` | No corre Gradle (el APK ya está construido). |
| `--notes`, `--prerelease` | Notas y marca de pre-release. |
| `--credentials`, `--credentials-dir` | Dónde está la llave de servicio. |
| `--apksigner`, `--keytool` | Rutas explícitas si no se autodetectan. |

### Llaves y el candado del `project_id`

| Entorno | Proyecto | Llave (por defecto en `/Volumes/M2-1TB/Developer/msp-api/`) |
|---|---|---|
| `--dev` | `msp-dev-96ff5` | `serviceAccountKey.json` |
| `--prod` | `msp-db-1c2ce` | `serviceAccountKeyProduction.json` |

El script **lee el `project_id` de la llave** y aborta si no corresponde al
entorno pedido. Es el mismo candado que ya evitó un accidente en msp-api: manda
lo que dice la llave, no el nombre del archivo.

---

## 5. Cuándo se usan los `MIN_VERSION_*`

**Casi nunca.** Son la palanca de emergencia.

El bloqueo deja al cobrador **sin poder usar la app** hasta que instale la
versión nueva. En una jornada de cobranza eso es un cobrador parado en la calle
con un teléfono que no le sirve. El costo de equivocarse es inmediato y
visible.

Se justifica solo cuando **seguir usando la versión vieja hace daño**:

- la versión instalada pierde, duplica o corrompe pagos;
- el contrato con el API cambió y las capturas viejas se rechazan en silencio;
- hay una fuga de datos o un problema de seguridad en el APK que ya está
  instalado.

**No** se justifica para: una pantalla nueva, un arreglo cosmético, "que todos
tengan la última", o un cambio que la versión vieja simplemente no muestra.

### Si de verdad hay que bloquear

1. Avise por WhatsApp **antes**. La pantalla de bloqueo muestra el texto de
   `--deadline` tal cual: que diga lo mismo que dijo usted.
2. Exente los teléfonos que no pueden actualizar en ese momento
   (`--exempt`), o quedarán fuera sin recurso.
3. Publique con:

```sh
python3 scripts/release_apk.py --prod --apply --confirm-prod 2.17.1 \
    --force-update --deadline "vie 22" --exempt cobrador1@muebleriamsp.mx
```

Los siete campos se escriben juntos o no se escriben. El cliente
(`FirestoreMinVersionConfigSource.readUpdatePackage`) descarta el paquete
completo si falta la URL, el `sha256` o el tamaño — un bloqueo a medias es la
app bloqueada mostrando "todavía no hay APK".

### Para levantar el bloqueo

El script no lo hace. Borre a mano los siete campos en la consola de Firestore,
o ponga `MIN_VERSION_CODE` en un valor bajo. **No toque `baseURL`.**

---

## 6. Procedimiento manual (si el script no sirve)

```sh
# 0. tags al día, árbol limpio, keystore.properties presente
git fetch --tags
git status --porcelain          # tiene que salir vacío
ls keystore.properties          # tiene que existir

# 1. subir versionCode y versionName en app/build.gradle.kts, commitear, push

# 2. compilar
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleProdRelease

# 3. VERIFICAR LA FIRMA — el paso que se olvida
APK=app/build/outputs/apk/prod/release/app-prod-release.apk
~/Library/Android/sdk/build-tools/36.1.0/apksigner verify --print-certs "$APK"
#   El DN debe ser  CN=MSP App, OU=Development, O=MSP Company, ...
#   NUNCA           C=US, O=Android, CN=Android Debug
#   La huella SHA-256 debe coincidir con:
keytool -list -v -keystore app/msp-app-release.keystore -alias msp-app-key

# 4. hash y tamaño DEL MISMO archivo
shasum -a 256 "$APK"
stat -f%z "$APK"

# 5. release
cp "$APK" /tmp/app-prod-release-2.17.1.apk
gh release create v2.17.1 /tmp/app-prod-release-2.17.1.apk --generate-notes

# 6. Firestore: en la consola, editar SOLO estos dos campos de
#    config/api_settings — sin tocar baseURL:
#      LATEST_VERSION = 2.17.1          (sin sufijo)
#      APK_URL        = https://github.com/abdimuy/msp-app-kt/releases/download/v2.17.1/app-prod-release-2.17.1.apk

# 7. releer el documento y confirmar que baseURL sigue ahí
```

---

## 7. Las pruebas

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Sin red, sin Firestore, sin Gradle, sin GitHub: todo por dobles. Solo `stdlib`
— no agrega dependencias. Cada clase de prueba cubre una propiedad de
seguridad, y cada una se pone **roja** si se revierte lo que protege:

| Propiedad | Clase | Se pone roja si… |
|---|---|---|
| Nunca se escribe `baseURL` | `BaseUrlNeverWritten` | el payload lo incluye, o la guarda `assert_no_forbidden_fields` deja de guardar |
| Falta `keystore.properties` → aborta | `KeystoreRequired` | `check_keystore` deja de lanzar (y verifica que no llegó a compilar) |
| APK firmado con la llave de debug → aborta | `DebugSignatureRejected` | se quita la detección del DN de debug, o la comparación contra la huella del keystore |
| `MIN_VERSION_*` solo con el flag, y los 7 | `MinVersionOnlyOnDemand` | alguno se escribe sin `--force-update`, o el bloqueo sale incompleto |
| `LATEST_VERSION` sin sufijo | `VersionSuffixStripped` | `base_version_name` deja de recortar |
| El simulacro no escribe nada | `DryRunWritesNothing` | Firestore, el release o `build.gradle.kts` se tocan sin `--apply` |
| La llave debe ser la del entorno | `CredentialsMustMatchEnvironment` | se deja de comparar el `project_id` |
| Colisión de versión | `VersionCollision` | se republica un tag existente, o el `versionCode` no sube |
| El hash describe el archivo subido | `HashDescribesTheUploadedFile` | se sube un archivo distinto del que se midió |
| Árbol limpio y confirmación de producción | `WorkingTreeAndConfirmation` | se publica con el árbol sucio, o producción deja de verificar lo tecleado |

> **Al mutar para comprobar el rojo, corra con `python3 -B` y
> `PYTHONDONTWRITEBYTECODE=1`.** Varias mutaciones (`if options.apply:` →
> `if True:`) producen un archivo del **mismo tamaño** en el **mismo segundo**,
> y Python valida el `.pyc` por `(mtime en segundos, tamaño)`: sin desactivar
> el bytecode se reutiliza el `__pycache__` de la mutación anterior y el
> reporte atribuye el rojo a la prueba equivocada. Ya pasó al escribir esto.

package com.example.msp_app.data.collectionreport

import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import kotlinx.coroutines.tasks.await

/**
 * Implementación real del puerto [UserCyclePort] de `:feature:collectionReport`,
 * provista en el composition root de `:app` (precedente
 * [com.example.msp_app.data.api.FirebaseAuthTokenProvider]): la fuente del ciclo del
 * cobrador —`FECHA_CARGA_INICIAL` y `NOMBRE`— es el documento de usuario en Firestore
 * (`users` where `EMAIL == currentUser.email`), fuera del alcance del módulo feature.
 *
 * **Kill-switch:** NO es `@Singleton` (ver el KDoc del puerto y `CollectionReportDataModule`).
 * Sostiene sesión: cada lectura consulta Firestore por el usuario autenticado vigente. Firestore
 * ya cachea offline, así que la lectura one-shot es barata y respeta el corte de sesión/baseURL.
 *
 * **Contrato de datos (auditado vs [User] / schema Firestore):**
 *  - `FECHA_CARGA_INICIAL` es un `com.google.firebase.Timestamp?` → [Instant] vía
 *    `toDate().toInstant()` (UTC, mismo puente que `AppNavigation`/`WeeklyReportScreen` viejo).
 *  - `NOMBRE` es el nombre del cobrador para el encabezado.
 *
 * **Degradación (no-regresión):** el reporte se alimenta de Room, no de este puerto; el
 * `userData` es una conveniencia de sesión. Por eso ambas lecturas degradan a
 * `null`/`""` ante ausencia de usuario o fallo de Firestore, en vez de propagar y forzar el
 * banner de error del tablero: en Día el reporte sigue renderizando con los datos locales, y en
 * Semana `RangeCalculator.cycleRange(null)` cae al rango de un día (fallback documentado), igual
 * que el `WeeklyReportScreen` viejo caía a `now()` cuando `FECHA_CARGA_INICIAL` era nula.
 *
 * [fetchUser] es inyectable **solo para test** (fakes-only, sin MockK); en producción usa el
 * usuario autenticado de Firebase + Firestore.
 */
class FirebaseUserCycleAdapter(
    private val fetchUser: suspend () -> User? = ::fetchCurrentUserFromFirestore
) : UserCyclePort {

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Firestore puede fallar con cualquier excepción; se degrada.
    override suspend fun fechaCargaInicial(): Instant? = try {
        fetchUser()?.FECHA_CARGA_INICIAL?.toDate()?.toInstant()
    } catch (failure: Exception) {
        null
    }

    @Suppress("TooGenericExceptionCaught") // idem: la ausencia de nombre no debe tumbar el reporte.
    override suspend fun cobradorNombre(): String = try {
        fetchUser()?.NOMBRE?.takeIf { it.isNotBlank() }.orEmpty()
    } catch (failure: Exception) {
        ""
    }
}

/**
 * Lee el documento del usuario autenticado desde Firestore (`users` where
 * `EMAIL == currentUser.email`). Devuelve `null` si no hay sesión o no existe el documento —
 * misma forma de resolución que [com.example.msp_app.features.auth.viewModels.AuthViewModel] y
 * [com.example.msp_app.data.repository.UsersRepository], pero como lectura suspend one-shot.
 */
private suspend fun fetchCurrentUserFromFirestore(): User? {
    val email = FirebaseAuth.getInstance().currentUser?.email ?: return null
    val snapshot = FirebaseFirestore.getInstance()
        .collection(Constants.USERS_COLLECTION)
        .whereEqualTo("EMAIL", email)
        .get()
        .await()
    val doc = snapshot.documents.firstOrNull() ?: return null
    return doc.toObject(User::class.java)?.copy(ID = doc.id)
}

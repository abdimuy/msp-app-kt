package com.example.msp_app.data.collectionreport

import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.collectionreport.domain.port.CycleStart
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
 *  - `FECHA_CARGA_INICIAL` es un `com.google.firebase.Timestamp?` → `java.time.Instant` vía
 *    `toDate().toInstant()` (UTC, mismo puente que `AppNavigation`/`WeeklyReportScreen` viejo).
 *  - `NOMBRE` es el nombre del cobrador para el encabezado.
 *
 * **Degradación (defecto D5 — lo que cambió):** este adapter degradaba CUALQUIER excepción de
 * Firestore a `null`, y ese `null` era indistinguible de "el cobrador no ha iniciado su semana".
 * Aguas abajo, `RangeCalculator.cycleRange(null)` caía al rango de un día y el tablero mostraba
 * $0.00 en la semana con la tabla de pagos llena — sin banner, sin aviso, y sin repararse solo
 * (el reporte es todo `suspend` one-shot: se quedaba así hasta que el usuario salía y volvía a
 * entrar). El fallo se seguía degradando (correcto: el reporte se alimenta de Room, no de este
 * puerto), pero ya NO en silencio ni para siempre: se devuelve [CycleStart.Unavailable], que el
 * ViewModel REINTENTA y, mientras tanto, anuncia en pantalla.
 *
 * Se conserva tal cual la degradación de [cobradorNombre] a `""`: un encabezado sin nombre no
 * falsea ninguna cifra.
 *
 * [fetchUser] es inyectable **solo para test** (fakes-only, sin MockK); en producción usa el
 * usuario autenticado de Firebase + Firestore.
 */
class FirebaseUserCycleAdapter(
    private val fetchUser: suspend () -> User? = ::fetchCurrentUserFromFirestore
) : UserCyclePort {

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Firestore puede fallar con cualquier excepción; se clasifica como reintentable.
    override suspend fun cycleStart(): CycleStart = try {
        val instant = fetchUser()?.FECHA_CARGA_INICIAL?.toDate()?.toInstant()
        if (instant == null) CycleStart.Missing else CycleStart.Known(instant)
    } catch (failure: Exception) {
        CycleStart.Unavailable
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

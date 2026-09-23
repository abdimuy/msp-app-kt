package com.example.msp_app.data.auth

import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.models.auth.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * El usuario autenticado desde Firestore (`users` where `EMAIL == email`), como
 * lectura `suspend` de una sola vez. Su `COBRADOR_ID` es a quién se le atribuye
 * lo que se está escribiendo: **al que está parado frente al cliente**, no al
 * cobrador de la venta (contrato de atribución de `PaymentFactory`).
 *
 * ## Por qué vive aquí y no dentro de un adaptador
 *
 * Porque la comparten el adaptador del **abono** y el de la **ficha**, y
 * dejarla en el del abono —como estuvo un momento, subida a `internal` para
 * que la ficha la alcanzara— le colgaba al camino del dinero una dependencia
 * de compilación de algo que no es dinero. **El pago es soberano justamente
 * para que nada pueda volverlo frágil:** editar el adaptador del abono no
 * puede romper la ficha, ni al revés. Este archivo no depende de ninguno de los
 * dos.
 *
 * `RegistroDeVisitaAdapter` conserva su propia copia privada: unificarla es una
 * limpieza real pero toca el camino de escritura de la visita, y esta tarea no
 * vino a eso.
 */
internal suspend fun usuarioAutenticado(): User? {
    val email = FirebaseAuth.getInstance().currentUser?.email ?: return null
    val snapshot = FirebaseFirestore.getInstance()
        .collection(Constants.USERS_COLLECTION)
        .whereEqualTo("EMAIL", email)
        .get()
        .await()
    val doc = snapshot.documents.firstOrNull() ?: return null
    return doc.toObject(User::class.java)?.copy(ID = doc.id)
}

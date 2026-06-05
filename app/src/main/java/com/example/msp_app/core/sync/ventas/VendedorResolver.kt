package com.example.msp_app.core.sync.ventas

import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.usuarios.EnsureVendedoresRequest
import com.example.msp_app.data.api.services.usuarios.UsuariosApi
import com.example.msp_app.data.api.services.ventas.VendedorDTO
import com.example.msp_app.features.camionetaAssignment.data.repository.CamionetaAssignmentRepository
import java.util.UUID

/**
 * Resolves the vendedores assigned to a camioneta into the shape expected by
 * the Go API's `POST /v2/ventas`.
 *
 * The catalog of vendedores per camioneta lives in Firestore (`users`
 * collection, field `CAMIONETA_ASIGNADA`). Those vendedores may not have a
 * Firebase account (or never logged in), so MSP_USUARIOS may not yet contain
 * a row for them. The Go endpoint `ensure-vendedores-by-email` lazily upserts
 * such rows as VENDEDOR_ONLY users and returns the email → usuario_id mapping.
 *
 * The `id` field on each [VendedorDTO] is a fresh UUID generated client-side:
 * the venta entity carries this snapshot independently of MSP_USUARIOS, so the
 * snapshot rows have their own identifiers.
 */
class VendedorResolver(
    private val camionetaRepo: CamionetaAssignmentRepository = CamionetaAssignmentRepository(),
    private val usuariosApi: UsuariosApi = V2ApiProvider.create(UsuariosApi::class.java)
) {

    /**
     * Resolves all vendedores assigned to [camionetaId] into [VendedorDTO]s.
     *
     * Throws if Firestore or the Go endpoint fails. Returns an empty list if
     * [camionetaId] is null (no camioneta assigned to the current user) or
     * if no vendedores are mapped to that camioneta.
     *
     * Each returned [VendedorDTO]:
     *  - `id`     — fresh UUID for the venta-level snapshot row.
     *  - `usuario_id` — value returned by the Go ensure endpoint.
     *  - `email`/`nombre` — copied from the Firestore user doc as-is.
     */
    suspend fun resolve(camionetaId: Int?): List<VendedorDTO> {
        if (camionetaId == null) return emptyList()

        val usersResult = camionetaRepo.getAllUsers()
        val users = usersResult.getOrElse { throw it }

        val vendedoresEnCamioneta = users.filter { user ->
            user.CAMIONETA_ASIGNADA == camionetaId && user.EMAIL.isNotBlank()
        }
        if (vendedoresEnCamioneta.isEmpty()) return emptyList()

        val emails = vendedoresEnCamioneta.map { it.EMAIL }
        val response = usuariosApi.ensureVendedoresByEmail(
            EnsureVendedoresRequest(emails = emails)
        )

        val emailToUsuarioId = response.vendedores.associateBy({ it.email }, { it.usuario_id })

        return vendedoresEnCamioneta.mapNotNull { user ->
            val usuarioId = emailToUsuarioId[user.EMAIL] ?: return@mapNotNull null
            VendedorDTO(
                id = UUID.randomUUID().toString(),
                usuario_id = usuarioId,
                email = user.EMAIL,
                nombre = user.NOMBRE
            )
        }
    }
}

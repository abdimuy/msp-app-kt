package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.garantiav2.GarantiaCatalogoCacheEntity
import com.example.msp_app.core.database.entities.garantiav2.GarantiaComandoPendienteEntity
import com.example.msp_app.core.database.entities.garantiav2.GarantiaFolioSeguidoEntity
import com.example.msp_app.core.database.entities.garantiav2.GarantiaFotoPendienteEntity
import com.example.msp_app.core.database.migrations.MIGRATION_33_34
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-33-34-test.db"
private const val OLD_VERSION = 33
private const val NEW_VERSION = 34

private const val CLIENTE_ID = 9041
private const val CLIENTE_NOMBRE = "José María Peña Núñez"
private const val CLIENTE_NORMALIZADO = "jose maria pena nunez"

private const val AHORA = "2026-11-12T18:00:00Z"

/**
 * Migración 33→34 validada de punta a punta contra los JSON reales (mismo
 * patrón que [Migration32to33Test]): la base se crea desde el `33.json`
 * exportado, se siembra con una fila REAL que el cobrador ya tiene en el
 * teléfono, se aplica la `Migration` REAL y Room valida el esquema resultante
 * contra el `34.json`.
 *
 * Lo que le importa a este archivo, y que ninguna otra prueba cubre:
 *
 * 1. Las cuatro tablas nuevas existen con sus índices (si el `CREATE` de la
 *    migración no coincide con las entidades, `runMigrationsAndValidate`
 *    revienta).
 * 2. Las filas preexistentes sobreviven: la migración es aditiva, no recrea
 *    ni vacía nada.
 * 3. El camino REAL de producción (`AppDatabase.buildDatabase`) encuentra la
 *    ruta 33→34 y los DAO nuevos funcionan sobre la base migrada.
 */
class Migration33to34Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `crea las cuatro tablas nuevas y conserva las filas preexistentes`() {
        seedV33()

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_33_34
        )

        try {
            assertClienteSobrevive(migrada)
            listOf(
                "garantia_folios_seguidos",
                "garantia_comandos_pendientes",
                "garantia_fotos_pendientes",
                "garantia_catalogos_cache"
            ).forEach { tabla -> assertTablaNuevaVacia(migrada, tabla) }
        } finally {
            migrada.close()
        }
    }

    @Test
    fun `abrir por el camino de produccion lleva una base v33 hasta v34`() = runTest {
        seedV33()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbPath = context.getDatabasePath(MIGRATION_DB).path

        val abierta = AppDatabase.buildDatabase(context, dbPath)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(
                "abrir por el camino real de produccion debe terminar en v34",
                NEW_VERSION,
                abierta.openHelper.readableDatabase.version
            )
            val comandoId = assertFoliosYComandosFuncionan(abierta)
            assertFotosFuncionan(abierta, comandoId)
            assertCatalogoFunciona(abierta)
        } finally {
            abierta.close()
        }
    }

    private fun assertClienteSobrevive(migrada: SupportSQLiteDatabase) {
        migrada.query(
            "SELECT NOMBRE, NOMBRE_NORMALIZADO FROM cliente WHERE CLIENTE_ID = ?",
            arrayOf(CLIENTE_ID)
        ).use { cursor ->
            assertTrue("el cliente sembrado en v33 debe seguir ahí", cursor.moveToFirst())
            assertEquals(CLIENTE_NOMBRE, cursor.getString(0))
            assertEquals(CLIENTE_NORMALIZADO, cursor.getString(1))
        }
    }

    private fun assertTablaNuevaVacia(migrada: SupportSQLiteDatabase, tabla: String) {
        migrada.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tabla)
        ).use { cursor ->
            assertTrue("falta la tabla $tabla del tramo 33→34", cursor.moveToFirst())
        }
        migrada.query("SELECT COUNT(*) FROM `$tabla`").use { cursor ->
            cursor.moveToFirst()
            assertEquals(
                "la tabla $tabla nace vacía: la migración no inventa filas",
                0,
                cursor.getInt(0)
            )
        }
    }

    /** Devuelve el id local del comando, que la foto referencia. */
    private suspend fun assertFoliosYComandosFuncionan(abierta: AppDatabase): Long {
        val folio = GarantiaFolioSeguidoEntity(
            ID = "folio-local-1",
            ORIGEN = "piso",
            ESTADO_LOCAL = "pendiente_envio",
            ABIERTO_EN = AHORA,
            ACTUALIZADO_EN = AHORA
        )
        abierta.garantiaFolioDao().guardar(folio)
        assertEquals("folio-local-1", abierta.garantiaFolioDao().todos().single().ID)
        assertNull(abierta.garantiaFolioDao().porId(folio.ID)?.FOLIO_ID)

        val comando = GarantiaComandoPendienteEntity(
            CLAVE = "clave-comando-1",
            OPERACION = "abrir_folio",
            METODO = "POST",
            RUTA = "v2/garantias",
            CUERPO = """{"origen":"piso"}""",
            CONTENT_TYPE = "application/json",
            DEVICE_CREATED_AT = AHORA,
            ESTADO = "pendiente",
            FOLIO_REF = folio.ID,
            CREADO_EN = AHORA,
            ACTUALIZADO_EN = AHORA
        )
        val comandoId = abierta.garantiaComandoDao().insertar(comando)
        assertEquals(1, abierta.garantiaComandoDao().pendientesCount())
        assertEquals(comando.CLAVE, abierta.garantiaComandoDao().pendientes().single().CLAVE)

        abierta.garantiaComandoDao().resolverFolio(
            folioRef = folio.ID,
            folioId = "folio-servidor-1",
            ahora = "2026-11-12T18:05:00Z"
        )
        assertEquals(
            "el id del servidor se propaga a los comandos que esperaban",
            "folio-servidor-1",
            abierta.garantiaComandoDao().porClave(comando.CLAVE)?.FOLIO_ID
        )

        abierta.garantiaComandoDao().marcarEnviado(comandoId, "2026-11-12T18:06:00Z")
        assertEquals(0, abierta.garantiaComandoDao().pendientesCount())
        return comandoId
    }

    private suspend fun assertFotosFuncionan(abierta: AppDatabase, comandoId: Long) {
        val foto = GarantiaFotoPendienteEntity(
            CLAVE = "clave-foto-1",
            COMANDO_ID = comandoId,
            FOLIO_REF = "folio-local-1",
            EVENTO_REF = "evento-local-1",
            RUTA_ARCHIVO = "/tmp/foto.png",
            MIME = "image/png",
            ESTADO = "pendiente",
            CREADO_EN = AHORA,
            ACTUALIZADO_EN = AHORA
        )
        abierta.garantiaFotoDao().guardar(foto)
        assertEquals(1, abierta.garantiaFotoDao().pendientesCount())
        assertTrue(
            "una foto sin evento resuelto no puede subir todavía",
            abierta.garantiaFotoDao().listasParaSubir().isEmpty()
        )
    }

    private suspend fun assertCatalogoFunciona(abierta: AppDatabase) {
        abierta.garantiaCatalogoDao().guardar(
            GarantiaCatalogoCacheEntity(
                CUERPO = """{"acciones":[]}""",
                ACTUALIZADO_EN = AHORA
            )
        )
        assertEquals(
            """{"acciones":[]}""",
            abierta.garantiaCatalogoDao().leer()?.CUERPO
        )
    }

    private fun seedV33() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            insertarCliente(db)
        }
    }

    private fun insertarCliente(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO cliente " +
                "(CLIENTE_ID, NOMBRE, ESTATUS, CAUSA_SUSP, NOMBRE_NORMALIZADO) " +
                "VALUES (?, ?, 'A', NULL, ?)",
            arrayOf(CLIENTE_ID, CLIENTE_NOMBRE, CLIENTE_NORMALIZADO)
        )
    }
}

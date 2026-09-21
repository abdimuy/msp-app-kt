package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_32_33
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-32-33-test.db"
private const val OLD_VERSION = 32
private const val NEW_VERSION = 33

private const val CLIENTE_ACENTOS_ID = 9041
private const val CLIENTE_ACENTOS_NOMBRE = "José María Peña Núñez"

private const val CLIENTE_ESPACIOS_ID = 9042
private const val CLIENTE_ESPACIOS_NOMBRE = "  Juan   Pérez   García  "

private const val CLIENTE_SIMPLE_ID = 9043
private const val CLIENTE_SIMPLE_NOMBRE = "Roberto Sanchez"

/**
 * Migración 32→33 validada de punta a punta contra los JSON reales (mismo
 * patrón que [Migration31to32Test]): la base se crea desde el `32.json`
 * exportado, se siembra con clientes REALES del padrón — con acentos,
 * mayúsculas mezcladas y espacios de más, exactamente lo que trae Microsip —,
 * se aplica la `Migration` REAL, y Room valida el esquema resultante contra
 * el `33.json`.
 *
 * Agrega `NOMBRE_NORMALIZADO` a `cliente` para el buscador de la Nueva Venta
 * (ver el KDoc de `MIGRATION_32_33` para la numeración y la aproximación en
 * SQL). Lo que le importa a ESTE archivo, y que ninguna otra prueba cubre:
 * que la columna quede POBLADA para las filas que YA EXISTÍAN antes de
 * migrar, no vacía a la espera del siguiente sync — si esto fallara, el
 * buscador de un teléfono recién actualizado no encontraría a NADIE hasta la
 * próxima sincronización de clientes.
 */
class Migration32to33Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `NOMBRE_NORMALIZADO existe y queda poblado para clientes preexistentes con acentos`() {
        seedClientesPreexistentes()

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_32_33
        )

        assertNombreNormalizado(
            migrated,
            CLIENTE_ACENTOS_ID,
            esperado = "jose maria pena nunez"
        )
        migrated.close()
    }

    @Test
    fun `NOMBRE_NORMALIZADO colapsa los espacios de mas de un cliente preexistente`() {
        seedClientesPreexistentes()

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_32_33
        )

        assertNombreNormalizado(
            migrated,
            CLIENTE_ESPACIOS_ID,
            esperado = "juan perez garcia"
        )
        migrated.close()
    }

    @Test
    fun `NOMBRE_NORMALIZADO pliega mayusculas de un cliente preexistente sin acentos`() {
        seedClientesPreexistentes()

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_32_33
        )

        assertNombreNormalizado(
            migrated,
            CLIENTE_SIMPLE_ID,
            esperado = "roberto sanchez"
        )
        migrated.close()
    }

    @Test
    fun `el cliente preexistente conserva su NOMBRE original tal cual, con acentos y mayusculas`() {
        seedClientesPreexistentes()

        val migrated = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_32_33
        )

        migrated.query(
            "SELECT NOMBRE FROM cliente WHERE CLIENTE_ID = ?",
            arrayOf(CLIENTE_ACENTOS_ID)
        ).use { cursor ->
            assertTrue("el cliente sembrado antes de migrar debe seguir ahi", cursor.moveToFirst())
            assertEquals(
                "normalizar para buscar nunca debe normalizar el NOMBRE que se muestra",
                CLIENTE_ACENTOS_NOMBRE,
                cursor.getString(0)
            )
        }
        migrated.close()
    }

    /**
     * El otro lado de la misma regla: una instalación NUEVA no pasa por la
     * migración —usa el `CREATE TABLE` que Room genera de la entidad— y la
     * columna también tiene que existir, con `NOT NULL`.
     */
    @Test
    fun `una instalacion nueva tambien declara NOMBRE_NORMALIZADO NOT NULL`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fresh = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val notNull = try {
            leerNotNullDeColumnaCliente(fresh.openHelper.readableDatabase, "NOMBRE_NORMALIZADO")
        } finally {
            fresh.close()
        }

        assertEquals(
            "NOMBRE_NORMALIZADO debe existir y ser NOT NULL en una instalacion nueva",
            1,
            notNull
        )
    }

    /** `PRAGMA table_info(cliente)` reducido al `notnull` de una columna, o `null` si no existe. */
    private fun leerNotNullDeColumnaCliente(db: SupportSQLiteDatabase, nombre: String): Int? {
        db.query("PRAGMA table_info(cliente)").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) != nombre) continue
                return cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
            }
        }
        return null
    }

    /**
     * Cierra el mismo hallazgo que ya cubrió `MIGRATION_30_31` y
     * `MIGRATION_31_32`: nada probaría que `MIGRATION_32_33` está REGISTRADA
     * en `AppDatabase.buildDatabase` si sólo se le pasara la migración a mano
     * a `MigrationTestHelper`. Esta prueba abre por el camino REAL de
     * producción — hoy la ÚLTIMA versión registrada, así que aquí sí se
     * compara por igualdad.
     */
    @Test
    fun `MIGRATION_32_33 esta registrada en la configuracion real que usa produccion`() {
        seedClientesPreexistentes()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbPath = context.getDatabasePath(MIGRATION_DB).path

        val opened = AppDatabase.buildDatabase(context, dbPath)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(
                "abrir por el camino real de produccion debe terminar en v33",
                NEW_VERSION,
                opened.openHelper.readableDatabase.version
            )

            opened.openHelper.readableDatabase.query(
                "SELECT NOMBRE_NORMALIZADO FROM cliente WHERE CLIENTE_ID = ?",
                arrayOf(CLIENTE_ACENTOS_ID)
            ).use { cursor ->
                assertTrue(
                    "el cliente sembrado en v32 debe sobrevivir a la apertura real",
                    cursor.moveToFirst()
                )
                assertEquals("jose maria pena nunez", cursor.getString(0))
            }
        } finally {
            opened.close()
        }
    }

    private fun assertNombreNormalizado(
        db: SupportSQLiteDatabase,
        clienteId: Int,
        esperado: String
    ) {
        db.query(
            "SELECT NOMBRE_NORMALIZADO FROM cliente WHERE CLIENTE_ID = ?",
            arrayOf(clienteId)
        ).use { cursor ->
            assertTrue("el cliente sembrado antes de migrar debe seguir ahi", cursor.moveToFirst())
            assertEquals(esperado, cursor.getString(0))
        }
    }

    private fun seedClientesPreexistentes() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            insertarCliente(db, CLIENTE_ACENTOS_ID, CLIENTE_ACENTOS_NOMBRE)
            insertarCliente(db, CLIENTE_ESPACIOS_ID, CLIENTE_ESPACIOS_NOMBRE)
            insertarCliente(db, CLIENTE_SIMPLE_ID, CLIENTE_SIMPLE_NOMBRE)
        }
    }

    private fun insertarCliente(db: SupportSQLiteDatabase, clienteId: Int, nombre: String) {
        db.execSQL(
            "INSERT INTO cliente (CLIENTE_ID, NOMBRE, ESTATUS, CAUSA_SUSP) VALUES (?, ?, 'A', NULL)",
            arrayOf(clienteId, nombre)
        )
    }
}

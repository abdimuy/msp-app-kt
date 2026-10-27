package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.migrations.MIGRATION_34_35
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val MIGRATION_DB = "migration-34-35-test.db"
private const val OLD_VERSION = 34
private const val NEW_VERSION = 35
private const val ULTIMA_VERSION = 38

/**
 * Spec visitas §4.2-7: la 34→35 agrega `Visit.CONFIRMADA_EN` y
 * `Visit.RESGUARDADA_EN`, nulas, y no reescribe ninguna fila.
 *
 * Lo que importa (Review Focus 2 del plan P1): una visita legada ya soltada
 * (`GUARDADO_EN_MICROSIP = 1`) sale de la migración con `CONFIRMADA_EN` nulo —
 * o sea, **sin prueba del servidor** — para que la reconciliación inversa la
 * confirme o la devuelva a pendiente. Cada columna preexistente de cada tabla
 * queda byte-idéntica (`rowid`, `typeof`, `quote`).
 */
class Migration34to35Test : RobolectricTestBase() {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `v35 agrega CONFIRMADA_EN y RESGUARDADA_EN nulas en pendientes y legadas entregadas`() {
        migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { sembrar(it) }

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_34_35
        )
        val filas = migrada.query(
            "SELECT ID, GUARDADO_EN_MICROSIP, CONFIRMADA_EN, RESGUARDADA_EN FROM Visit ORDER BY ID"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(listOf(c.getString(0), c.getInt(1), c.isNull(2), c.isNull(3)))
                }
            }
        }
        migrada.close()

        assertEquals(
            "pendientes y legadas entregadas conservan su estado y estrenan las dos marcas en NULL",
            listOf(
                listOf(PENDIENTE_CON_FOTO, 0, true, true),
                listOf(PENDIENTE, 0, true, true),
                listOf(LEGADA_ENTREGADA, 1, true, true),
                listOf(LEGADA_ENTREGADA_CON_PROMESA, 1, true, true)
            ),
            filas
        )
    }

    @Test
    fun `ninguna fila preexistente cambia y solo Visit gana dos columnas`() {
        val antes = migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            sembrar(db)
            val tablas = tablasDeUsuario(db)
            Triple(
                tablas.associateWith { columnas(db, it) },
                tablas.associateWith { volcado(db, it, columnas(db, it)) },
                esquema(db)
            )
        }
        val (columnasAntes, filasAntes, sqlAntes) = antes
        // Control positivo: el volcado ve lo sembrado en CADA tabla de v34. Si
        // una versión futura agrega una tabla y nadie la siembra, esto truena.
        assertEquals(FILAS_SEMBRADAS, filasAntes.mapValues { it.value.size })

        val migrada = migrationTestHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            NEW_VERSION,
            true,
            MIGRATION_34_35
        )
        val filasDespues = columnasAntes.mapValues { (tabla, cols) ->
            volcado(
                migrada,
                tabla,
                cols
            )
        }
        val ganadas = columnasAntes
            .mapValues { (tabla, cols) -> columnas(migrada, tabla) - cols.toSet() }
            .filterValues { it.isNotEmpty() }
        val sqlDespues = esquema(migrada)
        val tablasDespues = tablasDeUsuario(migrada)
        migrada.close()

        assertEquals(filasAntes, filasDespues)
        assertEquals("ninguna tabla nueva", columnasAntes.keys.toList(), tablasDespues)
        assertEquals(mapOf("Visit" to listOf("CONFIRMADA_EN", "RESGUARDADA_EN")), ganadas)
        assertEquals(
            "el SQL de toda tabla e índice distinto de Visit queda igual",
            sqlAntes.filterKeys { it != "Visit" },
            sqlDespues.filterKeys { it != "Visit" }
        )
    }

    /**
     * Camino real de producción: `buildDatabase` debe tener registrada la 34→35.
     * La cadena sigue hasta la última versión (v37 crea `capturas_en_curso`,
     * E-APP-084, y v38 las tablas de P3), así que se comparan las columnas que la fila ya tenía en v34.
     */
    @Test
    fun `MIGRATION_34_35 esta registrada en la configuracion real que usa produccion`() {
        val antes = migrationTestHelper.createDatabase(MIGRATION_DB, OLD_VERSION).use { db ->
            sembrar(db)
            volcado(db, "Visit", columnas(db, "Visit"))
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val abierta = AppDatabase.buildDatabase(context, context.getDatabasePath(MIGRATION_DB).path)
            .allowMainThreadQueries()
            .build()
        try {
            val db = abierta.openHelper.readableDatabase
            assertEquals(ULTIMA_VERSION, db.version)
            assertEquals(antes, volcado(db, "Visit", antes.first().keys.toList()))
            db.query(
                "SELECT COUNT(*) FROM Visit WHERE CONFIRMADA_EN IS NULL AND RESGUARDADA_EN IS NULL"
            )
                .use { c ->
                    c.moveToFirst()
                    assertEquals(4, c.getInt(0))
                }
        } finally {
            abierta.close()
        }
    }

    // ─── siembra con forma real ────────────────────────────────────────────────

    private fun sembrar(db: SupportSQLiteDatabase) {
        sembrarVisitasYPagos(db)
        sembrarNegocio(db)
    }

    /** Inserta una fila por nombre de columna. */
    private fun fila(
        db: SupportSQLiteDatabase,
        tabla: String,
        vararg columnas: Pair<String, Any?>
    ) {
        db.execSQL(
            "INSERT INTO `$tabla` (${columnas.joinToString { "`${it.first}`" }}) " +
                "VALUES (${columnas.joinToString { "?" }})",
            columnas.map { it.second }.toTypedArray()
        )
    }

    /** Una fila con forma real en cada tabla de v34 que no es Visit/Payment/visita_imagenes. */
    private fun sembrarNegocio(db: SupportSQLiteDatabase) {
        sembrarVentaMicrosip(db)
        sembrarGarantia(db)
        sembrarInventario(db)
        sembrarVentaLocal(db)
        sembrarClienteYCobranza(db)
    }

    private fun sembrarVentaMicrosip(db: SupportSQLiteDatabase) {
        fila(
            db, "sales",
            "DOCTO_CC_ACR_ID" to 512345, "DOCTO_CC_ID" to 8800120, "FOLIO" to "B-041237",
            "CLIENTE_ID" to 11486, "APLICADO" to "S", "COBRADOR_ID" to 2991009,
            "CLIENTE" to "Mendoza Torres, Ana", "ZONA_CLIENTE_ID" to 21552, "LIMITE_CREDITO" to 15000.0,
            "NOTAS" to "Casa azul junto a la tienda", "ZONA_NOMBRE" to "Tehuacán Centro",
            "IMPORTE_PAGO_PROMEDIO" to 175.5, "TOTAL_IMPORTE" to 6200.0, "NUM_IMPORTES" to 31,
            "FECHA" to "2026-03-02T00:00:00Z", "PARCIALIDAD" to 200, "ENGANCHE" to 500.0,
            "TIEMPO_A_CORTO_PLAZOMESES" to 3, "MONTO_A_CORTO_PLAZO" to 9800.0,
            "VENDEDOR_1" to "Pérez Núñez, José María", "VENDEDOR_2" to "", "VENDEDOR_3" to "",
            "PRECIO_TOTAL" to 12400.0, "IMPTE_REST" to 6200.0, "SALDO_REST" to 5700.25,
            "FECHA_ULT_PAGO" to "2026-10-14T15:00:00Z", "CALLE" to "Av. Reforma 1204",
            "CIUDAD" to "Tehuacán", "ESTADO" to "Puebla", "TELEFONO" to "2381234567",
            "NOMBRE_COBRADOR" to "López García, Minerva", "ESTADO_COBRANZA" to "PENDIENTE",
            "DIA_COBRANZA" to "JUEVES", "DIA_TEMPORAL_COBRANZA" to "", "PRECIO_DE_CONTADO" to 8900.0,
            "AVAL_O_RESPONSABLE" to "Torres Ruiz, Rosa", "FREC_PAGO" to "SEMANAL"
        )
        fila(
            db, "products",
            "DOCTO_PV_DET_ID" to 7700451, "DOCTO_PV_ID" to 7700450, "FOLIO" to "B-041237",
            "ARTICULO_ID" to 30211, "ARTICULO" to "Recámara Matrimonial Lisboa", "CANTIDAD" to 1,
            "PRECIO_UNITARIO_IMPTO" to 12400.0, "PRECIO_TOTAL_NETO" to 12400.0, "POSICION" to 1
        )
    }

    private fun sembrarGarantia(db: SupportSQLiteDatabase) {
        fila(
            db, "garantias",
            "ID" to 1, "EXTERNAL_ID" to "6d1f0c2e-8a4b-4f7e-9c3d-2b1a0f9e8d7c", "DOCTO_CC_ID" to 8800120,
            "ESTADO" to "SOLICITADA", "DESCRIPCION_FALLA" to "La puerta del ropero no cierra",
            "OBSERVACIONES" to null, "UPLOADED" to 0, "FECHA_SOLICITUD" to "2026-10-12T16:20:00Z",
            "NOMBRE_CLIENTE" to "Mendoza Torres, Ana", "NOMBRE_PRODUCTO" to "Recámara Matrimonial Lisboa"
        )
        fila(
            db,
            "garantia_imagenes",
            "ID" to "g-img-1",
            "GARANTIA_ID" to "6d1f0c2e-8a4b-4f7e-9c3d-2b1a0f9e8d7c",
            "IMG_PATH" to "/data/garantias/puerta.jpg",
            "IMG_MIME" to "image/jpeg",
            "IMG_DESC" to "Bisagra rota",
            "FECHA_SUBIDA" to "2026-10-12T16:21:00Z"
        )
        fila(
            db,
            "garantia_eventos",
            "ID" to "g-ev-1",
            "GARANTIA_ID" to "6d1f0c2e-8a4b-4f7e-9c3d-2b1a0f9e8d7c",
            "TIPO_EVENTO" to "SOLICITUD",
            "FECHA_EVENTO" to "2026-10-12T16:20:00Z",
            "COMENTARIO" to "Cliente pide revisión",
            "ENVIADO" to 0
        )
    }

    private fun sembrarInventario(db: SupportSQLiteDatabase) {
        fila(
            db,
            "product_inventory",
            "ARTICULO_ID" to 30211,
            "ARTICULO" to "Recámara Matrimonial Lisboa",
            "EXISTENCIAS" to 4,
            "LINEA_ARTICULO_ID" to 12,
            "LINEA_ARTICULO" to "Recámaras",
            "PRECIOS" to "Precio de lista:12400.0,Precio 3 meses:9800.0,Precio contado:8900.0"
        )
        fila(
            db,
            "products_inventory_images",
            "IMAGEN_ID" to 88001,
            "ARTICULO_ID" to 30211,
            "RUTA_LOCAL" to "/data/inventario/30211-1.jpg"
        )
    }

    private fun sembrarVentaLocal(db: SupportSQLiteDatabase) {
        fila(
            db, "local_sale",
            "LOCAL_SALE_ID" to LOCAL_SALE, "NOMBRE_CLIENTE" to "Ramírez Ortiz, Fernando",
            "FECHA_VENTA" to "2026-10-16T12:30:00Z", "LATITUD" to 18.4616, "LONGITUD" to -97.3928,
            "DIRECCION" to "Calle 5 Sur 302", "PARCIALIDAD" to 250.0, "ENGANCHE" to 1000.0,
            "TELEFONO" to "2389876543", "FREC_PAGO" to "SEMANAL", "AVAL_O_RESPONSABLE" to null,
            "NOTA" to "Entregar por la tarde", "DIA_COBRANZA" to "SABADO", "PRECIO_TOTAL" to 15800.0,
            "TIEMPO_A_CORTO_PLAZOMESES" to 3, "MONTO_A_CORTO_PLAZO" to 12600.0, "MONTO_DE_CONTADO" to 11200.0,
            "ENVIADO" to 0, "NUMERO" to "302", "COLONIA" to "El Carmen", "POBLACION" to "Tehuacán",
            "CIUDAD" to "Tehuacán", "TIPO_VENTA" to "CREDITO", "ZONA_CLIENTE_ID" to 21553,
            "ZONA_CLIENTE" to "Tehuacán Sur", "CLIENTE_ID" to null, "IDEMPOTENCY_KEY" to "idem-ls-1",
            "REVISION" to 0, "CORRECCION_NO_ENVIADA" to 0, "CORRECCION_REMOTA_PENDIENTE" to 0
        )
        fila(
            db,
            "sale_image",
            "LOCAL_SALE_IMAGE_ID" to "ls-img-1",
            "LOCAL_SALE_ID" to LOCAL_SALE,
            "IMAGE_URI" to "/data/ventas/ine-frente.jpg",
            "FECHA_SUBIDA" to "2026-10-16T12:31:00Z",
            "SERVER_UUID" to null
        )
        fila(
            db, "local_sale_combos",
            "COMBO_ID" to "combo-1", "LOCAL_SALE_ID" to LOCAL_SALE, "NOMBRE_COMBO" to "Sala y comedor",
            "PRECIO_LISTA" to 15800.0, "PRECIO_CORTO_PLAZO" to 12600.0, "PRECIO_CONTADO" to 11200.0,
            "SERVER_UUID" to null
        )
        fila(
            db, "local_sale_products",
            "LOCAL_SALE_ID" to LOCAL_SALE, "ARTICULO_ID" to 30455, "ARTICULO" to "Sala Venecia 3-2-1",
            "CANTIDAD" to 1, "PRECIO_LISTA" to 9800.0, "PRECIO_CORTO_PLAZO" to 7800.0,
            "PRECIO_CONTADO" to 6900.0, "COMBO_ID" to "combo-1", "SERVER_UUID" to null
        )
    }

    private fun sembrarClienteYCobranza(db: SupportSQLiteDatabase) {
        fila(
            db,
            "cliente",
            "CLIENTE_ID" to 11486,
            "NOMBRE" to "Mendoza Torres, Ana",
            "ESTATUS" to "V",
            "CAUSA_SUSP" to null,
            "NOMBRE_NORMALIZADO" to "mendoza torres ana"
        )
        fila(
            db, "cobranza_sync_state",
            "RESOURCE" to "pagos", "ZONA_CLIENTE_ID" to 21552, "CURSOR" to "2026-10-16T18:00:00Z|8800124",
            "LAST_SYNCED_AT" to "2026-10-16T18:00:05Z", "LAST_ERROR" to null, "EPOCH" to 3, "AFTER_ID" to 8800124
        )
        fila(
            db, "pago_imagenes",
            "ID" to "img-p-1", "PAGO_ID" to "7f3c2a10-5b1e-4c7a-9d2f-1a2b3c4d5e6f", "URI" to "/data/comprobante.jpg",
            "MIME" to "image/jpeg", "DESCRIPCION" to null, "ORDEN" to 0, "CREADA_EN" to "2026-10-15T17:42:09Z",
            "SUBIDA_EN" to null
        )
        fila(
            db, "visita_recomendaciones",
            "ID" to "rec-1", "CLIENTE_ID" to 11486, "VENTA_ID" to 512345, "COBRADOR_ID" to 2991009,
            "GENERADA_EN" to "2026-10-15T07:00:00Z", "POSICION" to 1, "MOTIVO" to "promesa_vencida",
            "ALGORITMO" to "v1", "GRUPO" to "tratamiento", "VISITA_ID" to PENDIENTE
        )
        fila(
            db,
            "cliente_ficha",
            "CLIENTE_ID" to 11486,
            "NOTA" to "Paga los jueves después de las 5",
            "ACTUALIZADA_EN" to "2026-10-10T19:00:00Z",
            "COBRADOR_ID" to 2991009
        )
        fila(
            db,
            "cliente_ficha_senales",
            "CLIENTE_ID" to 11486,
            "SENAL" to "PERRO_BRAVO",
            "ACTUALIZADA_EN" to "2026-10-10T19:00:00Z"
        )
        fila(
            db, "envio_diagnostico",
            "TIPO" to "visita", "FILA_ID" to PENDIENTE, "ULTIMO_ERROR_CODIGO" to "timeout",
            "ULTIMO_ERROR_MENSAJE" to "sin respuesta", "ULTIMO_ERROR_HTTP" to null, "INTENTOS" to 2,
            "ULTIMO_INTENTO_AT" to "2026-10-16T11:06:00Z", "ULTIMO_EXITO_AT" to null, "REPORTADO_CODIGO" to "timeout"
        )
    }

    private fun sembrarVisitasYPagos(db: SupportSQLiteDatabase) {
        val visitas = listOf(
            arrayOf<Any?>(
                PENDIENTE, 11486, "Mendoza Torres, Ana", 2991009, "2026-10-15T18:00:00Z", 0,
                19.4326077, -99.133208, "No estaba, vuelvo el jueves", "SIN_PAGO", 21552, 512345, 0,
                512345, "2026-10-17", 15000L, "2026-10-17", "10:30"
            ),
            arrayOf<Any?>(
                PENDIENTE_CON_FOTO, 22001, "Ramírez Ortiz, Fernando", 2991010,
                "2026-10-16T11:05:12.345Z", 0, 0.0, 0.0, null, "PIDE_TIEMPO", 21553, 600001, 0,
                null, null, null, null, null
            ),
            // Legada: soltada por el envío de hoy sin prueba del servidor.
            arrayOf<Any?>(
                LEGADA_ENTREGADA, 11486, "Mendoza Torres, Ana", 2991009, "2026-10-13T08:00:00Z",
                87327, 19.4, -99.1, "Ñandú — acentos áéíóú", "CON_PAGO", 21552, 512345, 1,
                null, null, null, null, null
            ),
            arrayOf<Any?>(
                LEGADA_ENTREGADA_CON_PROMESA, 30144, "Domínguez Reyes, Efraín", 7,
                "2026-10-10T17:30:00Z", 157, 19.043415, -98.198234, null, "PROMESA", 21, 700002, 1,
                700002, "2026-10-24", 25050L, null, null
            )
        )
        visitas.forEach {
            db.execSQL(
                "INSERT INTO Visit (ID, CLIENTE_ID, COBRADOR, COBRADOR_ID, FECHA, FORMA_COBRO_ID, LAT, LNG, " +
                    "NOTA, TIPO_VISITA, ZONA_CLIENTE_ID, IMPTE_DOCTO_CC_ID, GUARDADO_EN_MICROSIP, " +
                    "PROMESA_VENTA_ID, PROMESA_FECHA, PROMESA_MONTO_CENTAVOS, CITA_FECHA, CITA_HORA) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                it
            )
        }
        db.execSQL(
            "INSERT INTO visita_imagenes (ID, VISITA_ID, URI, MIME, DESCRIPCION, ORDEN, CREADA_EN, SUBIDA_EN) " +
                "VALUES ('img-v-1', '$PENDIENTE_CON_FOTO', '/data/fachada.jpg', 'image/jpeg', 'Fachada', 0, " +
                "'2026-10-16T11:05:13Z', NULL)"
        )
        listOf(
            arrayOf<Any?>(
                "7f3c2a10-5b1e-4c7a-9d2f-1a2b3c4d5e6f", "Mendoza Torres, Ana", 512345, -1,
                "2026-10-15T17:42:08.123Z", 0, 150.0, 19.4326077, -99.133208, 11486, 2991009, 157, 21552,
                "López García, Minerva", null
            ),
            arrayOf<Any?>(
                "0b9d8e7f-6a5b-4c3d-8e2f-9a8b7c6d5e4f", "Mendoza Torres, Ana", 512345, 8800123,
                "2026-10-14T15:00:00Z", 1, 200.5, 0.0, 0.0, 11486, 2991009, 158, 21552,
                "López García, Minerva", null
            )
        ).forEach {
            db.execSQL(
                "INSERT INTO Payment (ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO, " +
                    "GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID, COBRADOR_ID, FORMA_COBRO_ID, " +
                    "ZONA_CLIENTE_ID, NOMBRE_CLIENTE, PAGO_RECIBIDO_ID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                it
            )
        }
    }

    // ─── volcado byte a byte ───────────────────────────────────────────────────

    private fun tablasDeUsuario(db: SupportSQLiteDatabase): List<String> = db.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name"
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private fun esquema(db: SupportSQLiteDatabase): Map<String, String?> = db.query(
        "SELECT name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name != 'room_master_table'"
    ).use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) } }

    private fun columnas(db: SupportSQLiteDatabase, tabla: String): List<String> =
        db.query("PRAGMA table_info(`$tabla`)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    /** Cada fila como columna → `typeof:quote`, más `rowid`, en orden de `rowid`. */
    private fun volcado(
        db: SupportSQLiteDatabase,
        tabla: String,
        cols: List<String>
    ): List<Map<String, String>> {
        val datos = cols.filter { it != "rowid" }
        val select = (listOf("rowid") + datos.map { "typeof(`$it`) || ':' || quote(`$it`)" })
            .joinToString()
        return db.query("SELECT $select FROM `$tabla` ORDER BY rowid").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        buildMap {
                            put("rowid", c.getString(0))
                            datos.forEachIndexed { i, col -> put(col, c.getString(i + 1)) }
                        }
                    )
                }
            }
        }
    }

    private companion object {
        const val PENDIENTE = "a1b2c3d4-0000-3000-8000-000000000001"
        const val PENDIENTE_CON_FOTO = "a1b2c3d4-0000-3000-8000-000000000000"
        const val LEGADA_ENTREGADA = "a1b2c3d4-0000-3000-8000-000000000002"
        const val LEGADA_ENTREGADA_CON_PROMESA = "a1b2c3d4-0000-3000-8000-000000000003"
        const val LOCAL_SALE = "3e9b7c1a-2d4f-4a6b-8c0e-1f2a3b4c5d6e"

        /** Las 21 tablas de v34 (`34.json`), cada una con lo que [sembrar] le pone. */
        val FILAS_SEMBRADAS = mapOf(
            "Payment" to 2, "Visit" to 4, "cliente" to 1, "cliente_ficha" to 1,
            "cliente_ficha_senales" to 1, "cobranza_sync_state" to 1, "envio_diagnostico" to 1,
            "garantia_eventos" to 1, "garantia_imagenes" to 1, "garantias" to 1, "local_sale" to 1,
            "local_sale_combos" to 1, "local_sale_products" to 1, "pago_imagenes" to 1,
            "product_inventory" to 1, "products" to 1, "products_inventory_images" to 1, "sale_image" to 1,
            "sales" to 1, "visita_imagenes" to 1, "visita_recomendaciones" to 1
        )
    }
}

package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * La ÚNICA migración del plan `pagos-y-visitas` (Task 26). Cubre la
 * persistencia que necesitan las Tasks 19, 22, 23 y 24; ninguna de ellas
 * define migraciones propias.
 *
 * **Estrictamente aditiva.** Cinco columnas NUEVAS y NULLABLE sobre `Visit`, y
 * cinco tablas NUEVAS. Ni un `DROP`, ni un `ALTER COLUMN`, ni una tabla
 * recreada, ni un índice existente modificado: **ninguna sentencia toca una
 * columna, un índice o una fila que ya existan**. Un teléfono en v29 con tres
 * años de pagos llega a v30 con exactamente las mismas filas — `Payment` ni
 * siquiera aparece en el lado izquierdo de una sentencia. Esa es la razón por
 * la que la constraint "Room es INMUTABLE" admite esta excepción: reescribir
 * el esquema corrompería datos en campo, y aquí no se reescribe nada.
 *
 * ## Qué agrega y por qué (las seis filas del brief)
 *
 * 1. **Promesa estructurada** (`PROMESA_VENTA_ID`, `PROMESA_FECHA`,
 *    `PROMESA_MONTO_CENTAVOS` en `Visit`). Columnas y no tabla hija: una
 *    visita produce a lo más UNA promesa, y el dominio ya en verde
 *    (`VisitaEnVentana`, `EstadoCuentaDeriver`) la lee en singular. El monto va
 *    en **centavos enteros** — `Long`, jamás `Double`/`Float`: es dinero
 *    (`NoDoubleForMoney`).
 * 2. **Cita con hora** (`CITA_FECHA`, `CITA_HORA` en `Visit`). Dos columnas
 *    porque el mock tiene tres casos: hoy con hora, otro día con hora, y otro
 *    día sin hora.
 * 3. **La recomendación mostrada** (`visita_recomendaciones`). Tabla y no
 *    columnas, porque el **grupo de control no produce visita**: si el brazo
 *    del experimento viviera en la fila de la visita, el control nunca
 *    quedaría registrado. `GRUPO` nace con default `'tratamiento'` y admite
 *    otro valor sin una segunda migración.
 * 4 y 5. **Imágenes de pago y de visita** (`pago_imagenes`, `visita_imagenes`).
 *    Tablas hijas 0..N, no una columna: el servidor declara *"0..N
 *    comprobantes"*. La PK es el UUID que el teléfono genera y manda como
 *    `id_<n>` en el multipart — sin esa columna el contrato de la Task 9 no se
 *    puede satisfacer y el reintento deja de ser idempotente.
 * 6. **Ficha del cliente** (`cliente_ficha` + `cliente_ficha_senales`). El
 *    catálogo cerrado y la nota libre son dos campos con trabajos distintos y
 *    dos cardinalidades distintas (0..N contra 0..1), así que son dos tablas.
 *    NO son columnas de `cliente`: `ClienteDao.replaceAll` borra y reinserta
 *    esa tabla entera en cada sincronización y se llevaría la ficha con ella.
 *
 * ## Qué rompería si esta migración se editara mal
 *
 * Cualquier variante que recreara `Visit` o `Payment` en vez de usar
 * `ALTER TABLE ... ADD COLUMN` perdería visitas y pagos aún sin subir — dinero
 * cobrado en la calle que desaparece antes de llegar a Microsip, sin rollback
 * posible desde un teléfono. `Migration29to30Test` lo vigila con datos
 * sembrados y control positivo.
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addPromiseAndAppointmentColumns(db)
        createImageTables(db)
        createRecommendationTable(db)
        createClientProfileTables(db)
    }

    /**
     * Cinco `ADD COLUMN` nullable sobre `Visit`. Sin `DEFAULT` a propósito:
     * `NULL` es la lectura correcta para las visitas que ya existen — "esta
     * visita no dejó promesa ni cita" — y es distinta de un cero, que en un
     * monto significaría "prometió no pagar".
     */
    private fun addPromiseAndAppointmentColumns(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Visit` ADD COLUMN `PROMESA_VENTA_ID` INTEGER")
        db.execSQL("ALTER TABLE `Visit` ADD COLUMN `PROMESA_FECHA` TEXT")
        db.execSQL("ALTER TABLE `Visit` ADD COLUMN `PROMESA_MONTO_CENTAVOS` INTEGER")
        db.execSQL("ALTER TABLE `Visit` ADD COLUMN `CITA_FECHA` TEXT")
        db.execSQL("ALTER TABLE `Visit` ADD COLUMN `CITA_HORA` TEXT")
    }

    /**
     * Las dos tablas hijas de comprobantes. El DDL es el que Room genera para
     * estas entidades (copiado del `30.json` exportado), para que
     * `runMigrationsAndValidate` compare contra el esquema real y no contra
     * una paráfrasis.
     */
    private fun createImageTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `visita_imagenes` (" +
                "`ID` TEXT NOT NULL, `VISITA_ID` TEXT NOT NULL, `URI` TEXT NOT NULL, " +
                "`MIME` TEXT NOT NULL, `DESCRIPCION` TEXT, `ORDEN` INTEGER NOT NULL, " +
                "`CREADA_EN` TEXT NOT NULL, `SUBIDA_EN` TEXT, PRIMARY KEY(`ID`), " +
                "FOREIGN KEY(`VISITA_ID`) REFERENCES `Visit`(`ID`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_visita_imagenes_VISITA_ID` " +
                "ON `visita_imagenes` (`VISITA_ID`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_visita_imagenes_SUBIDA_EN` " +
                "ON `visita_imagenes` (`SUBIDA_EN`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pago_imagenes` (" +
                "`ID` TEXT NOT NULL, `PAGO_ID` TEXT NOT NULL, `URI` TEXT NOT NULL, " +
                "`MIME` TEXT NOT NULL, `DESCRIPCION` TEXT, `ORDEN` INTEGER NOT NULL, " +
                "`CREADA_EN` TEXT NOT NULL, `SUBIDA_EN` TEXT, PRIMARY KEY(`ID`), " +
                "FOREIGN KEY(`PAGO_ID`) REFERENCES `Payment`(`ID`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pago_imagenes_PAGO_ID` " +
                "ON `pago_imagenes` (`PAGO_ID`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pago_imagenes_SUBIDA_EN` " +
                "ON `pago_imagenes` (`SUBIDA_EN`)"
        )
    }

    /**
     * `visita_recomendaciones` deliberadamente SIN llave foránea a `Visit`:
     * `VisitDao.deleteUploadedVisits` poda las visitas ya confirmadas en cada
     * sincronización, y un `CASCADE` se llevaría con ellas la mitad "qué pasó"
     * del par que esta tabla existe para conservar.
     */
    private fun createRecommendationTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `visita_recomendaciones` (" +
                "`ID` TEXT NOT NULL, `CLIENTE_ID` INTEGER NOT NULL, `VENTA_ID` INTEGER, " +
                "`COBRADOR_ID` INTEGER NOT NULL, `GENERADA_EN` TEXT NOT NULL, " +
                "`POSICION` INTEGER NOT NULL, `MOTIVO` TEXT NOT NULL, " +
                "`ALGORITMO` TEXT NOT NULL, `GRUPO` TEXT NOT NULL DEFAULT 'tratamiento', " +
                "`VISITA_ID` TEXT, PRIMARY KEY(`ID`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_visita_recomendaciones_CLIENTE_ID` " +
                "ON `visita_recomendaciones` (`CLIENTE_ID`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_visita_recomendaciones_VISITA_ID` " +
                "ON `visita_recomendaciones` (`VISITA_ID`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_visita_recomendaciones_GENERADA_EN` " +
                "ON `visita_recomendaciones` (`GENERADA_EN`)"
        )
    }

    /**
     * Las dos mitades de la ficha, ninguna con llave foránea a `cliente` — esa
     * tabla se borra entera (`deleteAll()` + `insertAll()`) en cada
     * sincronización del catálogo, y la ficha es conocimiento local que debe
     * sobrevivirla.
     */
    private fun createClientProfileTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cliente_ficha` (" +
                "`CLIENTE_ID` INTEGER NOT NULL, `NOTA` TEXT, " +
                "`ACTUALIZADA_EN` TEXT NOT NULL, `COBRADOR_ID` INTEGER, " +
                "PRIMARY KEY(`CLIENTE_ID`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cliente_ficha_senales` (" +
                "`CLIENTE_ID` INTEGER NOT NULL, `SENAL` TEXT NOT NULL, " +
                "`ACTUALIZADA_EN` TEXT NOT NULL, PRIMARY KEY(`CLIENTE_ID`, `SENAL`))"
        )
    }
}

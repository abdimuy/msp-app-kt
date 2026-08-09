package com.example.msp_app.core.telemetry.queue

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Store Room PROPIO de telemetría (`telemetry_db`) — completamente
 * independiente de `msp_db` (`:core:database`, v27, INMUTABLE). Este módulo
 * NO importa `AppDatabase` ni ninguna entidad/DAO de `:core:database`; si
 * algo en el build lo insinuara, sería un error (ver gotcha del brief de
 * Task 3).
 *
 * `version = 1`: primer schema de este store, sin migraciones todavía —
 * cuando exista una v2, este es el punto que gana `addMigrations(...)` (mismo
 * patrón que `AppDatabase`, ver [buildDatabase]).
 *
 * Companion `getInstance`/`setInstanceForTesting`/`clearInstance` con la
 * MISMA semántica que `AppDatabase` (single-source de la conexión, puente
 * para tests) — ver KDoc de `com.example.msp_app.core.database.di.DatabaseModule`
 * para el porqué: una segunda conexión al mismo archivo arriesga
 * locking/corrupción, y un builder ad-hoc en el módulo Hilt rompería el
 * override que los tests usan para inyectar una DB in-memory/de archivo.
 */
@Database(
    entities = [TelemetryEventEntity::class],
    version = 1,
    exportSchema = true
)
abstract class TelemetryDatabase : RoomDatabase() {
    abstract fun telemetryEventDao(): TelemetryEventDao

    companion object {
        @Volatile
        private var instance: TelemetryDatabase? = null

        private const val DATABASE_NAME = "telemetry_db"

        fun getInstance(context: Context): TelemetryDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext, DATABASE_NAME)
                    .build().also { instance = it }
            }
        }

        /**
         * Única fuente de verdad para la configuración del builder de producción.
         * `getInstance` delega acá; los tests de este mismo módulo que necesitan
         * abrir la base "por la ruta de producción" (p.ej. la prueba de
         * durabilidad cerrar/reabrir) también, en vez de duplicar esta config a
         * mano — mismo patrón que `AppDatabase.buildDatabase`.
         */
        internal fun buildDatabase(
            context: Context,
            name: String
        ): RoomDatabase.Builder<TelemetryDatabase> =
            Room.databaseBuilder(context, TelemetryDatabase::class.java, name)

        @androidx.annotation.VisibleForTesting
        fun setInstanceForTesting(database: TelemetryDatabase) {
            instance = database
        }

        @androidx.annotation.VisibleForTesting
        fun clearInstance() {
            instance?.close()
            instance = null
        }
    }
}

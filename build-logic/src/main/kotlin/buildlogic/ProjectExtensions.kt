package buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Acceso al catálogo de versiones raíz (`gradle/libs.versions.toml`) desde
 * los convention plugins. `build-logic/settings.gradle.kts` lo expone bajo
 * el nombre "libs" via `versionCatalogs { create("libs") { from(files(...)) } }`.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

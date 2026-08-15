package com.example.msp_app.core.appgate

import com.example.msp_app.core.appgate.fake.FakeMinVersionConfigSource
import com.example.msp_app.core.appgate.fake.FakeVersionGateCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val INSTALLED_CODE = 56
private const val INSTALLED_NAME = "2.16.0"
private const val DEVICE_ID = "a1b2c3d4e5f60718"

/**
 * El cableado de las cuatro piezas. Lo importante acá no es la aritmética
 * (eso lo cubre `VersionGateTest`) sino **el sentido de las flechas**: la UI
 * lee de la caché, y la red solo escribe en ella.
 */
class AppVersionGateTest {

    private fun gate(
        cache: FakeVersionGateCache = FakeVersionGateCache(),
        remote: FakeMinVersionConfigSource = FakeMinVersionConfigSource(),
        debugBuild: Boolean = false,
        deviceId: String? = DEVICE_ID
    ) = AppVersionGate(
        cache = cache,
        remote = remote,
        buildInfo = AppBuildInfo(INSTALLED_CODE, INSTALLED_NAME, debugBuild),
        deviceIdProvider = { deviceId }
    )

    @Test
    fun `sin señal usa el ultimo veredicto guardado`() = runTest {
        // La fuente remota NUNCA emite (sótano sin datos) y aun así bloquea.
        val cache = FakeVersionGateCache(
            MinVersionConfig(
                minVersionCode = 58,
                minVersionName = "2.17.0",
                deadlineLabel = "vie 22"
            )
        )

        val status = gate(cache = cache).status.first()

        assertEquals(VersionVerdict.BLOCKED, status.verdict)
        assertEquals(INSTALLED_NAME, status.installedVersionName)
        assertEquals("2.17.0", status.requiredVersionName)
        assertEquals("vie 22", status.deadlineLabel)
    }

    @Test
    fun `sin nada en cache permite - un arranque limpio nunca bloquea`() = runTest {
        assertEquals(VersionVerdict.ALLOWED, gate().status.first().verdict)
    }

    @Test
    fun `un build de depuracion no se bloquea aunque la cache exija mas`() = runTest {
        val cache = FakeVersionGateCache(MinVersionConfig(minVersionCode = 999))

        assertEquals(
            VersionVerdict.ALLOWED,
            gate(cache = cache, debugBuild = true).status.first().verdict
        )
    }

    @Test
    fun `el mismo caso en release SI bloquea - el gemelo del anterior`() = runTest {
        val cache = FakeVersionGateCache(MinVersionConfig(minVersionCode = 999))

        assertEquals(
            VersionVerdict.BLOCKED,
            gate(cache = cache, debugBuild = false).status.first().verdict
        )
    }

    @Test
    fun `un dispositivo exento no se bloquea`() = runTest {
        val cache = FakeVersionGateCache(
            MinVersionConfig(minVersionCode = 999, exemptDeviceIds = setOf(DEVICE_ID))
        )

        assertEquals(VersionVerdict.ALLOWED, gate(cache = cache).status.first().verdict)
    }

    @Test
    fun `otro dispositivo con la misma lista SI se bloquea`() = runTest {
        val cache = FakeVersionGateCache(
            MinVersionConfig(minVersionCode = 999, exemptDeviceIds = setOf(DEVICE_ID))
        )

        val status = gate(cache = cache, deviceId = "0f1e2d3c4b5a6978").status.first()

        assertEquals(VersionVerdict.BLOCKED, status.verdict)
    }

    @Test
    fun `syncRemote vuelca la configuracion remota a la cache`() = runTest {
        val cache = FakeVersionGateCache()
        val remote = FakeMinVersionConfigSource()
        val subject = gate(cache = cache, remote = remote)
        remote.emit(MinVersionConfig(minVersionCode = 58, minVersionName = "2.17.0"))

        // `syncRemote` no termina nunca (escucha continua): se corre hasta la
        // primera escritura y se corta el scope de prueba.
        val job = launch { subject.syncRemote() }
        val status = subject.status.first { it.verdict == VersionVerdict.BLOCKED }
        job.cancel()

        assertEquals("2.17.0", status.requiredVersionName)
        assertEquals(1, cache.saveCount)
    }
}

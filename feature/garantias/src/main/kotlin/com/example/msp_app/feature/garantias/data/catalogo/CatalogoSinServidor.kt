package com.example.msp_app.feature.garantias.data.catalogo

import com.example.msp_app.feature.garantias.domain.model.CatalogoDeGarantias
import com.example.msp_app.feature.garantias.domain.port.CatalogoDeGarantiasPort
import javax.inject.Inject

/**
 * La implementación del catálogo mientras `GET /v2/garantias/catalogos` no
 * exista en `msp-api`.
 *
 * Devuelve [CatalogoDeGarantias.SIN_CATALOGO] y no una tabla escrita en el
 * teléfono. Eso es deliberado (D2): las transiciones válidas las decide el
 * servidor, y copiarlas acá sería una segunda fuente de verdad que se
 * despegaría en la primera corrección de la máquina de estados.
 *
 * Consecuencia visible: la pantalla de recolección no ofrece acciones y dice
 * "Sin catálogo". El día que el endpoint exista, esta clase se reemplaza por un
 * adaptador que lea la caché de Room o el endpoint, y **ninguna pantalla
 * cambia**.
 */
class CatalogoSinServidor @Inject constructor() : CatalogoDeGarantiasPort {

    override suspend fun leer(): CatalogoDeGarantias = CatalogoDeGarantias.SIN_CATALOGO
}

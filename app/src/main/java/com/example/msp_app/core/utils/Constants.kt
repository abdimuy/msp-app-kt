package com.example.msp_app.core.utils

object Constants {
    const val APP_VERSION = "2.0.2"

    const val PAGO_EN_EFECTIVO_ID = 157
    const val PAGO_CON_CHEQUE_ID = 158
    const val PAGO_CON_TRANSFERENCIA_ID = 52569
    const val CONDONACION_ID = 137026

    // Firebase Firestore collection names
    const val USERS_COLLECTION = "users"

    // Firebase Firestore field names
    const val EMAIL_FIELD = "EMAIL"
    const val START_OF_WEEK_DATE_FIELD = "FECHA_CARGA_INICIAL"

    // Opciones de los datos de Visita
    const val NO_SE_ENCONTRABA = "No se encontraba"
    const val CASA_CERRADA = "Casa cerrada con candado"
    const val SOLO_MENORES = "Solo había menores"
    const val NO_VA_A_DAR_PAGO = "Dijo que no va a pagar"
    const val PIDE_TIEMPO = "Pidió que regrese otro día"
    const val TIENE_PERO_NO_PAGA = "Tiene dinero pero no quiso pagar"
    const val FUE_GROSERO = "Fue grosero o agresivo"
    const val SE_ESCONDE = "Se asomó pero no salió"
    const val NO_RESPONDE = "No responde aunque está"
    const val SE_ESCUCHAN_RUIDOS = "Se escuchan ruidos pero no abre"
    const val PIDE_REAGENDAR = "Pidió reagendar visita"

    //Datos para ticket
    const val WHATSAPP = "238-374-06-84"
    const val TELEFONO = "238-110-50-61"

    //Firebase Collection
    const val COLLECTION_CONFIG = "config"

    //Firebase Document
    const val DOCUMENT_API_SETTINGS = "api_settings"

    //Firebase Field
    const val FIELD_BASE_URL = "baseURL"
}
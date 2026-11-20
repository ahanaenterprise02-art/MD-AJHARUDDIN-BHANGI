package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Splash : AppRoute

    @Serializable
    data object Home : AppRoute

    @Serializable
    data object MyPdfs : AppRoute

    @Serializable
    data class Tools(val toolId: String? = null) : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object ImageToPdf : AppRoute

    @Serializable
    data object DocumentScanner : AppRoute

    @Serializable
    data class PdfPreviewSuccess(
        val recordId: Long,
        val showSuccessBanner: Boolean = false
    ) : AppRoute
}

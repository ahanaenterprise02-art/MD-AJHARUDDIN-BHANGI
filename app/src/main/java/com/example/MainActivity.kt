package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ads.AdMobManager
import com.example.data.db.PdfRecordEntity
import com.example.data.prefs.ThemeMode
import com.example.domain.model.PdfToolType
import com.example.domain.pdf.PdfFileHelper
import com.example.ui.components.AdMobBannerBar
import com.example.ui.i18n.stringsFor
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.DocumentScannerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageToPdfScreen
import com.example.ui.screens.MyPdfsScreen
import com.example.ui.screens.PdfPreviewSuccessScreen
import com.example.ui.screens.PdfToolsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.AhaPdfTheme
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandWhite
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.ImageToPdfViewModel
import com.example.ui.viewmodel.PdfToolsViewModel
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AhaPdfRootApp()
        }
    }
}

private enum class TopLevelTab(
    val route: AppRoute,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(AppRoute.Home, Icons.Filled.Home, Icons.Outlined.Home),
    MY_PDFS(AppRoute.MyPdfs, Icons.Filled.Folder, Icons.Outlined.Folder),
    TOOLS(AppRoute.Tools(null), Icons.Filled.Build, Icons.Outlined.Build),
    SETTINGS(AppRoute.Settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AhaPdfRootApp() {
    val appViewModel: AppViewModel = viewModel()
    val imageToPdfViewModel: ImageToPdfViewModel = viewModel()
    val pdfToolsViewModel: PdfToolsViewModel = viewModel()

    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val recentPdfs by appViewModel.recentPdfs.collectAsStateWithLifecycle()
    val allPdfs by appViewModel.filteredPdfs.collectAsStateWithLifecycle()
    val searchQuery by appViewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by appViewModel.sortOrder.collectAsStateWithLifecycle()
    val isGridView by appViewModel.isGridView.collectAsStateWithLifecycle()
    val imageToPdfState by imageToPdfViewModel.uiState.collectAsStateWithLifecycle()
    val pdfToolsState by pdfToolsViewModel.uiState.collectAsStateWithLifecycle()

    val isDark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val strings = remember(settings.language) { stringsFor(settings.language) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSaveRecord by remember { mutableStateOf<PdfRecordEntity?>(null) }

    val saveSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val rec = pendingSaveRecord
        if (uri != null && rec != null) {
            scope.launch {
                val ok = PdfFileHelper.savePdfToSafUri(context, File(rec.filePath), uri)
                Toast.makeText(
                    context,
                    if (ok) "Saved ${rec.fileName}.pdf to device" else "Could not save PDF.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        pendingSaveRecord = null
    }

    var showCreateSheet by remember { mutableStateOf(false) }

    AhaPdfTheme(darkTheme = isDark) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        val showTopLevelChrome = currentDestination != null && (
            currentDestination.hasRoute<AppRoute.Home>() ||
                currentDestination.hasRoute<AppRoute.MyPdfs>() ||
                currentDestination.hasRoute<AppRoute.Tools>() ||
                currentDestination.hasRoute<AppRoute.Settings>()
            )

        fun navigateTopLevel(tab: TopLevelTab) {
            if (tab == TopLevelTab.TOOLS) {
                pdfToolsViewModel.openTool(null)
            }
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun tabLabel(tab: TopLevelTab): String = when (tab) {
            TopLevelTab.HOME -> strings.navHome
            TopLevelTab.MY_PDFS -> strings.navMyPdfs
            TopLevelTab.TOOLS -> strings.navTools
            TopLevelTab.SETTINGS -> strings.navSettings
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 680.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                floatingActionButton = {
                    if (showTopLevelChrome) {
                        FloatingActionButton(
                            onClick = { showCreateSheet = true },
                            containerColor = BrandRed,
                            contentColor = BrandWhite,
                            modifier = Modifier.testTag("main_fab_add")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Create or Scan PDF")
                        }
                    }
                },
                bottomBar = {
                    if (showTopLevelChrome) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AdMobBannerBar(modifier = Modifier.testTag("admob_banner_bar"))
                            if (!useRail) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 4.dp
                                ) {
                                    TopLevelTab.entries.forEach { tab ->
                                        val selected = when (tab) {
                                            TopLevelTab.HOME -> currentDestination?.hasRoute<AppRoute.Home>() == true
                                            TopLevelTab.MY_PDFS -> currentDestination?.hasRoute<AppRoute.MyPdfs>() == true
                                            TopLevelTab.TOOLS -> currentDestination?.hasRoute<AppRoute.Tools>() == true
                                            TopLevelTab.SETTINGS -> currentDestination?.hasRoute<AppRoute.Settings>() == true
                                        }
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTopLevel(tab) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tabLabel(tab)
                                                )
                                            },
                                            label = { Text(tabLabel(tab), maxLines = 1) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = BrandWhite,
                                                selectedTextColor = BrandNavy,
                                                indicatorColor = BrandNavy
                                            ),
                                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showTopLevelChrome && useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            TopLevelTab.entries.forEach { tab ->
                                val selected = when (tab) {
                                    TopLevelTab.HOME -> currentDestination?.hasRoute<AppRoute.Home>() == true
                                    TopLevelTab.MY_PDFS -> currentDestination?.hasRoute<AppRoute.MyPdfs>() == true
                                    TopLevelTab.TOOLS -> currentDestination?.hasRoute<AppRoute.Tools>() == true
                                    TopLevelTab.SETTINGS -> currentDestination?.hasRoute<AppRoute.Settings>() == true
                                }
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tabLabel(tab)
                                        )
                                    },
                                    label = { Text(tabLabel(tab)) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = AppRoute.Splash
                        ) {
                            composable<AppRoute.Splash> {
                                SplashScreen(
                                    onFinished = {
                                        navController.navigate(AppRoute.Home) {
                                            popUpTo<AppRoute.Splash> { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable<AppRoute.Home> {
                                HomeScreen(
                                    strings = strings,
                                    recentPdfs = recentPdfs,
                                    onOpenImageToPdf = { navController.navigate(AppRoute.ImageToPdf) },
                                    onOpenScanner = { navController.navigate(AppRoute.DocumentScanner) },
                                    onOpenTool = { tool ->
                                        pdfToolsViewModel.openTool(tool)
                                        navController.navigate(AppRoute.Tools(tool.id))
                                    },
                                    onOpenAllTools = {
                                        pdfToolsViewModel.openTool(null)
                                        navController.navigate(AppRoute.Tools(null))
                                    },
                                    onOpenMyPdfs = { navigateTopLevel(TopLevelTab.MY_PDFS) },
                                    onOpenPreview = { rec ->
                                        navController.navigate(AppRoute.PdfPreviewSuccess(rec.id, showSuccessBanner = false))
                                    },
                                    onRenamePdf = { rec, newName -> appViewModel.renamePdf(rec, newName) },
                                    onDuplicatePdf = { rec -> appViewModel.duplicatePdf(rec) },
                                    onDeletePdf = { rec -> appViewModel.deletePdf(rec) },
                                    onSavePdfToDevice = { rec ->
                                        pendingSaveRecord = rec
                                        saveSafLauncher.launch("${rec.fileName}.pdf")
                                    }
                                )
                            }

                            composable<AppRoute.MyPdfs> {
                                MyPdfsScreen(
                                    strings = strings,
                                    pdfs = allPdfs,
                                    searchQuery = searchQuery,
                                    sortOrder = sortOrder,
                                    isGridView = isGridView,
                                    onSearchChange = { appViewModel.updateSearchQuery(it) },
                                    onSortChange = { appViewModel.updateSortOrder(it) },
                                    onToggleGridView = { appViewModel.toggleGridView() },
                                    onOpenPreview = { rec ->
                                        navController.navigate(AppRoute.PdfPreviewSuccess(rec.id, showSuccessBanner = false))
                                    },
                                    onRenamePdf = { rec, newName -> appViewModel.renamePdf(rec, newName) },
                                    onDuplicatePdf = { rec -> appViewModel.duplicatePdf(rec) },
                                    onDeletePdf = { rec -> appViewModel.deletePdf(rec) },
                                    onSavePdfToDevice = { rec ->
                                        pendingSaveRecord = rec
                                        saveSafLauncher.launch("${rec.fileName}.pdf")
                                    },
                                    onCreatePdf = { navController.navigate(AppRoute.ImageToPdf) }
                                )
                            }

                            composable<AppRoute.Tools> { entry ->
                                val route = entry.toRoute<AppRoute.Tools>()
                                LaunchedEffect(route.toolId) {
                                    if (route.toolId != null) {
                                        val match = PdfToolType.entries.firstOrNull { it.id == route.toolId }
                                        if (match != null && pdfToolsState.activeTool != match) {
                                            pdfToolsViewModel.openTool(match)
                                        }
                                    }
                                }
                                PdfToolsScreen(
                                    strings = strings,
                                    language = settings.language,
                                    allSavedPdfs = allPdfs,
                                    uiState = pdfToolsState,
                                    viewModel = pdfToolsViewModel,
                                    onPdfCreated = { rec ->
                                        val navigate = {
                                            navController.navigate(AppRoute.PdfPreviewSuccess(rec.id, showSuccessBanner = true))
                                        }
                                        val act = context.findActivity()
                                        if (act != null) {
                                            AdMobManager.showInterstitialIfReady(act) { navigate() }
                                        } else {
                                            navigate()
                                        }
                                    },
                                    onNavigateToMyPdfs = { navigateTopLevel(TopLevelTab.MY_PDFS) }
                                )
                            }

                            composable<AppRoute.Settings> {
                                SettingsScreen(
                                    strings = strings,
                                    settings = settings,
                                    onLanguageChange = { appViewModel.setLanguage(it) },
                                    onThemeModeChange = { appViewModel.setThemeMode(it) },
                                    onDefaultPageSizeChange = { appViewModel.setDefaultPageSize(it) },
                                    onDefaultQualityChange = { appViewModel.setDefaultImageQuality(it) },
                                    onDefaultSaveLocationChange = { uri, label ->
                                        appViewModel.setDefaultSaveLocation(uri, label)
                                    }
                                )
                            }

                            composable<AppRoute.ImageToPdf> {
                                ImageToPdfScreen(
                                    strings = strings,
                                    uiState = imageToPdfState,
                                    viewModel = imageToPdfViewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenScanner = { navController.navigate(AppRoute.DocumentScanner) },
                                    onPdfGenerated = { record ->
                                        imageToPdfViewModel.clearAllPages()
                                        val navigate = {
                                            navController.navigate(AppRoute.PdfPreviewSuccess(record.id, showSuccessBanner = true)) {
                                                popUpTo<AppRoute.ImageToPdf> { inclusive = true }
                                            }
                                        }
                                        val act = context.findActivity()
                                        if (act != null) {
                                            AdMobManager.showInterstitialIfReady(act) { navigate() }
                                        } else {
                                            navigate()
                                        }
                                    }
                                )
                            }

                            composable<AppRoute.DocumentScanner> {
                                DocumentScannerScreen(
                                    onBack = { navController.popBackStack() },
                                    onDoneWithPages = { scanned ->
                                        imageToPdfViewModel.addScannedPages(scanned, replaceExisting = false)
                                        navController.navigate(AppRoute.ImageToPdf) {
                                            popUpTo<AppRoute.DocumentScanner> { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable<AppRoute.PdfPreviewSuccess> { entry ->
                                val route = entry.toRoute<AppRoute.PdfPreviewSuccess>()
                                PdfPreviewSuccessScreen(
                                    recordId = route.recordId,
                                    showSuccessBanner = route.showSuccessBanner,
                                    strings = strings,
                                    loadRecord = { id -> appViewModel.getPdfById(id) },
                                    onRenameRecord = { rec, newName, cb ->
                                        appViewModel.renamePdf(rec, newName, cb)
                                    },
                                    onBack = { navController.popBackStack() },
                                    onUnlockPdfRequested = {
                                        pdfToolsViewModel.openTool(PdfToolType.UNLOCK_PDF)
                                        navController.navigate(AppRoute.Tools(PdfToolType.UNLOCK_PDF.id))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showCreateSheet) {
            ModalBottomSheet(onDismissRequest = { showCreateSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Create or Convert PDF",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    CreateSheetActionRow(
                        title = strings.imageToPdf,
                        subtitle = "Pick multiple gallery photos, crop, apply filters & build PDF",
                        icon = Icons.Filled.Image,
                        accent = BrandRed
                    ) {
                        showCreateSheet = false
                        navController.navigate(AppRoute.ImageToPdf)
                    }
                    CreateSheetActionRow(
                        title = strings.scanDocument,
                        subtitle = "CameraX live scanner with auto edge detection & perspective fix",
                        icon = Icons.Filled.DocumentScanner,
                        accent = BrandNavy
                    ) {
                        showCreateSheet = false
                        navController.navigate(AppRoute.DocumentScanner)
                    }
                    CreateSheetActionRow(
                        title = strings.pdfToolsTitle,
                        subtitle = "Merge, Split, Compress, Sign, Watermark, Lock & Unlock PDFs",
                        icon = Icons.Filled.Build,
                        accent = BrandRed
                    ) {
                        showCreateSheet = false
                        pdfToolsViewModel.openTool(null)
                        navController.navigate(AppRoute.Tools(null))
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateSheetActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

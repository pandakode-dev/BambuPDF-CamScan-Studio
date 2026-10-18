package com.example.bambupdf

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

// ============================================================================
// 1. PALET WARNA: IMPERIAL CRIMSON, SAPPHIRE BLUE & ROYAL GOLD
// ============================================================================
val CrimsonRed = Color(0xFFDC2626)
val DeepRuby = Color(0xFF991B1B)
val RoyalSapphire = Color(0xFF1E40AF)
val SoftBlueBg = Color(0xFFEFF6FF)
val ImperialGold = Color(0xFFF59E0B)
val SoftGoldBg = Color(0xFFFEF3C7)
val WarmPearlBg = Color(0xFFFFFDF9)
val SoftRoseBorder = Color(0xFFFECACA)
val CharcoalInk = Color(0xFF1E293B)
val PureWhite = Color(0xFFFFFFFF)

private val CrimsonSapphireColorScheme = lightColorScheme(
    primary = CrimsonRed,
    onPrimary = PureWhite,
    primaryContainer = SoftRoseBorder,
    onPrimaryContainer = DeepRuby,
    secondary = RoyalSapphire,
    onSecondary = PureWhite,
    secondaryContainer = SoftBlueBg,
    onSecondaryContainer = RoyalSapphire,
    tertiary = ImperialGold,
    background = WarmPearlBg,
    onBackground = CharcoalInk,
    surface = PureWhite,
    onSurface = CharcoalInk
)

data class PdfHistoryItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val type: String,
    val filePath: String,
    val hashCode: String,
    val timestamp: String
)

data class SignatureStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

enum class DocFilterMode(val label: String, val desc: String) {
    MAGIC_COLOR("Magic Pro", "Kertas putih & teks tajam"),
    BW_SHARP("Hitam Putih", "Fotokopi / hemat tinta"),
    ORIGINAL("Warna Asli", "Tanpa filter warna"),
    GRAYSCALE("Abu Resmi", "Nuansa formal"),
    WARM_PAPER("Kertas Buku", "Nyaman dibaca")
}

enum class PdfPageSize(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("Kertas A4", 595, 842),
    F4_FOLIO("F4 / Folio", 612, 936),
    LETTER("US Letter", 612, 792)
}

// Pilihan Stempel Kantor Digital Baru
enum class OfficeStampType(val label: String, val hexColor: String) {
    NONE("Tanpa Stempel", "#000000"),
    PAID("LUNAS / PAID", "#16A34A"),
    APPROVED("DISETUJUI / APPROVED", "#1E40AF"),
    ORIGINAL("DOKUMEN ASLI", "#DC2626"),
    CONFIDENTIAL("RAHASIA / URGENT", "#991B1B"),
    VERIFIED("TERVERIFIKASI LAB", "#D97706")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = CrimsonSapphireColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BambuPdfUltimateApp()
                }
            }
        }
    }
}

// ============================================================================
// 2. KOMPONEN UTAMA & NAVIGASI 5 TAB LENGKAP
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BambuPdfUltimateApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    var savedSignatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var signerName by remember { mutableStateOf("Muhammad Saputra") }
    var signerRole by remember { mutableStateOf("Founder PandaKode") }
    var selectedStamp by remember { mutableStateOf(OfficeStampType.VERIFIED) }

    var activePdfToView by remember { mutableStateOf<File?>(null) }

    val pdfHistoryList = remember {
        mutableStateListOf<PdfHistoryItem>().apply {
            addAll(loadPdfHistory(context))
        }
    }

    fun addPdfRecord(title: String, type: String, file: File, hash: String) {
        val time = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        pdfHistoryList.add(
            0,
            PdfHistoryItem(
                title = title,
                type = type,
                filePath = file.absolutePath,
                hashCode = hash,
                timestamp = time
            )
        )
        savePdfHistory(context, pdfHistoryList)
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(DeepRuby, CrimsonRed, RoyalSapphire)
                        )
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PureWhite,
                            border = BorderStroke(2.dp, ImperialGold),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.logo_bambupdf),
                                    contentDescription = "Logo BambuPDF",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "BambuPDF",
                                    color = PureWhite,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = ImperialGold,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "SUPER SUITE",
                                        color = CharcoalInk,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "CamScan • AI OCR • Pasfoto • Merge PDF",
                                color = SoftGoldBg,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        color = if (savedSignatureBitmap != null) ImperialGold else PureWhite.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ImperialGold),
                        modifier = Modifier.clickable { selectedTab = 1 }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (savedSignatureBitmap != null) Icons.Default.Verified else Icons.Default.Draw,
                                contentDescription = null,
                                tint = if (savedSignatureBitmap != null) CharcoalInk else PureWhite,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (savedSignatureBitmap != null) "TTD Aktif" else "Set TTD",
                                color = if (savedSignatureBitmap != null) CharcoalInk else PureWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Brush.horizontalGradient(listOf(ImperialGold, PureWhite, ImperialGold)))
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = PureWhite, tonalElevation = 10.dp) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                    label = { Text("CamScan", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PureWhite,
                        selectedTextColor = CrimsonRed,
                        indicatorColor = CrimsonRed
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Draw, contentDescription = null) },
                    label = { Text("TTD & Cap", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PureWhite,
                        selectedTextColor = RoyalSapphire,
                        indicatorColor = RoyalSapphire
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    label = { Text("KTP & Foto", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PureWhite,
                        selectedTextColor = CrimsonRed,
                        indicatorColor = CrimsonRed
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    label = { Text("OCR & Gabung", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PureWhite,
                        selectedTextColor = RoyalSapphire,
                        indicatorColor = RoyalSapphire
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.FolderCopy, contentDescription = null) },
                    label = { Text("Arsip (${pdfHistoryList.size})", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PureWhite,
                        selectedTextColor = CrimsonRed,
                        indicatorColor = CrimsonRed
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> CamScannerStudioScreen(
                    savedSignature = savedSignatureBitmap,
                    signerName = signerName,
                    signerRole = signerRole,
                    activeStamp = selectedStamp,
                    onGoToSignTab = { selectedTab = 1 },
                    onPdfCreated = { title, type, file, hash ->
                        addPdfRecord(title, type, file, hash)
                        activePdfToView = file
                    }
                )
                1 -> SignatureAndStampStudioScreen(
                    currentSignerName = signerName,
                    currentSignerRole = signerRole,
                    currentStamp = selectedStamp,
                    savedSignature = savedSignatureBitmap,
                    onSaveSignatureAndStamp = { btm, name, role, stamp ->
                        if (btm != null) savedSignatureBitmap = btm
                        signerName = name
                        signerRole = role
                        selectedStamp = stamp
                    }
                )
                2 -> SmartTemplatesAndPasfotoScreen(
                    savedSignature = savedSignatureBitmap,
                    signerName = signerName,
                    signerRole = signerRole,
                    activeStamp = selectedStamp,
                    onPdfCreated = { title, type, file, hash ->
                        addPdfRecord(title, type, file, hash)
                        activePdfToView = file
                    }
                )
                3 -> AiOcrAndMergePdfScreen(
                    signerName = signerName,
                    signerRole = signerRole,
                    savedSignature = savedSignatureBitmap,
                    onPdfCreated = { title, type, file, hash ->
                        addPdfRecord(title, type, file, hash)
                        activePdfToView = file
                    }
                )
                4 -> PdfArchiveAndAboutScreen(
                    historyList = pdfHistoryList,
                    onOpenPdfViewer = { file -> activePdfToView = file },
                    onDelete = { item ->
                        pdfHistoryList.remove(item)
                        savePdfHistory(context, pdfHistoryList)
                    }
                )
            }
        }
    }

    activePdfToView?.let { pdfFile ->
        InAppPdfViewerDialog(
            pdfFile = pdfFile,
            onDismiss = { activePdfToView = null },
            onShare = { sharePdfFile(context, pdfFile) }
        )
    }
}

// ============================================================================
// 3. TAB 1: CAMSCANNER PRO + NOMOR HALAMAN + STEMPEL KANTOR
// ============================================================================
@Composable
fun CamScannerStudioScreen(
    savedSignature: Bitmap?,
    signerName: String,
    signerRole: String,
    activeStamp: OfficeStampType,
    onGoToSignTab: () -> Unit,
    onPdfCreated: (String, String, File, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val filterScrollState = rememberLazyListState()

    val scannedPages = remember { mutableStateListOf<Bitmap>() }
    var pdfTitle by remember { mutableStateOf("Scan_BambuPDF") }
    var selectedFilter by remember { mutableStateOf(DocFilterMode.MAGIC_COLOR) }
    var selectedPageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var watermarkText by remember { mutableStateOf("") }
    var attachCryptoSeal by remember { mutableStateOf(true) }
    var showPageNumbers by remember { mutableStateOf(true) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.forEach { page ->
                loadBitmapFromUri(context, page.imageUri)?.let { btm ->
                    scannedPages.add(btm)
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            loadBitmapFromUri(context, uri)?.let { btm ->
                scannedPages.add(btm)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Banner Hero CamScanner
        Card(
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(2.dp, ImperialGold),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(DeepRuby, CrimsonRed, RoyalSapphire)))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = ImperialGold, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = CharcoalInk, modifier = Modifier.size(26.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Smart AI CamScanner", color = PureWhite, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                                Text("Auto-Crop Tepi Kertas & Bersihkan Bayangan", color = SoftGoldBg, fontSize = 11.sp)
                            }
                        }
                        Surface(color = PureWhite, shape = RoundedCornerShape(50)) {
                            Text(
                                text = "${scannedPages.size} Hal",
                                color = DeepRuby,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (activity != null) {
                                    val options = GmsDocumentScannerOptions.Builder()
                                        .setGalleryImportAllowed(true)
                                        .setPageLimit(25)
                                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                                        .build()
                                    GmsDocumentScanning.getClient(options)
                                        .getStartScanIntent(activity)
                                        .addOnSuccessListener { intentSender ->
                                            scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                        }
                                        .addOnFailureListener {
                                            galleryLauncher.launch("image/*")
                                        }
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = ImperialGold),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraEnhance, contentDescription = null, tint = CharcoalInk)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("MULAI CAMSCAN", color = CharcoalInk, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PureWhite),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = RoyalSapphire)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dari Galeri", color = RoyalSapphire, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Meja Kerja Halaman
        Card(
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.5.dp, SoftRoseBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. Meja Kerja Halaman (${scannedPages.size})", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = DeepRuby)
                    TextButton(onClick = { scannedPages.add(createSampleDocumentBitmap(scannedPages.size + 1)) }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = RoyalSapphire, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Contoh Halaman", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RoyalSapphire)
                    }
                }

                if (scannedPages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SoftBlueBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Klik 'MULAI CAMSCAN', 'Dari Galeri', atau '+ Contoh Halaman' untuk mulai menyusun dokumen.",
                            fontSize = 12.sp,
                            color = RoyalSapphire,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(scannedPages) { index, pageBtm ->
                            val previewFiltered = remember(pageBtm, selectedFilter) {
                                applyDocFilter(pageBtm, selectedFilter)
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = WarmPearlBg),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, ImperialGold),
                                modifier = Modifier.width(135.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.fillMaxWidth().height(145.dp)) {
                                        Image(
                                            bitmap = previewFiltered.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            color = CrimsonRed,
                                            shape = RoundedCornerShape(bottomEnd = 8.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text("Hal ${index + 1}", color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(PureWhite).padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        IconButton(onClick = { if (index > 0) Collections.swap(scannedPages, index, index - 1) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = RoyalSapphire, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { scannedPages[index] = rotateBitmap90(pageBtm) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.RotateRight, contentDescription = null, tint = ImperialGold, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { if (index < scannedPages.lastIndex) Collections.swap(scannedPages, index, index + 1) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = RoyalSapphire, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { scannedPages.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filter Magic CamScanner
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("2. Filter Magic CamScanner & Kertas", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = DeepRuby)
                Text("👉 Geser untuk memilih filter pembersih dokumen", fontSize = 11.sp, color = RoyalSapphire, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    shape = CircleShape,
                    color = PureWhite,
                    border = BorderStroke(1.dp, CrimsonRed),
                    modifier = Modifier.size(28.dp).clickable { coroutineScope.launch { filterScrollState.animateScrollToItem(0) } }
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(16.dp)) }
                }
                Surface(
                    shape = CircleShape,
                    color = ImperialGold,
                    border = BorderStroke(1.dp, DeepRuby),
                    modifier = Modifier.size(28.dp).clickable { coroutineScope.launch { filterScrollState.animateScrollToItem(DocFilterMode.entries.size - 1) } }
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CharcoalInk, modifier = Modifier.size(16.dp)) }
                }
            }
        }

        LazyRow(state = filterScrollState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(DocFilterMode.entries) { mode ->
                val isSelected = selectedFilter == mode
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) CrimsonRed else PureWhite,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) ImperialGold else SoftRoseBorder),
                    modifier = Modifier.clickable { selectedFilter = mode }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = if (isSelected) ImperialGold else CrimsonRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(mode.label, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (isSelected) PureWhite else CharcoalInk)
                        }
                        Text(mode.desc, fontSize = 10.sp, color = if (isSelected) SoftGoldBg else Color.Gray)
                    }
                }
            }
        }

        // Pengaturan Dokumen, Nomor Halaman & Stempel
        Card(
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SoftRoseBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pdfTitle,
                    onValueChange = { pdfTitle = it },
                    label = { Text("Nama Dokumen PDF") },
                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = CrimsonRed) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Watermark Diagonal (Opsional)") },
                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = RoyalSapphire) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tampilkan Nomor Halaman Otomatis (Hal 1 dari X)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = showPageNumbers, onCheckedChange = { showPageNumbers = it })
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftGoldBg.copy(alpha = 0.65f))
                        .border(1.dp, ImperialGold, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🔏 Stempel QR Crypto-Seal, TTD & Cap Kantor", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = CharcoalInk)
                        Text("Cap Aktif: ${activeStamp.label} • TTD: $signerName", fontSize = 11.sp, color = DeepRuby)
                    }
                    Switch(
                        checked = attachCryptoSeal,
                        onCheckedChange = { attachCryptoSeal = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ImperialGold, checkedTrackColor = CrimsonRed)
                    )
                }
            }
        }

        Button(
            onClick = {
                val pagesToRender = if (scannedPages.isEmpty()) listOf(createSampleDocumentBitmap(1)) else scannedPages.toList()
                val result = generateMultiPagePdf(
                    context = context,
                    fileName = pdfTitle.ifBlank { "Scan_BambuPDF" },
                    pages = pagesToRender,
                    pageSize = selectedPageSize,
                    filterMode = selectedFilter,
                    watermark = watermarkText.trim(),
                    includeCryptoSeal = attachCryptoSeal,
                    includePageNumbers = showPageNumbers,
                    stampType = activeStamp,
                    signatureBitmap = savedSignature,
                    signerName = signerName,
                    signerRole = signerRole
                )
                if (result != null) {
                    val (pdfFile, hashCode) = result
                    savePdfToDownloads(context, pdfFile)
                    onPdfCreated(pdfTitle.ifBlank { "Scan PDF" }, "CamScan (${selectedFilter.label})", pdfFile, hashCode)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ImperialGold)
            Spacer(modifier = Modifier.width(8.dp))
            Text("KONVERSI KE PDF & LIHAT HASILNYA", color = PureWhite, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

// ============================================================================
// 4. TAB 2: KANVAS TANDA TANGAN + PEMILIH STEMPEL KANTOR (LUNAS/ASLI/DISETUJUI)
// ============================================================================
@Composable
fun SignatureAndStampStudioScreen(
    currentSignerName: String,
    currentSignerRole: String,
    currentStamp: OfficeStampType,
    savedSignature: Bitmap?,
    onSaveSignatureAndStamp: (Bitmap?, String, String, OfficeStampType) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(currentSignerName) }
    var role by remember { mutableStateOf(currentSignerRole) }
    var chosenStamp by remember { mutableStateOf(currentStamp) }

    val strokes = remember { mutableStateListOf<SignatureStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedPenColor by remember { mutableStateOf(RoyalSapphire) }

    val penColors = listOf(
        "Biru Pena Resmi" to RoyalSapphire,
        "Hitam Tegas" to CharcoalInk,
        "Merah Marun" to DeepRuby
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Studio Tanda Tangan & Stempel Kantor Digital", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = DeepRuby)

        // Pilihan Stempel Kantor Digital
        Text("1. Pilih Stempel Basah Digital (Otomatis Tampil di Dokumen):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RoyalSapphire)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(OfficeStampType.entries) { stamp ->
                val isSelected = chosenStamp == stamp
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        chosenStamp = stamp
                        onSaveSignatureAndStamp(null, name, role, stamp)
                    },
                    label = { Text(stamp.label, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Approval, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RoyalSapphire,
                        selectedLabelColor = PureWhite,
                        selectedLeadingIconColor = ImperialGold
                    )
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SoftRoseBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Penandatangan") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = CrimsonRed) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Jabatan / Instansi") },
                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = RoyalSapphire) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            penColors.forEach { (label, color) ->
                FilterChip(
                    selected = selectedPenColor == color,
                    onClick = { selectedPenColor = color },
                    label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(color)) }
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, ImperialGold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✍️ Goreskan Tanda Tangan di Kanvas Bawah Ini", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RoyalSapphire)
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftBlueBg.copy(alpha = 0.4f))
                        .border(1.dp, RoyalSapphire.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .pointerInput(selectedPenColor) {
                            detectDragGestures(
                                onDragStart = { offset -> currentPoints = listOf(offset) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPoints = currentPoints + change.position
                                },
                                onDragEnd = {
                                    if (currentPoints.size > 1) {
                                        strokes.add(SignatureStroke(currentPoints, selectedPenColor, 8f))
                                    }
                                    currentPoints = emptyList()
                                }
                            )
                        }
                ) {
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(40f, size.height * 0.78f),
                            end = Offset(size.width - 40f, size.height * 0.78f),
                            strokeWidth = 2f
                        )
                        strokes.forEach { stroke ->
                            val composePath = androidx.compose.ui.graphics.Path().apply {
                                stroke.points.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
                            }
                            drawPath(composePath, stroke.color, style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        if (currentPoints.size > 1) {
                            val activePath = androidx.compose.ui.graphics.Path().apply {
                                currentPoints.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
                            }
                            drawPath(activePath, selectedPenColor, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            strokes.clear()
                            currentPoints = emptyList()
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, CrimsonRed)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = CrimsonRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ulangi", color = CrimsonRed, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val btm = if (strokes.isNotEmpty()) renderSignatureToBitmap(strokes) else savedSignature
                            onSaveSignatureAndStamp(btm, name, role, chosenStamp)
                            Toast.makeText(context, "Tanda Tangan & Stempel (${chosenStamp.label}) diaktifkan!", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalSapphire)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ImperialGold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan TTD & Cap", color = PureWhite, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. TAB 3: KTP 2-IN-1, SURAT RESMI & STUDIO PASFOTO A4 (3X4, 4X6, 2X3)
// ============================================================================
@Composable
fun SmartTemplatesAndPasfotoScreen(
    savedSignature: Bitmap?,
    signerName: String,
    signerRole: String,
    activeStamp: OfficeStampType,
    onPdfCreated: (String, String, File, String) -> Unit
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableIntStateOf(0) } // 0 = KTP, 1 = Pasfoto 3x4/4x6, 2 = Surat/Invoice

    // State KTP
    var frontIdBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backIdBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var idWatermarkPurpose by remember { mutableStateOf("VERIFIKASI RESMI - JANGAN DISALAHGUNAKAN") }

    val frontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { frontIdBitmap = loadBitmapFromUri(context, it) }
    }
    val backLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { backIdBitmap = loadBitmapFromUri(context, it) }
    }

    // State Studio Pasfoto 3x4 & 4x6
    var pasfotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pasfotoBgColor by remember { mutableStateOf("#DC2626") } // Merah Formal / Biru Resmi
    val pasfotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pasfotoBitmap = loadBitmapFromUri(context, it) }
    }

    // State Surat Resmi
    var companyHeader by remember { mutableStateOf("PANDAKODE SOFTWARE HOUSE") }
    var docNumber by remember { mutableStateOf("001/PK-INV/X/2026") }
    var recipientName by remember { mutableStateOf("Kepada Yth. Mitra Klien") }
    var docSubject by remember { mutableStateOf("SURAT KETERANGAN / INVOICE RESMI") }
    var docBody by remember {
        mutableStateOf(
            "Dengan hormat,\nMelalui dokumen resmi ini kami menyatakan bahwa layanan pengembangan sistem/aplikasi telah diselesaikan sesuai spesifikasi. Dokumen ini dilengkapi dengan Stempel QR Crypto-Seal yang sah."
        )
    }
    var amountValue by remember { mutableStateOf("Rp 2.500.000,-") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 3 Tombol Mode Template
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Triple(0, "KTP 2-in-1", Icons.Default.Badge),
                Triple(1, "Pasfoto 3x4", Icons.Default.Portrait),
                Triple(2, "Surat/Nota", Icons.Default.ReceiptLong)
            ).forEach { (idx, title, icon) ->
                val isSelected = selectedMode == idx
                Button(
                    onClick = { selectedMode = idx },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) CrimsonRed else PureWhite
                    ),
                    border = BorderStroke(1.5.dp, if (isSelected) ImperialGold else SoftRoseBorder),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = if (isSelected) ImperialGold else CrimsonRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(title, color = if (isSelected) PureWhite else CharcoalInk, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        when (selectedMode) {
            0 -> {
                // MODE KTP 2-IN-1
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, ImperialGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🪪 Mode Cetak KTP / Kartu (Depan & Belakang di 1 Lembar A4)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = DeepRuby)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { frontLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.CreditCard, contentDescription = null, tint = CrimsonRed)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (frontIdBitmap != null) "Depan (Siap)" else "Foto Depan", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { backLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.CreditCard, contentDescription = null, tint = RoyalSapphire)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (backIdBitmap != null) "Belakang (Siap)" else "Foto Belakang", fontSize = 12.sp)
                            }
                        }
                        OutlinedTextField(
                            value = idWatermarkPurpose,
                            onValueChange = { idWatermarkPurpose = it },
                            label = { Text("Teks Segel Keamanan Melintang") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val fBtm = frontIdBitmap ?: createSampleIdCardBitmap("BAGIAN DEPAN KARTU / KTP")
                                val bBtm = backIdBitmap ?: createSampleIdCardBitmap("BAGIAN BELAKANG KARTU / KTP")
                                val result = generateIdCardSheetPdf(context, fBtm, bBtm, idWatermarkPurpose)
                                if (result != null) {
                                    val (file, hash) = result
                                    savePdfToDownloads(context, file)
                                    onPdfCreated("KTP_2in1_Verification", "ID Card Sheet", file, hash)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = ImperialGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BUAT & LIHAT PDF KARTU 2-IN-1", color = PureWhite, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            1 -> {
                // FITUR BARU: STUDIO CETAK PASFOTO A4 (2x3, 3x4, 4x6 SEKALIGUS)
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, RoyalSapphire),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("📸 Studio Cetak Pasfoto A4 (Otomatis Susun Ukuran 2x3, 3x4 & 4x6)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = RoyalSapphire)
                        Text("Pilih 1 foto diri Anda, aplikasi akan menyusun paket lengkap pasfoto ukuran 2x3 (4 lembar), 3x4 (4 lembar), dan 4x6 (3 lembar) siap cetak di kertas A4 lengkap dengan garis potong!", fontSize = 12.sp, color = Color.DarkGray)

                        Button(
                            onClick = { pasfotoLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalSapphire),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AccountBox, contentDescription = null, tint = ImperialGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (pasfotoBitmap != null) "Ganti Foto Wajah (Sudah Dipilih)" else "Pilih Foto Wajah dari Galeri", color = PureWhite, fontWeight = FontWeight.Bold)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = pasfotoBgColor == "#DC2626",
                                onClick = { pasfotoBgColor = "#DC2626" },
                                label = { Text("Bingkai Merah Formal", fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = pasfotoBgColor == "#1E40AF",
                                onClick = { pasfotoBgColor = "#1E40AF" },
                                label = { Text("Bingkai Biru Resmi", fontWeight = FontWeight.Bold) }
                            )
                        }

                        Button(
                            onClick = {
                                val photo = pasfotoBitmap ?: createSampleIdCardBitmap("FOTO\n3x4 & 4x6")
                                val result = generatePasfotoSheetPdf(context, photo, pasfotoBgColor)
                                if (result != null) {
                                    val (file, hash) = result
                                    savePdfToDownloads(context, file)
                                    onPdfCreated("Paket_Pasfoto_A4", "Pasfoto 2x3, 3x4, 4x6", file, hash)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.GridOn, contentDescription = null, tint = ImperialGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BUAT LEMBAR PASFOTO A4 SIAP CETAK", color = PureWhite, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            2 -> {
                // MODE SURAT RESMI & KWITANSI
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, CrimsonRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📄 Generator Surat Resmi & Invoice Instan", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = DeepRuby)
                        OutlinedTextField(value = companyHeader, onValueChange = { companyHeader = it }, label = { Text("Nama Perusahaan / Kop Surat") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = docNumber, onValueChange = { docNumber = it }, label = { Text("Nomor Surat/Nota") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = amountValue, onValueChange = { amountValue = it }, label = { Text("Nominal (Opsional)") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        OutlinedTextField(value = recipientName, onValueChange = { recipientName = it }, label = { Text("Kepada / Penerima") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = docSubject, onValueChange = { docSubject = it }, label = { Text("Judul / Perihal Dokumen") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = docBody, onValueChange = { docBody = it }, label = { Text("Isi Paragraf Surat / Rincian") }, modifier = Modifier.fillMaxWidth().height(110.dp), maxLines = 5)

                        Button(
                            onClick = {
                                val result = generateOfficialLetterPdf(
                                    context = context,
                                    header = companyHeader,
                                    number = docNumber,
                                    recipient = recipientName,
                                    subject = docSubject,
                                    body = docBody,
                                    amount = amountValue,
                                    stampType = activeStamp,
                                    signatureBitmap = savedSignature,
                                    signerName = signerName,
                                    signerRole = signerRole
                                )
                                if (result != null) {
                                    val (file, hash) = result
                                    savePdfToDownloads(context, file)
                                    onPdfCreated(docSubject, "Surat/Invoice Resmi", file, hash)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalSapphire),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = ImperialGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CETAK & BUKA PDF SURAT RESMI", color = PureWhite, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 6. TAB 4 BARU: AI OCR (AMBIL TEKS DARI FOTO) & GABUNG BANYAK FILE PDF (MERGER)
// ============================================================================
@Composable
fun AiOcrAndMergePdfScreen(
    signerName: String,
    signerRole: String,
    savedSignature: Bitmap?,
    onPdfCreated: (String, String, File, String) -> Unit
) {
    val context = LocalContext.current
    var extractedText by remember {
        mutableStateOf("Pilih foto kertas/dokumen di atas, maka AI OCR akan mengekstrak seluruh tulisannya ke kotak ini agar bisa diedit, disalin, atau dijadikan PDF baru.")
    }
    var isOcrLoading by remember { mutableStateOf(false) }
    val selectedPdfsToMerge = remember { mutableStateListOf<Uri>() }
    var mergedFileName by remember { mutableStateOf("Gabungan_Dokumen_PandaKode") }

    // Launcher Pilih Gambar untuk AI OCR
    val ocrImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isOcrLoading = true
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        isOcrLoading = false
                        extractedText = visionText.text.ifBlank { "Tidak ditemukan teks pada gambar tersebut." }
                        Toast.makeText(context, "Berhasil mengekstrak teks dengan AI OCR!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        isOcrLoading = false
                        Toast.makeText(context, "Gagal membaca teks: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                isOcrLoading = false
            }
        }
    }

    // Launcher Pilih Banyak File PDF untuk Digabungkan (PDF Merger)
    val mergePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        selectedPdfsToMerge.addAll(uris)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // FITUR 1: SMART AI OCR (BACA TEKS DARI FOTO KERTAS / BUKU)
        Card(
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, RoyalSapphire),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = RoyalSapphire)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("1. Smart AI OCR (Ambil Teks dari Foto Kertas)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = RoyalSapphire)
                        Text("Ubah foto catatan, surat, atau buku menjadi teks yang bisa diedit & disalin.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Button(
                    onClick = { ocrImageLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = ImperialGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ImageSearch, contentDescription = null, tint = CharcoalInk)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOcrLoading) "SEDANG MEMBACA TEKS..." else "PILIH FOTO KERTAS UNTUK DI-SCAN TEKSNYA",
                        color = CharcoalInk,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }

                OutlinedTextField(
                    value = extractedText,
                    onValueChange = { extractedText = it },
                    label = { Text("Hasil Ekstraksi Teks AI OCR (Bisa Diedit)") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    maxLines = 8
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", extractedText))
                            Toast.makeText(context, "Teks disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.5.dp, RoyalSapphire)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = RoyalSapphire, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Teks", color = RoyalSapphire, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val result = generateOfficialLetterPdf(
                                context = context,
                                header = "DOKUMEN HASIL EKSTRAKSI AI OCR",
                                number = "OCR-${System.currentTimeMillis() % 10000}",
                                recipient = "Arsip Digital PandaKode",
                                subject = "SALINAN TEKS DOKUMEN TERVERIFIKASI",
                                body = extractedText,
                                amount = "",
                                stampType = OfficeStampType.VERIFIED,
                                signatureBitmap = savedSignature,
                                signerName = signerName,
                                signerRole = signerRole
                            )
                            if (result != null) {
                                val (file, hash) = result
                                savePdfToDownloads(context, file)
                                onPdfCreated("Dokumen_AI_OCR", "AI OCR to PDF", file, hash)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Jadikan PDF", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // FITUR 2: PDF MERGER (GABUNG BANYAK FILE PDF JADI SATU)
        Card(
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, CrimsonRed),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MergeType, contentDescription = null, tint = CrimsonRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("2. PDF Merger (Gabungkan Banyak File PDF)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = DeepRuby)
                        Text("Pilih beberapa file PDF dari HP Anda untuk disatukan menjadi 1 dokumen PDF utuh.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                OutlinedTextField(
                    value = mergedFileName,
                    onValueChange = { mergedFileName = it },
                    label = { Text("Nama File Hasil Gabungan") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { mergePdfLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.5.dp, CrimsonRed)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = CrimsonRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pilih File PDF (${selectedPdfsToMerge.size})", color = CrimsonRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (selectedPdfsToMerge.isNotEmpty()) {
                        TextButton(onClick = { selectedPdfsToMerge.clear() }) {
                            Text("Reset", color = Color.Red)
                        }
                    }
                }

                Button(
                    onClick = {
                        if (selectedPdfsToMerge.isEmpty()) {
                            Toast.makeText(context, "Pilih minimal 1 atau 2 file PDF terlebih dahulu!", Toast.LENGTH_SHORT).show()
                        } else {
                            val result = mergeMultiplePdfUris(context, selectedPdfsToMerge, mergedFileName)
                            if (result != null) {
                                val (file, hash) = result
                                savePdfToDownloads(context, file)
                                onPdfCreated(mergedFileName, "Merged PDF (${selectedPdfsToMerge.size} File)", file, hash)
                                selectedPdfsToMerge.clear()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalSapphire),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LibraryAddCheck, contentDescription = null, tint = ImperialGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GABUNGKAN SEMUA PDF SEKARANG", color = PureWhite, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ============================================================================
// 7. TAB 5: ARSIP RIWAYAT PDF & IN-APP PDF VIEWER
// ============================================================================
@Composable
fun PdfArchiveAndAboutScreen(
    historyList: List<PdfHistoryItem>,
    onOpenPdfViewer: (File) -> Unit,
    onDelete: (PdfHistoryItem) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, ImperialGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🐼 BambuPDF Super Suite v1.3.0", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = DeepRuby)
                    Text("Official Smart Document Ecosystem by PandaKode", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RoyalSapphire)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Dilengkapi CamScan AI, Text Recognition OCR, Pasfoto Studio, PDF Merger, dan Stempel QR Crypto-Seal SHA-256.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = Color.DarkGray
                    )
                }
            }
        }

        item {
            Text("Arsip Dokumen PDF Anda (${historyList.size})", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = DeepRuby)
        }

        if (historyList.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SoftRoseBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Belum ada dokumen PDF. Silakan gunakan menu 'CamScan', 'KTP & Foto', atau 'OCR & Gabung'.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(historyList, key = { it.id }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SoftRoseBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val file = File(item.filePath)
                            if (file.exists()) onOpenPdfViewer(file)
                        }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(color = SoftBlueBg, shape = RoundedCornerShape(6.dp)) {
                                    Text(item.type, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RoyalSapphire, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = CharcoalInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.timestamp} • Seal ID: #${item.hashCode}", fontSize = 11.sp, color = Color.Gray)
                            }
                            Row {
                                IconButton(onClick = { val file = File(item.filePath); if (file.exists()) onOpenPdfViewer(file) }) {
                                    Icon(Icons.Default.Visibility, contentDescription = "Lihat PDF", tint = RoyalSapphire)
                                }
                                IconButton(onClick = { val file = File(item.filePath); if (file.exists()) sharePdfFile(context, file) }) {
                                    Icon(Icons.Default.Share, contentDescription = "Bagikan", tint = CrimsonRed)
                                }
                                IconButton(onClick = { onDelete(item) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InAppPdfViewerDialog(
    pdfFile: File,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val renderedPages = remember(pdfFile.absolutePath) { renderPdfFileToBitmaps(pdfFile) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = {
            Column {
                Text("Pratinjau Dokumen PDF", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = DeepRuby)
                Text("${pdfFile.name} (${renderedPages.size} Halaman)", fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(420.dp)
            ) {
                itemsIndexed(renderedPages) { index, btm ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Halaman ${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RoyalSapphire)
                        Spacer(modifier = Modifier.height(4.dp))
                        Image(
                            bitmap = btm.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.5.dp, ImperialGold, RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onShare, colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)) {
                Icon(Icons.Default.Share, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bagikan PDF", color = PureWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup", color = RoyalSapphire, fontWeight = FontWeight.Bold) }
        }
    )
}

fun renderPdfFileToBitmaps(file: File): List<Bitmap> {
    val result = mutableListOf<Bitmap>()
    try {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val btm = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(btm)
            canvas.drawColor(android.graphics.Color.WHITE)
            page.render(btm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            result.add(btm)
        }
        renderer.close()
        descriptor.close()
    } catch (e: Exception) {
        // Ignore
    }
    return result
}

// ============================================================================
// 8. ENGINE GENERATOR PDF: MULTI-PAGE, PASFOTO A4, MERGER & STEMPEL KANTOR
// ============================================================================
fun generateMultiPagePdf(
    context: Context,
    fileName: String,
    pages: List<Bitmap>,
    pageSize: PdfPageSize,
    filterMode: DocFilterMode,
    watermark: String,
    includeCryptoSeal: Boolean,
    includePageNumbers: Boolean,
    stampType: OfficeStampType,
    signatureBitmap: Bitmap?,
    signerName: String,
    signerRole: String
): Pair<File, String>? {
    return try {
        val pdfDocument = PdfDocument()
        val pageWidth = pageSize.widthPt
        val pageHeight = pageSize.heightPt
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val shortHash = generateShortHash("$fileName-$signerName-$timestamp")

        pages.forEachIndexed { index, rawBitmap ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)

            val filteredBitmap = applyDocFilter(rawBitmap, filterMode)

            val margin = 32f
            val maxW = pageWidth - (margin * 2)
            val maxH = if (includeCryptoSeal && index == pages.lastIndex) pageHeight - 220f else pageHeight - (margin * 2)
            val scale = minOf(maxW / filteredBitmap.width, maxH / filteredBitmap.height)
            val drawW = filteredBitmap.width * scale
            val drawH = filteredBitmap.height * scale
            val left = (pageWidth - drawW) / 2f
            val top = margin

            canvas.drawBitmap(filteredBitmap, null, RectF(left, top, left + drawW, top + drawH), Paint(Paint.FILTER_BITMAP_FLAG))

            if (watermark.isNotEmpty()) {
                drawDiagonalWatermark(canvas, pageWidth, pageHeight, watermark)
            }

            if (includeCryptoSeal && index == pages.lastIndex) {
                drawCryptoSealFooter(
                    canvas = canvas,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    docTitle = fileName,
                    signerName = signerName,
                    signerRole = signerRole,
                    shortHash = shortHash,
                    timestamp = timestamp,
                    stampType = stampType,
                    signatureBitmap = signatureBitmap
                )
            }

            if (includePageNumbers) {
                val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.GRAY
                    textSize = 10f
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("Halaman ${index + 1} dari ${pages.size}", (pageWidth - 36).toFloat(), (pageHeight - 16).toFloat(), numPaint)
            }

            pdfDocument.finishPage(page)
        }

        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val cleanName = fileName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val outFile = File(dir, "${cleanName}_${shortHash}.pdf")
        FileOutputStream(outFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        Pair(outFile, shortHash)
    } catch (e: Exception) {
        null
    }
}

// Engine Pembuat Paket Pasfoto A4 (2x3, 3x4, 4x6 Lengkap)
fun generatePasfotoSheetPdf(
    context: Context,
    photoBitmap: Bitmap,
    bgHexColor: String
): Pair<File, String>? {
    return try {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val shortHash = generateShortHash("PASFOTO-${System.currentTimeMillis()}")

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#1E40AF")
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("LEMBAR CETAK PASFOTO STUDIO A4 (2x3, 3x4, 4x6 CM)", pageWidth / 2f, 45f, titlePaint)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.parseColor(bgHexColor)
        }
        val cropLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.LTGRAY
            strokeWidth = 1.5f
        }

        fun drawPhotoRow(label: String, wPt: Float, hPt: Float, count: Int, startY: Float) {
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.DKGRAY
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(label, 45f, startY - 10f, labelPaint)
            var currentX = 45f
            for (i in 0 until count) {
                val outerRect = RectF(currentX, startY, currentX + wPt, startY + hPt)
                canvas.drawRect(outerRect, bgPaint)
                val innerRect = RectF(currentX + 4f, startY + 4f, currentX + wPt - 4f, startY + hPt - 4f)
                canvas.drawBitmap(photoBitmap, null, innerRect, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.drawRect(outerRect, cropLinePaint)
                currentX += wPt + 18f
            }
        }

        // Baris 1: Pasfoto 4x6 cm (113 x 170 pt) -> 4 lembar
        drawPhotoRow("Ukuran 4x6 cm (4 Lembar):", 113f, 170f, 4, 85f)

        // Baris 2: Pasfoto 3x4 cm (85 x 113 pt) -> 5 lembar
        drawPhotoRow("Ukuran 3x4 cm (5 Lembar):", 85f, 113f, 5, 300f)

        // Baris 3: Pasfoto 2x3 cm (56 x 85 pt) -> 6 lembar
        drawPhotoRow("Ukuran 2x3 cm (6 Lembar):", 56f, 85f, 6, 460f)

        pdfDocument.finishPage(page)

        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val outFile = File(dir, "Pasfoto_A4_${shortHash}.pdf")
        FileOutputStream(outFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        Pair(outFile, shortHash)
    } catch (e: Exception) {
        null
    }
}

// Engine Penggabung Banyak File PDF (PDF Merger)
fun mergeMultiplePdfUris(
    context: Context,
    uris: List<Uri>,
    mergedTitle: String
): Pair<File, String>? {
    return try {
        val outPdf = PdfDocument()
        val shortHash = generateShortHash("$mergedTitle-${System.currentTimeMillis()}")
        var globalPageNumber = 1

        uris.forEach { uri ->
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val renderer = PdfRenderer(pfd)
                for (i in 0 until renderer.pageCount) {
                    val srcPage = renderer.openPage(i)
                    val btm = Bitmap.createBitmap(srcPage.width * 2, srcPage.height * 2, Bitmap.Config.ARGB_8888)
                    val btmCanvas = Canvas(btm)
                    btmCanvas.drawColor(android.graphics.Color.WHITE)
                    srcPage.render(btm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    val pageInfo = PdfDocument.PageInfo.Builder(srcPage.width, srcPage.height, globalPageNumber++).create()
                    val destPage = outPdf.startPage(pageInfo)
                    destPage.canvas.drawBitmap(
                        btm,
                        null,
                        RectF(0f, 0f, srcPage.width.toFloat(), srcPage.height.toFloat()),
                        Paint(Paint.FILTER_BITMAP_FLAG)
                    )
                    outPdf.finishPage(destPage)
                    srcPage.close()
                }
                renderer.close()
            }
        }

        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val cleanName = mergedTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val outFile = File(dir, "${cleanName}_${shortHash}.pdf")
        FileOutputStream(outFile).use { outPdf.writeTo(it) }
        outPdf.close()
        Pair(outFile, shortHash)
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal menggabungkan PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        null
    }
}

fun applyDocFilter(source: Bitmap, mode: DocFilterMode): Bitmap {
    if (mode == DocFilterMode.ORIGINAL) return source
    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint()

    val matrix = ColorMatrix()
    when (mode) {
        DocFilterMode.MAGIC_COLOR -> {
            val c = 1.42f
            val b = -28f
            matrix.set(
                floatArrayOf(
                    c, 0f, 0f, 0f, b,
                    0f, c, 0f, 0f, b,
                    0f, 0f, c, 0f, b,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        DocFilterMode.BW_SHARP -> {
            matrix.setSaturation(0f)
            val contrast = 1.85f
            val shift = -85f
            val cm = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, shift,
                    0f, contrast, 0f, 0f, shift,
                    0f, 0f, contrast, 0f, shift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.postConcat(cm)
        }
        DocFilterMode.GRAYSCALE -> matrix.setSaturation(0f)
        DocFilterMode.WARM_PAPER -> matrix.setScale(1.06f, 1.01f, 0.86f, 1f)
        else -> {}
    }
    paint.colorFilter = ColorMatrixColorFilter(matrix)
    canvas.drawBitmap(source, 0f, 0f, paint)
    return result
}

fun rotateBitmap90(source: Bitmap): Bitmap {
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

fun generateIdCardSheetPdf(
    context: Context,
    frontBitmap: Bitmap,
    backBitmap: Bitmap,
    securityPurpose: String
): Pair<File, String>? {
    return try {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val shortHash = generateShortHash("IDCARD-$securityPurpose-$timestamp")

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#991B1B")
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("LEMBAR VERIFIKASI IDENTITAS (2-IN-1 SHEET)", pageWidth / 2f, 50f, titlePaint)

        val cardW = 320f
        val cardH = 204f
        val left = (pageWidth - cardW) / 2f

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.parseColor("#F59E0B")
            strokeWidth = 3f
        }

        val frontRect = RectF(left, 90f, left + cardW, 90f + cardH)
        canvas.drawBitmap(frontBitmap, null, frontRect, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawRoundRect(frontRect, 12f, 12f, borderPaint)

        val backRect = RectF(left, 330f, left + cardW, 330f + cardH)
        canvas.drawBitmap(backBitmap, null, backRect, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawRoundRect(backRect, 12f, 12f, borderPaint)

        val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(105, 220, 38, 38)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.save()
        canvas.rotate(-12f, pageWidth / 2f, 200f)
        canvas.drawText("HANYA UNTUK: ${securityPurpose.uppercase()} ($timestamp)", pageWidth / 2f, 195f, sealPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(-12f, pageWidth / 2f, 430f)
        canvas.drawText("HANYA UNTUK: ${securityPurpose.uppercase()} ($timestamp)", pageWidth / 2f, 435f, sealPaint)
        canvas.restore()

        pdfDocument.finishPage(page)

        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val outFile = File(dir, "KTP_2in1_${shortHash}.pdf")
        FileOutputStream(outFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        Pair(outFile, shortHash)
    } catch (e: Exception) {
        null
    }
}

fun generateOfficialLetterPdf(
    context: Context,
    header: String,
    number: String,
    recipient: String,
    subject: String,
    body: String,
    amount: String,
    stampType: OfficeStampType,
    signatureBitmap: Bitmap?,
    signerName: String,
    signerRole: String
): Pair<File, String>? {
    return try {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val timestamp = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        val shortHash = generateShortHash("$header-$number-$timestamp")

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = android.graphics.Color.parseColor("#991B1B")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText(header.uppercase(), 45f, 60f, paint)

        paint.color = android.graphics.Color.parseColor("#1E40AF")
        paint.strokeWidth = 4f
        canvas.drawLine(45f, 75f, (pageWidth - 45).toFloat(), 75f, paint)

        paint.color = android.graphics.Color.parseColor("#1E293B")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        canvas.drawText("Nomor Dokumen : $number", 45f, 110f, paint)
        canvas.drawText("Tanggal Terbit  : $timestamp", 45f, 128f, paint)
        canvas.drawText("Kepada Yth.     : $recipient", 45f, 146f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        canvas.drawText(subject.uppercase(), 45f, 190f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12.5f
        var currentY = 225f
        val words = body.replace("\n", " \n ").split(" ")
        var line = ""
        for (word in words) {
            if (word == "\n") {
                canvas.drawText(line, 45f, currentY, paint)
                line = ""
                currentY += 20f
                continue
            }
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(testLine) > (pageWidth - 90)) {
                canvas.drawText(line, 45f, currentY, paint)
                line = word
                currentY += 20f
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, 45f, currentY, paint)
            currentY += 35f
        }

        if (amount.isNotBlank()) {
            val boxRect = RectF(45f, currentY, (pageWidth - 45).toFloat(), currentY + 55f)
            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.parseColor("#FEF3C7")
            canvas.drawRoundRect(boxRect, 10f, 10f, paint)

            paint.style = Paint.Style.STROKE
            paint.color = android.graphics.Color.parseColor("#F59E0B")
            paint.strokeWidth = 2f
            canvas.drawRoundRect(boxRect, 10f, 10f, paint)

            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.parseColor("#1E293B")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 15f
            canvas.drawText("TOTAL NOMINAL / NILAI : $amount", 65f, currentY + 33f, paint)
        }

        drawCryptoSealFooter(
            canvas = canvas,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            docTitle = subject,
            signerName = signerName,
            signerRole = signerRole,
            shortHash = shortHash,
            timestamp = timestamp,
            stampType = stampType,
            signatureBitmap = signatureBitmap
        )

        pdfDocument.finishPage(page)

        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val outFile = File(dir, "Surat_Resmi_${shortHash}.pdf")
        FileOutputStream(outFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        Pair(outFile, shortHash)
    } catch (e: Exception) {
        null
    }
}

fun drawCryptoSealFooter(
    canvas: Canvas,
    pageWidth: Int,
    pageHeight: Int,
    docTitle: String,
    signerName: String,
    signerRole: String,
    shortHash: String,
    timestamp: String,
    stampType: OfficeStampType,
    signatureBitmap: Bitmap?
) {
    val footerTop = pageHeight - 180f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val sealRect = RectF(36f, footerTop, 330f, pageHeight - 40f)
    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.parseColor("#EFF6FF")
    canvas.drawRoundRect(sealRect, 12f, 12f, paint)

    paint.style = Paint.Style.STROKE
    paint.color = android.graphics.Color.parseColor("#1E40AF")
    paint.strokeWidth = 2f
    canvas.drawRoundRect(sealRect, 12f, 12f, paint)

    val qrContent = "BAMBUPDF-VERIFIED|DOC:$docTitle|BY:$signerName|ROLE:$signerRole|TIME:$timestamp|HASH:$shortHash"
    val qrBtm = generateSealQrBitmap(qrContent)
    if (qrBtm != null) {
        canvas.drawBitmap(qrBtm, null, RectF(46f, footerTop + 14f, 146f, footerTop + 114f), null)
    }

    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.parseColor("#991B1B")
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 10f
    canvas.drawText("BAMBUPDF CRYPTO-SEAL", 156f, footerTop + 32f, paint)

    paint.color = android.graphics.Color.parseColor("#1E293B")
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 9f
    canvas.drawText("Ditandatangani secara digital oleh:", 156f, footerTop + 48f, paint)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(signerName, 156f, footerTop + 62f, paint)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    canvas.drawText(signerRole, 156f, footerTop + 76f, paint)
    canvas.drawText("Waktu: $timestamp", 156f, footerTop + 92f, paint)
    canvas.drawText("Seal Hash: #$shortHash", 156f, footerTop + 106f, paint)

    // Gambar Stempel Kantor Miring (Jika Aktif)
    if (stampType != OfficeStampType.NONE) {
        canvas.save()
        canvas.rotate(-14f, 385f, footerTop + 65f)
        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.parseColor(stampType.hexColor)
            strokeWidth = 3f
        }
        val stampRect = RectF(335f, footerTop + 42f, 455f, footerTop + 82f)
        canvas.drawRoundRect(stampRect, 8f, 8f, stampPaint)

        stampPaint.style = Paint.Style.FILL
        stampPaint.textSize = 11f
        stampPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        stampPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(stampType.label, stampRect.centerX(), stampRect.centerY() + 4f, stampPaint)
        canvas.restore()
    }

    val sigLeft = 370f
    paint.color = android.graphics.Color.parseColor("#1E293B")
    paint.textAlign = Paint.Align.LEFT
    paint.textSize = 10f
    canvas.drawText("Tanda Tangan Resmi:", sigLeft, footerTop + 20f, paint)

    if (signatureBitmap != null) {
        canvas.drawBitmap(
            signatureBitmap,
            null,
            RectF(sigLeft, footerTop + 26f, pageWidth - 45f, footerTop + 105f),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
    }

    paint.strokeWidth = 1.5f
    canvas.drawLine(sigLeft, footerTop + 110f, pageWidth - 45f, footerTop + 110f, paint)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(signerName, sigLeft, footerTop + 124f, paint)
}

fun drawDiagonalWatermark(canvas: Canvas, width: Int, height: Int, text: String) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(45, 220, 38, 38)
        textSize = 42f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    canvas.save()
    canvas.rotate(-35f, width / 2f, height / 2f)
    canvas.drawText(text.uppercase(), width / 2f, height / 2f, paint)
    canvas.restore()
}

fun generateSealQrBitmap(content: String): Bitmap? {
    return try {
        val size = 220
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val btm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val fg = android.graphics.Color.parseColor("#1E40AF")
        val bg = android.graphics.Color.WHITE
        for (x in 0 until size) {
            for (y in 0 until size) {
                btm.setPixel(x, y, if (matrix[x, y]) fg else bg)
            }
        }
        btm
    } catch (e: Exception) {
        null
    }
}

fun renderSignatureToBitmap(strokes: List<SignatureStroke>): Bitmap {
    val width = 500
    val height = 260
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { s ->
        paint.color = s.color.toArgb()
        paint.strokeWidth = s.strokeWidth
        val path = Path()
        s.points.forEachIndexed { idx, pt ->
            if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}

fun createSampleDocumentBitmap(pageNumber: Int): Bitmap {
    val btm = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(btm)
    canvas.drawColor(android.graphics.Color.parseColor("#FFFDF9"))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#991B1B")
        textSize = 25f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("HASIL SCAN CAMSCANNER - HAL $pageNumber", 40f, 80f, paint)
    paint.color = android.graphics.Color.parseColor("#1E293B")
    paint.textSize = 18f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    canvas.drawText("Dokumen ini diproses dengan Filter Magic Color", 40f, 140f, paint)
    canvas.drawText("agar latar kertas tampak bersih dan teks tajam.", 40f, 175f, paint)
    canvas.drawText("Dilengkapi Stempel QR Crypto-Seal di bagian bawah.", 40f, 210f, paint)
    return btm
}

fun createSampleIdCardBitmap(label: String): Bitmap {
    val btm = Bitmap.createBitmap(500, 320, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(btm)
    canvas.drawColor(android.graphics.Color.parseColor("#EFF6FF"))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1E40AF")
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(label, 250f, 160f, paint)
    return btm
}

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (e: Exception) {
        null
    }
}

fun generateShortHash(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.take(4).joinToString("") { "%02X".format(it) }
}

fun savePdfToDownloads(context: Context, file: File) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BambuPDF")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(context, "Tersimpan di folder Downloads/BambuPDF!", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        // Ignore
    }
}

fun sharePdfFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Dokumen PDF Resmi dibuat dengan BambuPDF Super Suite by PandaKode 🐼📄")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan File PDF ke..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal membagikan PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun savePdfHistory(context: Context, list: List<PdfHistoryItem>) {
    val prefs = context.getSharedPreferences("bambu_pdf_prefs", Context.MODE_PRIVATE)
    val arr = JSONArray()
    list.forEach { item ->
        arr.put(
            JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("type", item.type)
                put("filePath", item.filePath)
                put("hashCode", item.hashCode)
                put("timestamp", item.timestamp)
            }
        )
    }
    prefs.edit().putString("history", arr.toString()).apply()
}

fun loadPdfHistory(context: Context): List<PdfHistoryItem> {
    val prefs = context.getSharedPreferences("bambu_pdf_prefs", Context.MODE_PRIVATE)
    val raw = prefs.getString("history", null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        val res = mutableListOf<PdfHistoryItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            res.add(
                PdfHistoryItem(
                    id = o.optLong("id"),
                    title = o.optString("title"),
                    type = o.optString("type"),
                    filePath = o.optString("filePath"),
                    hashCode = o.optString("hashCode"),
                    timestamp = o.optString("timestamp")
                )
            )
        }
        res
    } catch (e: Exception) {
        emptyList()
    }
}
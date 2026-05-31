package com.sorrowblue.kpdfium.sample.android

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorrowblue.kpdfium.PdfDocument
import com.sorrowblue.kpdfium.PdfExtractor
import com.sorrowblue.kpdfium.openDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PdfViewerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var pdfDocument by remember { mutableStateOf<PdfDocument?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Launcher for file picker (PDF files only)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoading = true
                errorMessage = null
                
                // Close previous document if open
                pdfDocument?.close()
                pdfDocument = null
                pageCount = 0
                
                try {
                    // 1. Resolve picked PDF file name
                    selectedFileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex != -1) cursor.getString(nameIndex) else null
                    } ?: uri.lastPathSegment ?: "Unknown File"
                    
                    // 2. Read Uri stream to ByteArray on IO Dispatcher
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalArgumentException("Could not open stream for chosen PDF file.")
                    }
                    
                    // 3. Open PDF document using kpdfium
                    val doc = PdfExtractor.openDocument(bytes)
                    pdfDocument = doc
                    pageCount = doc.pageCount
                } catch (e: Throwable) {
                    e.printStackTrace()
                    errorMessage = e.localizedMessage ?: e.message ?: "Failed to open PDF document."
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Clean up document when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            pdfDocument?.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Simple Top Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { filePickerLauncher.launch("application/pdf") },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "OPEN PDF FILE", fontWeight = FontWeight.Bold)
            }
            
            if (pageCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Pages: $pageCount",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Simple picked file info
        selectedFileName?.let { name ->
            Text(
                text = "File: $name",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val doc = pdfDocument
            val err = errorMessage
            
            if (isLoading) {
                CircularProgressIndicator()
            } else if (err != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text(
                        text = "Error:\n$err",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp
                    )
                }
            } else if (doc != null && pageCount > 0) {
                // Scrollable list displaying all pages asynchronously
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(List(pageCount) { it }) { index ->
                        PdfPageItem(doc = doc, pageIndex = index)
                    }
                }
            } else {
                Text(
                    text = "No PDF loaded.\nTap the button above to choose a file.",
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun PdfPageItem(doc: PdfDocument, pageIndex: Int) {
    var imageState by remember(doc, pageIndex) { mutableStateOf<ImageBitmap?>(null) }
    var errorState by remember(doc, pageIndex) { mutableStateOf<String?>(null) }
    var isPageLoading by remember(doc, pageIndex) { mutableStateOf(true) }

    LaunchedEffect(doc, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                // Fetch page and render directly to png bytes in background
                doc.getPage(pageIndex).use { page ->
                    val bytes = page.renderToPng(scale = 1.5f)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        imageState = bitmap.asImageBitmap()
                    } else {
                        errorState = "Failed to decode page bytes."
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                errorState = e.localizedMessage ?: e.message ?: "Rendering error"
            } finally {
                isPageLoading = false
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Page ${pageIndex + 1}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 500.dp),
                contentAlignment = Alignment.Center
            ) {
                val img = imageState
                val err = errorState
                
                if (isPageLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                } else if (img != null) {
                    Image(
                        bitmap = img,
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                } else if (err != null) {
                    Text(
                        text = "Error rendering page: $err",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

package es.rubenseglop.escanerpdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.android.gms.mlkit.documents.GmsDocumentScanning
import com.google.android.gms.mlkit.documents.GmsDocumentScannerOptions
import com.google.android.gms.mlkit.documents.GmsDocumentScanningResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EscanerPdfApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun EscanerPdfApp() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var documents by remember { mutableStateOf(loadDocuments(context.filesDir)) }
    var message by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }

    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember { GmsDocumentScanning.getClient(scannerOptions) }

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scanning = false
        if (result.resultCode == RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scanResult?.pdf?.uri
            if (pdfUri != null) {
                try {
                    val folder = File(context.filesDir, "documents").apply { mkdirs() }
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val destination = File(folder, "Escaneo_$stamp.pdf")
                    context.contentResolver.openInputStream(pdfUri)?.use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("No se pudo leer el PDF generado")
                    documents = loadDocuments(context.filesDir)
                    message = "Documento guardado correctamente"
                } catch (e: Exception) {
                    message = "No se pudo guardar el PDF: ${e.localizedMessage ?: "error desconocido"}"
                }
            } else {
                message = "No se ha recibido ningún PDF"
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Escáner PDF") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Convierte tus documentos en PDF", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Escanea varias páginas, ajusta el resultado y guarda el documento en tu dispositivo.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            scanning = true
                            message = null
                            scanner.getStartScanIntent(activity)
                                .addOnSuccessListener { intentSender ->
                                    scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                }
                                .addOnFailureListener { error ->
                                    scanning = false
                                    message = "No se pudo iniciar el escáner: ${error.localizedMessage ?: "comprueba Google Play Services"}"
                                }
                        },
                        enabled = !scanning
                    ) {
                        Text(if (scanning) "Preparando escáner…" else "Escanear documento")
                    }
                }
            }

            if (message != null) {
                Text(message!!, color = MaterialTheme.colorScheme.secondary)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Documentos recientes", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Text("${documents.size}", style = MaterialTheme.typography.labelLarge)
            }

            if (documents.isEmpty()) {
                Text("Todavía no has guardado ningún documento.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(documents, key = { it.absolutePath }) { file ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null
                                )
                                Column(
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                                ) {
                                    Text(file.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        android.text.format.Formatter.formatShortFileSize(context, file.length()),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                androidx.compose.material3.IconButton(onClick = {
                                    sharePdf(context, file)
                                }) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Compartir PDF"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadDocuments(filesDir: File): List<File> {
    return File(filesDir, "documents")
        .listFiles { file -> file.isFile && file.extension.equals("pdf", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

private fun sharePdf(context: android.content.Context, file: File) {
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir documento"))
}

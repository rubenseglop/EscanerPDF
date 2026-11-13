package es.rubenseglop.escanerpdf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.print.PrintManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

private data class PdfInfo(val file: File, val ocrAvailable: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EscanerPdfApp() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var documents by remember { mutableStateOf(loadDocuments(context.filesDir)) }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var busyFile by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var previewTarget by remember { mutableStateOf<File?>(null) }
    var editTarget by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var darkTheme by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var autoOcr by remember { mutableStateOf(prefs.getBoolean("auto_ocr", false)) }
    var settingsOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

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
        if (result.resultCode == Activity.RESULT_OK) {
            val pdfUri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri
            if (pdfUri != null) {
                try {
                    val folder = File(context.filesDir, "documents").apply { mkdirs() }
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val destination = File(folder, "Escaneo_$stamp.pdf")
                    context.contentResolver.openInputStream(pdfUri)?.use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("No se pudo leer el PDF")
                    refreshDocuments(context.filesDir) { documents = it }
                    message = "PDF guardado correctamente"
                    if (autoOcr) runOcr(destination, context, scope, { busyFile = it }, { busyFile = null }) { message = it }
                } catch (e: Exception) {
                    message = "Error al guardar: ${e.localizedMessage}"
                }
            }
        }
    }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Escáner PDF") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Configuración")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        scanning = true
                        message = null
                        scanner.getStartScanIntent(activity)
                            .addOnSuccessListener { sender ->
                                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            }
                            .addOnFailureListener {
                                scanning = false
                                message = "No se pudo iniciar el escáner. Comprueba Google Play Services."
                            }
                    },
                    icon = { Icon(Icons.Default.DocumentScanner, null) },
                    text = { Text(if (scanning) "Preparando…" else "Escanear") },
                    expanded = true
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tus documentos, en PDF", style = MaterialTheme.typography.titleMedium)
                            Text("Escanea varias páginas, corrige bordes y aplica filtros.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (message != null) {
                    Text(message!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar por nombre o texto reconocido…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Limpiar") } },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mis documentos", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Text("${documents.size} PDF", style = MaterialTheme.typography.labelMedium)
                }
                val filtered = remember(documents, search) {
                    if (search.isBlank()) documents else documents.filter { file ->
                        file.name.contains(search, true) ||
                            File(file.parentFile, file.nameWithoutExtension + ".txt").takeIf { it.exists() }
                                ?.readText()?.contains(search, true) == true
                    }
                }
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(8.dp))
                            Text(if (documents.isEmpty()) "Aún no tienes documentos" else "No hay resultados")
                            Text("Pulsa Escanear para crear tu primer PDF", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.absolutePath }) { file ->
                            DocumentCard(
                                file = file,
                                busy = busyFile == file.name,
                                onOpen = { previewTarget = file },
                                onShare = { sharePdf(context, file) },
                                onRename = { renameTarget = file; renameText = file.nameWithoutExtension },
                                onDelete = { deleteTarget = file },
                                onOcr = { runOcr(file, context, scope, { busyFile = it }, { busyFile = null }) { message = it } },
                                onPrint = { printPdf(context, file) },
                                onEdit = { editTarget = file }
                            )
                        }
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar documento") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nombre del archivo") },
                    suffix = { Text(".pdf") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget!!
                    val safe = renameText.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").trim('.')
                    if (safe.isNotBlank()) {
                        val destination = File(target.parentFile, "$safe.pdf")
                        if (destination.exists()) message = "Ya existe un documento con ese nombre"
                        else if (target.renameTo(destination)) {
                            File(target.parentFile, target.nameWithoutExtension + ".txt").takeIf { it.exists() }
                                ?.renameTo(File(target.parentFile, safe + ".txt"))
                            refreshDocuments(context.filesDir) { documents = it }
                            renameTarget = null
                        } else message = "No se pudo renombrar el documento"
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } }
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar documento") },
            text = { Text("¿Quieres eliminar ${deleteTarget!!.name}? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    val file = deleteTarget!!
                    val txt = File(file.parentFile, file.nameWithoutExtension + ".txt")
                    val deleted = file.delete()
                    txt.delete()
                    deleteTarget = null
                    refreshDocuments(context.filesDir) { documents = it }
                    message = if (deleted) "Documento eliminado" else "No se pudo eliminar el documento"
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Configuración") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tema oscuro")
                            Text("Usar una apariencia oscura", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = darkTheme, onCheckedChange = {
                            darkTheme = it
                            prefs.edit().putBoolean("dark", it).apply()
                        })
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("OCR automático")
                            Text("Reconocer texto al terminar cada escaneo", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = autoOcr, onCheckedChange = {
                            autoOcr = it
                            prefs.edit().putBoolean("auto_ocr", it).apply()
                        })
                    }
                    Text("Los PDF y el texto reconocido se guardan únicamente en este dispositivo.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Listo") } }
        )
    }

    if (editTarget != null) {
        PdfPageEditorDialog(
            file = editTarget!!,
            onDismiss = { editTarget = null },
            onSaved = {
                refreshDocuments(context.filesDir) { documents = it }
                message = "Páginas actualizadas correctamente. Vuelve a ejecutar OCR para renovar el texto reconocido."
            }
        )
    }

    if (previewTarget != null) {
        PdfPreviewDialog(file = previewTarget!!, onDismiss = { previewTarget = null }, onShare = { sharePdf(context, it) }, onPrint = { printPdf(context, it) })
    }
}

@Composable
private fun DocumentCard(
    file: File,
    busy: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOcr: () -> Unit,
    onPrint: () -> Unit,
    onEdit: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val ocrExists = remember(file.lastModified()) { File(file.parentFile, file.nameWithoutExtension + ".txt").exists() }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.padding(12.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.nameWithoutExtension, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "${android.text.format.Formatter.formatShortFileSize(LocalContext.current, file.length())} · ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ocrExists) Text("Texto reconocido", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 5.dp))
            }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Compartir") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Abrir / Vista previa") }, leadingIcon = { Icon(Icons.Default.Visibility, null) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Editar páginas") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Reconocer texto (OCR)") }, leadingIcon = { Icon(Icons.Default.TextFields, null) }, onClick = { menu = false; onOcr() }, enabled = !busy)
                    DropdownMenuItem(text = { Text("Imprimir") }, leadingIcon = { Icon(Icons.Default.Print, null) }, onClick = { menu = false; onPrint() })
                    DropdownMenuItem(text = { Text("Renombrar") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun PdfPreviewDialog(file: File, onDismiss: () -> Unit, onShare: (File) -> Unit, onPrint: (File) -> Unit) {
    var page by remember(file) { mutableIntStateOf(0) }
    var pageCount by remember(file) { mutableIntStateOf(0) }
    var bitmap by remember(file, page) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    LaunchedEffect(file, page) {
        withContext(Dispatchers.IO) {
            try {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(descriptor).use { renderer ->
                    pageCount = renderer.pageCount
                    if (page < renderer.pageCount) {
                        renderer.openPage(page).use { pdfPage ->
                            val scale = (1200f / pdfPage.width).coerceAtMost(2.5f)
                            val bmp = Bitmap.createBitmap((pdfPage.width * scale).toInt(), (pdfPage.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap = bmp
                        }
                    }
                }
            } catch (e: Exception) { error = e.localizedMessage ?: "No se pudo abrir el PDF" }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.nameWithoutExtension, maxLines = 1) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) {
                    Image(bitmap!!.asImageBitmap(), contentDescription = "Página ${page + 1}", modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp))
                } else if (error != null) Text(error!!)
                else CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Icon(Icons.Default.ChevronLeft, "Anterior") }
                    Text(if (pageCount > 0) "Página ${page + 1} de $pageCount" else "Cargando…")
                    IconButton(onClick = { if (page < pageCount - 1) page++ }, enabled = page < pageCount - 1) { Icon(Icons.Default.ChevronRight, "Siguiente") }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onPrint(file) }) { Text("Imprimir") }
                TextButton(onClick = { onShare(file) }) { Text("Compartir") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    )
}

private fun runOcr(file: File, context: Context, scope: kotlinx.coroutines.CoroutineScope, onBusy: (String) -> Unit, onDone: () -> Unit, onMessage: (String) -> Unit) {
    onBusy(file.name)
    scope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                val output = StringBuilder()
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(descriptor).use { renderer ->
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { page ->
                                val scale = (1800f / page.width).coerceAtMost(3f)
                                val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                                output.append("\n--- Página ${index + 1} ---\n").append(result.text).append("\n")
                                bitmap.recycle()
                            }
                        }
                    } finally { recognizer.close() }
                }
                output.toString()
            }
            File(file.parentFile, file.nameWithoutExtension + ".txt").writeText(text)
            onMessage(if (text.isBlank()) "OCR terminado: no se ha detectado texto" else "Texto reconocido y guardado. Ya puedes buscar dentro del documento.")
        } catch (e: Exception) {
            onMessage("No se pudo reconocer el texto: ${e.localizedMessage ?: "error"}")
        } finally { onDone() }
    }
}

private fun loadDocuments(filesDir: File): List<File> =
    File(filesDir, "documents").listFiles { f -> f.isFile && f.extension.equals("pdf", true) }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()

private fun refreshDocuments(filesDir: File, update: (List<File>) -> Unit) { update(loadDocuments(filesDir)) }

private fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir documento"))
}

private fun printPdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir PDF para imprimir"))
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "No hay ninguna aplicación para imprimir PDF", android.widget.Toast.LENGTH_LONG).show()
    }
}

package es.rubenseglop.escanerpdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@Composable
internal fun PdfPageEditorDialog(file: File, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folder = remember(file) { File(file.parentFile, file.nameWithoutExtension + ".pages") }
    var pages by remember(file) { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        try { pages = withContext(Dispatchers.IO) { preparePageImages(file, folder) } }
        catch (e: Exception) { error = e.localizedMessage ?: "No se pudieron cargar las páginas" }
        finally { loading = false }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            saving = true
            scope.launch {
                try {
                    val added = withContext(Dispatchers.IO) {
                        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                            ?: throw IllegalStateException("No se pudo abrir la imagen")
                        val output = File(folder, "added_\${System.nanoTime()}.png")
                        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                        output
                    }
                    pages = pages + added
                } catch (e: Exception) { error = e.localizedMessage ?: "No se pudo añadir la imagen" }
                finally { saving = false }
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!saving) {
                if (!saved) folder.deleteRecursively()
                onDismiss()
            }
        },
        title = { Text("Editar páginas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Organiza las páginas de \${file.name}. Puedes añadir imágenes desde la galería.", style = MaterialTheme.typography.bodySmall)
                if (loading || saving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(if (loading) " Preparando páginas…" else " Procesando…")
                    }
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                if (!loading) {
                    Text("\${pages.size} páginas", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(pages, key = { _, page -> page.absolutePath }) { index, pageFile ->
                            PageEditorRow(
                                index, pageFile, index > 0, index < pages.lastIndex,
                                onMoveUp = { pages = pages.toMutableList().also { val p = it.removeAt(index); it.add(index - 1, p) } },
                                onMoveDown = { pages = pages.toMutableList().also { val p = it.removeAt(index); it.add(index + 1, p) } },
                                onDelete = { pages = pages.toMutableList().also { it.removeAt(index) } }
                            )
                        }
                    }
                    OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), enabled = !saving) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Text(" Añadir imagen desde galería")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { rebuildPdfFromPages(file, folder, pages) }
                            saved = true
                            onSaved()
                            onDismiss()
                        } catch (e: Exception) { error = e.localizedMessage ?: "No se pudo guardar el PDF" }
                        finally { saving = false }
                    }
                },
                enabled = !loading && !saving && pages.isNotEmpty()
            ) { Text(if (saving) "Guardando…" else "Guardar cambios") }
        },
        dismissButton = {
            TextButton(onClick = { if (!saving) { if (!saved) folder.deleteRecursively(); onDismiss() } }, enabled = !saving) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun PageEditorRow(
    index: Int, pageFile: File, canMoveUp: Boolean, canMoveDown: Boolean,
    onMoveUp: () -> Unit, onMoveDown: () -> Unit, onDelete: () -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, pageFile.absolutePath) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(pageFile.absolutePath) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Página \${index + 1}", Modifier.size(58.dp, 76.dp))
        else Icon(Icons.Default.PictureAsPdf, null, Modifier.size(58.dp, 76.dp))
        Text("Página \${index + 1}", Modifier.weight(1f).padding(start = 10.dp))
        IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Default.ArrowUpward, "Subir página") }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Default.ArrowDownward, "Bajar página") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar página", tint = MaterialTheme.colorScheme.error) }
    }
}

private fun preparePageImages(pdf: File, folder: File): List<File> {
    folder.mkdirs()
    val existing = folder.listFiles { f -> f.isFile && f.extension.equals("png", true) }?.sortedBy { it.name } ?: emptyList()
    if (existing.isNotEmpty()) return existing
    val descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
    PdfRenderer(descriptor).use { renderer ->
        for (index in 0 until renderer.pageCount) {
            renderer.openPage(index).use { page ->
                val width = 1500
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                FileOutputStream(File(folder, String.format(Locale.US, "page_%04d.png", index + 1))).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }
    return folder.listFiles { f -> f.isFile && f.extension.equals("png", true) }?.sortedBy { it.name } ?: emptyList()
}

private fun rebuildPdfFromPages(original: File, folder: File, pages: List<File>) {
    require(pages.isNotEmpty()) { "El documento debe contener al menos una página" }
    val document = PdfDocument()
    try {
        pages.forEachIndexed { index, imageFile ->
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: error("No se pudo leer la página \${index + 1}")
            val info = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
            val page = document.startPage(info)
            val scale = minOf(595f / bitmap.width, 842f / bitmap.height)
            val width = (bitmap.width * scale).toInt()
            val height = (bitmap.height * scale).toInt()
            val left = (595 - width) / 2
            val top = (842 - height) / 2
            page.canvas.drawColor(android.graphics.Color.WHITE)
            page.canvas.drawBitmap(bitmap, null, Rect(left, top, left + width, top + height), Paint(Paint.FILTER_BITMAP_FLAG))
            document.finishPage(page)
            bitmap.recycle()
        }
        val temp = File(original.parentFile, original.nameWithoutExtension + ".tmp.pdf")
        FileOutputStream(temp).use { document.writeTo(it) }
        val backup = File(original.parentFile, original.name + ".bak")
        if (backup.exists()) backup.delete()
        check(original.renameTo(backup)) { "No se pudo proteger el PDF original" }
        if (!temp.renameTo(original)) {
            backup.renameTo(original)
            error("No se pudo sustituir el PDF original")
        }
        backup.delete()
        File(original.parentFile, original.nameWithoutExtension + ".txt").delete()

        folder.deleteRecursively()

    } finally { document.close() }
}

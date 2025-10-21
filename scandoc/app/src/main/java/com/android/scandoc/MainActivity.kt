package com.android.scandoc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DocumentScannerScreen(cameraExecutor)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun DocumentScannerScreen(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var recognizedText by remember { mutableStateOf("") }
    var nom by remember { mutableStateOf("") }
    var prenom by remember { mutableStateOf("") }
    var numeroCin by remember { mutableStateOf("") }
    var dateNaissance by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var showCamera by remember { mutableStateOf(false) }
    var showGalleryLauncher by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val contentResolver = context.contentResolver

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val inputStream: InputStream? = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    recognizedText = visionText.text
                    val data = extractFieldSmart(visionText.text)
                    nom = data["nom"] ?: ""
                    prenom = data["prenom"] ?: ""
                    numeroCin = data["cin"] ?: ""
                    dateNaissance = data["dateNaissance"] ?: ""
                }
                .addOnFailureListener {
                    it.printStackTrace()
                }
        }
    }

    fun sendEmail() {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            message = "Veuillez entrer une adresse email valide."
            return
        }
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, "Informations extraites du document")
            putExtra(
                Intent.EXTRA_TEXT,
                "Nom: $nom\nPrénom: $prenom\nCIN: $numeroCin\nDate de Naissance: $dateNaissance"
            )
        }
        context.startActivity(Intent.createChooser(emailIntent, "Envoyer un email via:"))
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { showCamera = true }, modifier = Modifier.weight(1f)) {
                Text("Prendre une photo")
            }
            Button(onClick = { showGalleryLauncher = true }, modifier = Modifier.weight(1f)) {
                Text("Choisir depuis la galerie")
            }
        }

        if (showCamera) {
            CameraPreview(
                imageCapture = imageCapture,
                cameraExecutor = cameraExecutor,
                lifecycleOwner = lifecycleOwner,
                onImageCaptured = { imageProxy ->
                    processImageProxy(imageProxy) { text ->
                        recognizedText = text
                        val data = extractFieldSmart(text)
                        nom = data["nom"] ?: ""
                        prenom = data["prenom"] ?: ""
                        numeroCin = data["cin"] ?: ""
                        dateNaissance = data["dateNaissance"] ?: ""
                    }
                    showCamera = false
                }
            )
        }

        if (showGalleryLauncher) {
            LaunchedEffect(Unit) {
                galleryLauncher.launch("image/*")
                showGalleryLauncher = false
            }
        }

        OutlinedTextField(value = nom, onValueChange = { nom = it }, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = prenom, onValueChange = { prenom = it }, label = { Text("Prénom") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = numeroCin, onValueChange = { numeroCin = it }, label = { Text("CIN") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = dateNaissance, onValueChange = { dateNaissance = it }, label = { Text("Date de naissance") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email du destinataire") }, leadingIcon = { Icon(Icons.Filled.Email, null) }, modifier = Modifier.fillMaxWidth())

        Button(onClick = { sendEmail() }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Icon(Icons.Filled.Email, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Envoyer les données par email")
        }

        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Text("Texte brut reconnu:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(recognizedText, modifier = Modifier.fillMaxWidth().padding(8.dp))
    }
}

@Composable
fun CameraPreview(
    imageCapture: ImageCapture,
    cameraExecutor: ExecutorService,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onImageCaptured: (ImageProxy) -> Unit,
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(key1 = lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null

        val cameraProviderListener = Runnable {
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }

        cameraProviderFuture.addListener(cameraProviderListener, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                imageCapture.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                            onImageCaptured(imageProxy)
                            imageProxy.close()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            exception.printStackTrace()
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Prendre une photo et extraire le texte")
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(imageProxy: ImageProxy, onTextExtracted: (String) -> Unit) {
    val mediaImage = imageProxy.image ?: return
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            onTextExtracted(visionText.text)
        }
        .addOnFailureListener {
            it.printStackTrace()
        }
}

private fun extractFieldSmart(text: String): Map<String, String> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    var nom = ""
    var prenom = ""
    var cin = ""
    var date = ""

    for (line in lines) {
        val lower = line.lowercase()

        // Cas : "Nom : RABE Prénom : Heri" ou "Anarana : RABE Fanampin'anarana : Heri"
        if ((lower.contains("nom") || lower.contains("anarana")) &&
            (lower.contains("prénom") || lower.contains("fanampin'anarana")) &&
            line.contains(":")
        ) {
            val regexFr = Regex("""(?i)nom\s*:\s*(\w+)[\s,]+prénom\s*:\s*(\w+)""")
            val regexMg = Regex("""(?i)anarana\s*:\s*(\w+)[\s,]+fanampin'anarana\s*:\s*(\w+)""")
            val matchFr = regexFr.find(line)
            val matchMg = regexMg.find(line)
            when {
                matchFr != null -> {
                    nom = matchFr.groupValues[1]
                    prenom = matchFr.groupValues[2]
                    continue
                }
                matchMg != null -> {
                    nom = matchMg.groupValues[1]
                    prenom = matchMg.groupValues[2]
                    continue
                }
            }

            // Cas alternatif : "Nom et prénom : RABE Heri" ou "Anarana sy Fanampin'anarana : RABE Heri"
            val altFr = "nom et prénom"
            val altMg = "anarana sy fanampin'anarana"
            if (lower.startsWith(altFr) || lower.startsWith(altMg)) {
                val fullLine = line.substringAfter(":").trim().split(" ")
                if (fullLine.size >= 2) {
                    nom = fullLine[0]
                    prenom = fullLine.subList(1, fullLine.size).joinToString(" ")
                    continue
                }
            }
        }

        // Cas : "Nom : RABE" ou "Anarana : RABE"
        if ((lower.startsWith("nom") || lower.startsWith("anarana")) && nom.isEmpty()) {
            nom = line.substringAfter(":").trim().split(" ").first()
        }

        // Cas : "Prénom : Heri" ou "Fanampin'anarana : Heri"
        if ((lower.startsWith("prénom") || lower.startsWith("fanampin'anarana")) && prenom.isEmpty()) {
            prenom = line.substringAfter(":").trim()
        }

        // Cas : CIN (reste CIN ou 12 chiffres)
        if (lower.contains("cin") && cin.isEmpty()) {
            cin = Regex("""\d{12}""").find(line)?.value ?: ""
        }

        // Cas : Date de naissance ou Teraka
        if ((lower.contains("date de naissance") || lower.contains("teraka")) && date.isEmpty()) {
            date = line.substringAfter(":").trim()
        }
    }

    // Appel de la méthode fallback si les champs sont vides ou peu fiables
    if (nom.isBlank() || prenom.isBlank() || cin.isBlank() || date.isBlank()) {
        val fallback = extractFieldFallback(lines)
        if (nom.isBlank()) nom = fallback["nom"].orEmpty()
        if (prenom.isBlank()) prenom = fallback["prenom"].orEmpty()
        if (cin.isBlank()) cin = fallback["cin"].orEmpty()
        if (date.isBlank()) date = fallback["dateNaissance"].orEmpty()
    }

    return mapOf(
        "nom" to nom,
        "prenom" to prenom,
        "cin" to cin,
        "dateNaissance" to date
    )
}


private fun extractFieldFallback(lines: List<String>): Map<String, String> {
    var nom = ""
    var prenom = ""
    var cin = ""
    var date = ""

    // Recherche d'une date au format JJ/MM/AAAA ou JJ-MM-AAAA
    date = lines.firstOrNull { Regex("""\b\d{2}[/-]\d{2}[/-]\d{4}\b""").containsMatchIn(it) }
        ?.let { Regex("""\b\d{2}[/-]\d{2}[/-]\d{4}\b""").find(it)?.value } ?: ""

    // Recherche du CIN : 12 chiffres consécutifs ou avec espaces
    for (line in lines) {
        val cleaned = line.replace(" ", "")
        val match = Regex("""\b\d{12}\b""").find(cleaned)
        if (match != null) {
            cin = match.value
            break
        }
    }

    // Trouver la ligne du nom (mot tout en majuscules)
    val nomLineIndex = lines.indexOfFirst { line ->
        // On vérifie si la ligne contient au moins un mot en majuscule total (>=3 lettres)
        line.split(Regex("""\s+""")).any { word ->
            word.length >= 3 && word.all { ch -> ch.isLetter() } && word == word.uppercase()
        }
    }

    if (nomLineIndex != -1) {
        val nomLine = lines[nomLineIndex]
        // Extraire le premier mot tout en majuscules comme nom
        val nomWord = nomLine.split(Regex("""\s+""")).firstOrNull { w ->
            w.length >= 3 && w.all { ch -> ch.isLetter() } && w == w.uppercase()
        }
        if (nomWord != null) nom = nomWord

        // Le prénom = concat de toutes les lignes suivantes "propres", jusqu'à une ligne vide ou date
        val prenomLines = mutableListOf<String>()
        for (i in (nomLineIndex + 1) until lines.size) {
            val l = lines[i].trim()
            if (l.isEmpty()) break
            // Stop si la ligne ressemble à une date ou contient beaucoup de chiffres
            if (Regex("""\b\d{2}[/-]\d{2}[/-]\d{4}\b""").matches(l) || l.any { it.isDigit() }) break

            prenomLines.add(l)
        }

        // Corriger la casse du prénom
        prenom = prenomLines.joinToString(" ")
            .lowercase()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    return mapOf(
        "nom" to nom,
        "prenom" to prenom,
        "cin" to cin,
        "dateNaissance" to date
    )
}






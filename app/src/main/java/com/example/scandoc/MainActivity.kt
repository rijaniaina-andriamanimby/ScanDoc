package com.example.scandoc

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var extractBtn: Button
    private lateinit var nameField: EditText
    private lateinit var ocrResult: EditText
    private val PICK_IMAGE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        extractBtn = findViewById(R.id.extractBtn)
        nameField = findViewById(R.id.nameField)
        ocrResult = findViewById(R.id.ocrResult)

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)

        imageView.setOnClickListener {
            val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            startActivityForResult(gallery, PICK_IMAGE)
        }

        extractBtn.setOnClickListener {
            imageView.drawable?.let {
                val bitmap = imageView.drawable.toBitmap()
                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        ocrResult.setText(visionText.text)
                        val name = Regex("Nom[:\s]*(\w+)").find(visionText.text)?.groupValues?.get(1) ?: ""
                        nameField.setText(name)
                    }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == PICK_IMAGE) {
            val imageUri: Uri? = data?.data
            imageUri?.let {
                val source = ImageDecoder.createSource(this.contentResolver, it)
                val bitmap = ImageDecoder.decodeBitmap(source)
                imageView.setImageBitmap(bitmap)
            }
        }
    }
}
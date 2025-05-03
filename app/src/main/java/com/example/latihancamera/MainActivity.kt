package com.example.latihancamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import com.example.latihancamera.ml.Model
import com.example.latihancamera.ml.Models
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder


class MainActivity : AppCompatActivity() {
    var camera: Button? = null
    var gallery: Button? = null
    var imageView: ImageView? = null
    var result: TextView? = null
    // Change imageSize back to 32
    var imageSize: Int = 32  // Changed from 224 to 32
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//      xml data
        camera = findViewById(R.id.button)
        gallery = findViewById(R.id.button2)
        result = findViewById(R.id.result)
        imageView = findViewById(R.id.imageView)


        camera?.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, 3)
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
            }
        }
        gallery?.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, 2)
        }
    } 



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                2 -> {
                    try {
                        val selectedImage = data?.data
                        var image = MediaStore.Images.Media.getBitmap(this.contentResolver, selectedImage)
                        imageView?.setImageBitmap(image)
                        
                        image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)
                        classifyImage(image)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                3 -> {
                    val image = data?.extras?.get("data") as Bitmap
                    val dimension = Math.min(image.width, image.height)
                    var processedImage = ThumbnailUtils.extractThumbnail(image, dimension, dimension)
                    imageView?.setImageBitmap(processedImage)

                    processedImage = Bitmap.createScaledBitmap(processedImage, imageSize, imageSize, false)
                    classifyImage(processedImage)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun classifyImage(image: Bitmap) {
        try {
            val model = Models.newInstance(applicationContext)
//            val model = Model.newInstance(applicationContext)
            
            // Creates inputs for reference.
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 32, 32, 3), DataType.FLOAT32)
            val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
            byteBuffer.order(ByteOrder.nativeOrder())
            
            val intValues = IntArray(imageSize * imageSize)
            image.getPixels(intValues, 0, image.width, 0, 0, image.width, image.height)
            var pixel = 0
            
            // iterate over each pixel and extract R, G, and B values. Add those values individually to the byte buffer.
            for (i in 0 until imageSize) {
                for (j in 0 until imageSize) {
                    val `val` = intValues[pixel++] // RGB
                    byteBuffer.putFloat((((`val` shr 16) and 0xFF) * (1f / 1))) // Seharusnya 1f/255f
                    byteBuffer.putFloat((((`val` shr 8) and 0xFF) * (1f / 1)))  // Seharusnya 1f/255f
                    byteBuffer.putFloat(((`val` and 0xFF) * (1f / 1)))          // Seharusnya 1f/255f
                }
            }
            
            inputFeature0.loadBuffer(byteBuffer)
            
            // Runs model inference and gets result.
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            
            val confidences = outputFeature0.floatArray
            var maxPos = 0
            var maxConfidence = 0f
            for (i in confidences.indices) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i]
                    maxPos = i
                }
            }

            // Update the classes array to match your model's output size
            val classes = listOf(
                "Apple braeburn", "Wortel", "Timun", "Terong Panjang", "Pear"
            )

            // Tambahkan threshold untuk confidence
            val threshold = 0.6f
            if (maxConfidence < threshold) {
                result?.text = "Bukan buah yang dikenali\nKepercayaan: ${(maxConfidence * 10).toInt()}%"
            } else {
                if (maxPos < classes.size) {
                    result?.text = "${classes[maxPos]}\nKepercayaan: ${(maxConfidence * 10).toInt()}%"
                } else {
                    result?.text = "Tidak dikenali (index: $maxPos)"
                }
            }
            
            model.close()
        } catch (e: Exception) {
            result?.text = "Error: ${e.message}"
            e.printStackTrace()
        }
    }
}
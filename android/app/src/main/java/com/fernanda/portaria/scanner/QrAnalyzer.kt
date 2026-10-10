package com.fernanda.portaria.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

class QrAnalyzer(
    private val onCode: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private val busy = AtomicBoolean(false)
    private var lastCode: String? = null
    private var lastAt = 0L

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { codes ->
                val raw = codes.firstOrNull {
                    it.format == Barcode.FORMAT_QR_CODE || it.rawValue != null
                }?.rawValue
                if (raw != null) {
                    val now = System.currentTimeMillis()
                    if (raw != lastCode || now - lastAt > 2500) {
                        lastCode = raw
                        lastAt = now
                        onCode(raw)
                    }
                }
            }
            .addOnCompleteListener {
                busy.set(false)
                imageProxy.close()
            }
    }
}

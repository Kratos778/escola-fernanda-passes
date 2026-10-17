package com.fernanda.portaria.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
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
                val raw = codes
                    .mapNotNull { it.rawValue }
                    .maxByOrNull { score(it) }
                if (raw != null) {
                    val now = System.currentTimeMillis()
                    if (raw != lastCode || now - lastAt > 2500L) {
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

    private fun score(v: String): Int {
        val t = v.trim()
        return when {
            t.matches(Regex("^[A-Za-z]{1,3}-\\d+$")) -> 100
            t.matches(Regex("^[a-fA-F0-9]{32}$")) -> 90
            t.matches(Regex("^\\d{1,9}$")) -> 80
            t.contains("ESCOLA", ignoreCase = true) -> 70
            else -> t.length.coerceAtMost(50)
        }
    }
}

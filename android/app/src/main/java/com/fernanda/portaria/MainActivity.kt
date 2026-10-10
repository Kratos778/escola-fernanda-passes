package com.fernanda.portaria

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fernanda.portaria.data.RespostaLeitura
import com.fernanda.portaria.data.SupabaseRepo
import com.fernanda.portaria.data.TipoMovimento
import com.fernanda.portaria.scanner.QrAnalyzer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repo: SupabaseRepo
    private val pedirCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SupabaseRepo(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pedirCamera.launch(Manifest.permission.CAMERA)
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF238F76), secondary = Color(0xFF136650))) {
                AppPortaria(repo)
            }
        }
    }
}

@Composable
fun AppPortaria(repo: SupabaseRepo) {
    var logado by remember { mutableStateOf(repo.sessaoAtiva()) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (!logado) {
        Box(Modifier.fillMaxSize().background(Color(0xFFE3F1ED)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Portaria \u2014 Escola Fernanda", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF136650))
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pass, { pass = it }, label = { Text("Palavra-passe") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                erro?.let { Text(it, color = Color.Red, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    loading = true; erro = null
                    scope.launch {
                        repo.login(email.trim(), pass).onSuccess { logado = true }.onFailure { erro = it.message ?: "Falha" }
                        loading = false
                    }
                }, enabled = !loading && email.isNotBlank() && pass.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (loading) "A entrar\u2026" else "Entrar")
                }
            }
        }
        return
    }
    ScannerScreen(repo) { scope.launch { repo.logout(); logado = false } }
}

@Composable
fun ScannerScreen(repo: SupabaseRepo, onLogout: () -> Unit) {
    var modo by remember { mutableStateOf(TipoMovimento.entrada) }
    var ultima by remember { mutableStateOf<RespostaLeitura?>(null) }
    var processando by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(repo.temInternet()) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        while (true) {
            online = repo.temInternet()
            if (online) repo.sincronizarPendentes()
            kotlinx.coroutines.delay(15_000)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF16231E))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PORTARIA", color = Color.White, fontWeight = FontWeight.Bold)
            Text(if (online) "ONLINE" else "OFFLINE", color = if (online) Color(0xFF4ADE80) else Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
            TextButton(onClick = onLogout) { Text("Sair", color = Color.White) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { modo = TipoMovimento.entrada }, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (modo == TipoMovimento.entrada) Color(0xFF238F76) else Color(0xFF374151)),
                shape = RoundedCornerShape(10.dp)) { Text("ENTRADA", fontWeight = FontWeight.Bold) }
            Button(onClick = { modo = TipoMovimento.saida }, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (modo == TipoMovimento.saida) Color(0xFF3B82F6) else Color(0xFF374151)),
                shape = RoundedCornerShape(10.dp)) { Text("SAIDA", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
            AndroidView(factory = { c ->
                PreviewView(c).also { previewView ->
                    val future = ProcessCameraProvider.getInstance(c)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(c), QrAnalyzer { code ->
                            if (processando) return@QrAnalyzer
                            processando = true
                            scope.launch {
                                val token = extrairToken(code)
                                ultima = repo.registarLeitura(token, modo, offline = !repo.temInternet())
                                processando = false
                            }
                        })
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(c))
                }
            }, modifier = Modifier.fillMaxSize())
            if (processando) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        val r = ultima
        val bg = when {
            r == null -> Color(0xFF1F2937)
            r.offline -> Color(0xFF92400E)
            r.resultado in listOf("permitido", "autorizado_manual") -> Color(0xFF166534)
            r.resultado == "duplicado" -> Color(0xFF374151)
            else -> Color(0xFF991B1B)
        }
        Column(Modifier.fillMaxWidth().background(bg).padding(16.dp)) {
            Text(r?.mensagem ?: "Aponte ao QR do passe", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            r?.alunoNome?.let { Text("$it  \u00b7  ${r.alunoTurma ?: ""}", color = Color.White.copy(0.9f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            r?.estadoPropina?.let { Text("Propina: $it", color = Color.White.copy(0.8f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        }
    }
}

private fun extrairToken(raw: String): String {
    val t = raw.trim()
    if (t.matches(Regex("^[a-fA-F0-9]{32}$"))) return t.lowercase()
    Regex("[a-fA-F0-9]{32}").find(t)?.value?.let { return it.lowercase() }
    return t
}

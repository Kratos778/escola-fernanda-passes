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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.fernanda.portaria.data.AppDatabase
import com.fernanda.portaria.data.EventoPendente
import com.fernanda.portaria.data.RespostaLeitura
import com.fernanda.portaria.data.SupabaseRepo
import com.fernanda.portaria.data.TipoMovimento
import com.fernanda.portaria.scanner.QrAnalyzer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

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
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF238F76), secondary = Color(0xFF136650), surface = Color(0xFFF5F7F6))) {
                AppPortaria(repo)
            }
        }
    }
}

enum class Aba { Scanner, Historico, Pendentes, Conta }

@Composable
fun AppPortaria(repo: SupabaseRepo) {
    var logado by remember { mutableStateOf(repo.sessaoAtiva()) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var aba by remember { mutableStateOf(Aba.Scanner) }
    val scope = rememberCoroutineScope()
    val historico = remember { mutableStateListOf<RespostaLeitura>() }

    if (!logado) {
        LoginScreen(email, pass, erro, loading, { email = it }, { pass = it }) {
            loading = true; erro = null
            scope.launch {
                repo.login(email.trim(), pass)
                    .onSuccess { logado = true }
                    .onFailure {
                        erro = "Credenciais invalidas.\nCria o user em Supabase → Authentication → Users.\nUsa a password definida la (nao a do Gmail)."
                    }
                loading = false
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF136650)) {
                val cores = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White, selectedTextColor = Color.White,
                    unselectedIconColor = Color(0xFFA7C4BB), unselectedTextColor = Color(0xFFA7C4BB),
                    indicatorColor = Color(0xFF238F76)
                )
                NavigationBarItem(selected = aba == Aba.Scanner, onClick = { aba = Aba.Scanner },
                    icon = { Icon(Icons.Default.QrCodeScanner, null) }, label = { Text("Scanner", fontSize = 11.sp) }, colors = cores)
                NavigationBarItem(selected = aba == Aba.Historico, onClick = { aba = Aba.Historico },
                    icon = { Icon(Icons.Default.History, null) }, label = { Text("Historico", fontSize = 11.sp) }, colors = cores)
                NavigationBarItem(selected = aba == Aba.Pendentes, onClick = { aba = Aba.Pendentes },
                    icon = { Icon(Icons.Default.CloudOff, null) }, label = { Text("Offline", fontSize = 11.sp) }, colors = cores)
                NavigationBarItem(selected = aba == Aba.Conta, onClick = { aba = Aba.Conta },
                    icon = { Icon(Icons.Default.Person, null) }, label = { Text("Conta", fontSize = 11.sp) }, colors = cores)
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (aba) {
                Aba.Scanner -> ScannerTab(repo) { r -> historico.add(0, r); if (historico.size > 100) historico.removeLast() }
                Aba.Historico -> HistoricoTab(historico)
                Aba.Pendentes -> PendentesTab(repo)
                Aba.Conta -> ContaTab(repo) { scope.launch { repo.logout(); logado = false } }
            }
        }
    }
}

@Composable
fun LoginScreen(email: String, pass: String, erro: String?, loading: Boolean, onEmail: (String) -> Unit, onPass: (String) -> Unit, onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFFE3F1ED)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Portaria Fernanda", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF136650))
            Text("Login do funcionario", fontSize = 14.sp, color = Color(0xFF506058), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            OutlinedTextField(email, onEmail, label = { Text("Email (Supabase Auth)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(pass, onPass, label = { Text("Password do Supabase") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            erro?.let { Text(it, color = Color(0xFFB91C1C), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLogin, enabled = !loading && email.isNotBlank() && pass.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238F76))) {
                Text(if (loading) "A entrar..." else "Entrar", fontSize = 16.sp)
            }
            Card(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Como ver / criar o login", fontWeight = FontWeight.Bold, color = Color(0xFF136650))
                    Spacer(Modifier.height(6.dp))
                    Text("1. Supabase → Authentication → Users\n2. Add user → mete email e uma password NOVA\n3. Essa password NAO e a do Gmail\n4. No SQL Editor:\n\ninsert into public.staff (id, nome, papel)\nselect id, 'Nome', 'admin'\nfrom auth.users where email = 'seu@email';", fontSize = 12.sp, color = Color(0xFF374151), lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
fun ScannerTab(repo: SupabaseRepo, onResultado: (RespostaLeitura) -> Unit) {
    val ultimoTipoPorToken = remember { mutableStateMapOf<String, TipoMovimento>() }
    var ultima by remember { mutableStateOf<RespostaLeitura?>(null) }
    var processando by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(repo.temInternet()) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }
    LaunchedEffect(Unit) {
        while (true) {
            online = repo.temInternet()
            if (online) repo.sincronizarPendentes()
            kotlinx.coroutines.delay(12_000)
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF16231E))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SCANNER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (online) Color(0xFF4ADE80) else Color(0xFFFBBF24), RoundedCornerShape(50)))
                Spacer(Modifier.width(6.dp))
                Text(if (online) "ONLINE" else "OFFLINE", color = if (online) Color(0xFF4ADE80) else Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Text("Icone unico: 1a leitura do cartao = ENTRADA · 2a = SAIDA", color = Color(0xFFA7C4BB), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
            AndroidView(factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        try {
                            val provider = future.get()
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                            val analysis = ImageAnalysis.Builder().setTargetResolution(Size(1280, 720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                            analysis.setAnalyzer(cameraExecutor, QrAnalyzer { code ->
                                if (processando) return@QrAnalyzer
                                processando = true
                                scope.launch {
                                    val token = extrairToken(code)
                                    val anterior = ultimoTipoPorToken[token]
                                    val tipo = when (anterior) {
                                        null, TipoMovimento.saida -> TipoMovimento.entrada
                                        TipoMovimento.entrada -> TipoMovimento.saida
                                    }
                                    ultimoTipoPorToken[token] = tipo
                                    val r = repo.registarLeitura(token, tipo, offline = !repo.temInternet())
                                    ultima = r; onResultado(r); processando = false
                                }
                            })
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner as LifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
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
            r?.tipo?.let { Text(it.uppercase(), color = Color.White.copy(0.85f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
            r?.alunoNome?.let { Text("$it  |  ${r.alunoTurma ?: ""}", color = Color.White.copy(0.9f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            r?.estadoPropina?.let { Text("Propina: $it", color = Color.White.copy(0.8f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun HistoricoTab(items: List<RespostaLeitura>) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7F6)).padding(12.dp)) {
        Text("Historico desta sessao", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF136650))
        Text("Leituras neste telemovel desde o login", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Ainda sem leituras", color = Color.Gray) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { r ->
                val cor = when {
                    r.offline -> Color(0xFF92400E)
                    r.resultado in listOf("permitido", "autorizado_manual") -> Color(0xFF166534)
                    r.resultado == "duplicado" -> Color(0xFF6B7280)
                    else -> Color(0xFF991B1B)
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.mensagem, fontWeight = FontWeight.SemiBold, color = cor)
                        Text("${r.tipo.uppercase()} · ${r.resultado}" + (r.alunoNome?.let { " · $it" } ?: ""), fontSize = 13.sp, color = Color(0xFF374151))
                        if (r.offline) Text("OFFLINE — verificacao pendente", fontSize = 12.sp, color = Color(0xFFB45309))
                    }
                }
            }
        }
    }
}

@Composable
fun PendentesTab(repo: SupabaseRepo) {
    var lista by remember { mutableStateOf<List<EventoPendente>>(emptyList()) }
    var aSinc by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    fun carregar() { scope.launch { lista = AppDatabase.get(ctx).eventoDao().pendentes() } }
    LaunchedEffect(Unit) { carregar() }
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7F6)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Eventos offline", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF136650))
                Text("${lista.size} pendente(s)", fontSize = 12.sp, color = Color.Gray)
            }
            Button(onClick = {
                aSinc = true; msg = null
                scope.launch {
                    val n = repo.sincronizarPendentes()
                    msg = if (n > 0) "$n sincronizado(s)" else "Nada a sincronizar ou sem rede"
                    carregar(); aSinc = false
                }
            }, enabled = !aSinc, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238F76))) {
                Icon(Icons.Default.Sync, null); Spacer(Modifier.width(6.dp)); Text(if (aSinc) "..." else "Sincronizar")
            }
        }
        msg?.let { Text(it, color = Color(0xFF136650), modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(8.dp))
        if (lista.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sem eventos pendentes", color = Color.Gray) }
        else {
            val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lista) { e ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(e.tipo.uppercase(), fontWeight = FontWeight.Bold)
                            Text("Token: ${e.token.take(8)}…", fontSize = 12.sp, color = Color.Gray)
                            Text(fmt.format(Date(e.ocorridoEm)), fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContaTab(repo: SupabaseRepo, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7F6)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Conta do funcionario", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF136650))
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
                Text("Sessao ativa no Supabase Auth", fontSize = 14.sp)
                Text("Rede: ${if (repo.temInternet()) "com internet" else "sem internet"}", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Terminar sessao") }
        Spacer(Modifier.height(24.dp))
        Text("Escola Fernanda · Portaria v1.1", fontSize = 12.sp, color = Color.Gray)
    }
}

private fun extrairToken(raw: String): String {
    val t = raw.trim()
    if (t.matches(Regex("^[a-fA-F0-9]{32}$"))) return t.lowercase()
    Regex("[a-fA-F0-9]{32}").find(t)?.value?.let { return it.lowercase() }
    return t
}

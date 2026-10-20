package com.gi.apkbuilder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val BG = Color(0xFF050B12)
private val PANEL = Color(0xE6111D2A)
private val CYAN = Color(0xFF35F2FF)
private val GOLD = Color(0xFFFFC857)
private val GREEN = Color(0xFF5BFFB2)
private val RED = Color(0xFFFF647C)
private val MUTED = Color(0xFF8FA8BC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { GIApp(this) } }
}

private class Store(context: Context) {
    private val prefs = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "gi_builder", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (_: Exception) { context.getSharedPreferences("gi_builder_fallback", Context.MODE_PRIVATE) }
    fun get(k: String, d: String = "") = prefs.getString(k, d) ?: d
    fun put(k: String, v: String) { prefs.edit().putString(k, v).apply() }
    fun bool(k: String, d: Boolean = false) = prefs.getBoolean(k, d)
    fun putBool(k: String, v: Boolean) { prefs.edit().putBoolean(k, v).apply() }
}
private data class RunInfo(val id: Long, val status: String, val conclusion: String, val updated: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GIApp(context: Context) {
    val store = remember { Store(context) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val client = remember { OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build() }
    var repo by remember { mutableStateOf(store.get("repo")) }
    var branch by remember { mutableStateOf(store.get("branch", "main")) }
    var token by remember { mutableStateOf(store.get("token")) }
    var page by remember { mutableStateOf("Splash") }
    var drawerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(2400); if (page == "Splash") page = "Home" }
    BackHandler(enabled = drawerOpen || page != "Home") {
        if (drawerOpen) drawerOpen = false else page = "Home"
    }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("READY") }
    var message by remember { mutableStateOf("Configure GitHub connection to start a cloud build.") }
    var runId by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }
    var console by remember { mutableStateOf("G.I APK BUILDER // GOLDEN CYBER CORE\nSystem initialized.\nWaiting for GitHub configuration…") }
    var selectedZip by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(listOf<RunInfo>()) }
    var autoRefresh by remember { mutableStateOf(store.bool("autoRefresh", true)) }
    var autoDownload by remember { mutableStateOf(store.bool("autoDownload", false)) }
    var neon by remember { mutableStateOf(store.bool("neon", true)) }
    var compact by remember { mutableStateOf(store.bool("compact", false)) }
    var showToken by remember { mutableStateOf(false) }

    fun log(line: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        console = (console + "\n[$t] $line").takeLast(24000)
    }
    suspend fun api(method: String, url: String, body: String? = null): String = withContext(Dispatchers.IO) {
        if (token.isBlank()) throw IOException("GitHub token missing. Open Settings → GitHub Connection.")
        val b = Request.Builder().url(url).header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").header("Authorization", "Bearer $token")
            .header("User-Agent", "GI-APK-Builder")
        if (method == "POST") b.post((body ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType())) else b.get()
        client.newCall(b.build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("GitHub API HTTP ${res.code}: ${text.take(500)}")
            text
        }
    }
    fun repository(): String {
        val r = repo.trim().trim('/')
        require(r.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Repository must use owner/repository format." }
        return r
    }
    suspend fun refresh() {
        val r = repository()
        val data = JSONObject(api("GET", "https://api.github.com/repos/$r/actions/runs?per_page=10"))
        val runs = data.optJSONArray("workflow_runs") ?: JSONArray()
        history = (0 until runs.length()).map { i ->
            val x = runs.getJSONObject(i)
            RunInfo(x.optLong("id"), x.optString("status"), x.optString("conclusion", "pending"), x.optString("updated_at"))
        }
        val first = history.firstOrNull()
        if (first == null) { status = "NO RUN"; message = "No workflow runs found."; log(message); return }
        runId = first.id
        status = first.status.uppercase()
        message = "Run #$runId • $status • ${first.conclusion}"
        log("Workflow status: #$runId $status ${first.conclusion}")
        if (first.status == "completed" && first.conclusion == "success") log("BUILD SUCCESS. Open Download Manager to access artifacts.")
        if (first.status == "completed" && first.conclusion != "success") log("BUILD FAILED. Open GitHub Actions for step-by-step runner logs.")
    }
    suspend fun startBuild() {
        busy = true; status = "STARTING"; progress = .05f
        try {
            val r = repository()
            store.put("repo", repo.trim()); store.put("branch", branch.ifBlank { "main" }.trim()); store.put("token", token)
            log("Checking repository: $r")
            api("GET", "https://api.github.com/repos/$r")
            val wfJson = JSONObject(api("GET", "https://api.github.com/repos/$r/actions/workflows"))
            val wfs = wfJson.optJSONArray("workflows") ?: JSONArray()
            var wf = "android-build.yml"
            for (i in 0 until wfs.length()) {
                val path = wfs.getJSONObject(i).optString("path")
                if (path.endsWith("android-build.yml") || path.endsWith("android.yml")) { wf = path.substringAfterLast("/"); break }
            }
            log("Dispatching $wf on ${branch.ifBlank { "main" }}")
            api("POST", "https://api.github.com/repos/$r/actions/workflows/$wf/dispatches",
                JSONObject().put("ref", branch.ifBlank { "main" }).toString())
            status = "QUEUED"; message = "Workflow dispatched."; progress = .15f
            log("Workflow dispatch accepted. Polling for run…")
            var found = false
            repeat(10) {
                delay(2500)
                try {
                    refresh()
                    if (runId > 0L) { found = true; status = if (status == "COMPLETED") status else "RUNNING"; progress = .35f; return@repeat }
                } catch (e: Exception) { log("Polling: ${e.message}") }
            }
            if (found) {
                repeat(30) {
                    delay(5000)
                    refresh()
                    progress = (progress + .02f).coerceAtMost(.95f)
                    if (status == "COMPLETED") return@repeat
                }
            }
            progress = 1f
            message = "Workflow status: $status"
        } catch (e: Exception) {
            status = "ERROR"; message = e.message ?: "Unknown error"; log("ERROR: $message")
        } finally { busy = false }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) { selectedZip = uri.lastPathSegment ?: uri.toString(); log("ZIP selected: $selectedZip"); message = "ZIP selected locally; repository upload is not implemented yet." }
    }
    val saveLogPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(console) }
            log("Console exported to text file.")
        } catch (e: Exception) { log("Export error: ${e.message}") }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = CYAN, secondary = GOLD, background = BG, surface = PANEL, onBackground = Color.White, onSurface = Color.White, error = RED)) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        LaunchedEffect(drawerOpen) { if (drawerOpen) drawerState.open() else drawerState.close() }
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(290.dp), drawerContainerColor = Color(0xFF08131D)) {
                    Spacer(Modifier.height(18.dp))
                    Text("G.I // CONTROL DECK", color = CYAN, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(18.dp))
                    listOf("Home", "Project Manager", "Build History", "Download Manager", "Console", "Settings", "Advanced Tools").forEach { item ->
                        NavigationDrawerItem(label = { Text(item) }, selected = page == item,
                            onClick = { page = item; drawerOpen = false; scope.launch { drawerState.close() } }, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = CYAN.copy(alpha = .13f), selectedTextColor = CYAN, unselectedTextColor = Color.LightGray))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("GOLDEN CYBER • v1.0.0", color = MUTED, modifier = Modifier.padding(18.dp), fontSize = 11.sp)
                }
            }
        ) {
            Box(Modifier.fillMaxSize().background(BG)) {
                CyberBackground()
                Scaffold(containerColor = Color.Transparent, topBar = {
                    TopAppBar(
                        title = { Column { Text("G.I APK BUILDER", color = CYAN, fontWeight = FontWeight.Black, fontSize = 16.sp); Text("GOLDEN CYBER 3D • CLOUD BUILD CORE", color = GOLD, fontSize = 9.sp) } },
                        navigationIcon = { IconButton(onClick = { drawerOpen = true }) { Icon(Icons.Default.Menu, "Menu", tint = CYAN) } },
                        actions = { IconButton(onClick = { scope.launch { try { refresh() } catch (e: Exception) { log("Refresh error: ${e.message}") } } }) { Icon(Icons.Default.Refresh, "Refresh", tint = GREEN) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xDD07111B))
                    )
                }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (page) {
                            "Splash" -> { SplashScreen() }
                            "Home" -> {
                                NeonCard(neon) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("BUILD CONTROL CENTER", color = GOLD, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Spacer(Modifier.height(6.dp))
                                            Text("Build Android APK\nfrom GitHub Actions", fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 27.sp)
                                            Text("Powered by I KO KO", color = CYAN, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                                        }
                                        Box(Modifier.size(70.dp).background(CYAN.copy(alpha = .09f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Memory, null, tint = CYAN, modifier = Modifier.size(46.dp)) }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatCard("STATUS", status, if (status == "ERROR") RED else GREEN, Modifier.weight(1f), neon)
                                    StatCard("RUN ID", if (runId > 0) "#$runId" else "—", GOLD, Modifier.weight(1f), neon)
                                }
                                NeonCard(neon) {
                                    Text("BUILD PIPELINE", color = CYAN, fontWeight = FontWeight.Bold)
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp), color = GREEN, trackColor = Color(0xFF243340))
                                    Text(message, color = MUTED, fontSize = 12.sp)
                                    Button(onClick = { scope.launch { startBuild() } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = CYAN, contentColor = BG)) {
                                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = BG, strokeWidth = 2.dp) else Icon(Icons.Default.RocketLaunch, null)
                                        Spacer(Modifier.width(8.dp)); Text(if (busy) "BUILD IN PROGRESS…" else "START CLOUD BUILD", fontWeight = FontWeight.Black)
                                    }
                                    OutlinedButton(onClick = { page = "Console" }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, GOLD)) { Icon(Icons.Default.Terminal, null, tint = GOLD); Spacer(Modifier.width(8.dp)); Text("OPEN BUILD CONSOLE", color = GOLD) }
                                }
                                NeonCard(neon) {
                                    Text("QUICK ACCESS", color = GOLD, fontWeight = FontWeight.Bold)
                                    listOf("Project Manager", "Build History", "Download Manager", "Settings").forEach { p ->
                                        OutlinedButton(onClick = { page = p }, modifier = Modifier.fillMaxWidth()) { Text(p) }
                                    }
                                }
                            }
                            "Project Manager" -> NeonCard(neon) {
                                Text("PROJECT MANAGER", color = CYAN, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Text("Choose a source ZIP from your device.", color = MUTED)
                                Button(onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.UploadFile, null); Text("  SELECT PROJECT ZIP") }
                                if (selectedZip.isNotBlank()) Text("Selected: $selectedZip", color = GOLD)
                                Text("Note: selected ZIP is not uploaded to GitHub automatically in this edition.", color = RED, fontSize = 11.sp)
                            }
                            "Build History" -> NeonCard(neon) {
                                Text("BUILD HISTORY", color = CYAN, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Button(onClick = { scope.launch { try { refresh() } catch (e: Exception) { log(e.message ?: "Refresh failed") } } }) { Icon(Icons.Default.Refresh, null); Text(" REFRESH") }
                                if (history.isEmpty()) Text("No build history loaded.", color = MUTED)
                                history.forEach { r ->
                                    HorizontalDivider(color = CYAN.copy(alpha = .2f))
                                    Text("RUN #${r.id}", color = GOLD, fontWeight = FontWeight.Bold)
                                    Text("${r.status.uppercase()} • ${r.conclusion.uppercase()}", color = if (r.conclusion == "success") GREEN else MUTED)
                                    Text(r.updated, color = MUTED, fontSize = 10.sp)
                                }
                            }
                            "Download Manager" -> NeonCard(neon) {
                                Text("DOWNLOAD MANAGER", color = CYAN, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Text("Current workflow run: ${if (runId > 0) "#$runId" else "not selected"}", color = MUTED)
                                Button(onClick = {
                                    scope.launch {
                                        try {
                                            val r = repository()
                                            val json = JSONObject(api("GET", "https://api.github.com/repos/$r/actions/runs/$runId/artifacts"))
                                            val arr = json.optJSONArray("artifacts") ?: JSONArray()
                                            if (arr.length() == 0) log("No artifacts found for run #$runId.")
                                            else {
                                                val art = arr.getJSONObject(0)
                                                log("Artifact: ${art.optString("name")}")
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(art.optString("archive_download_url"))))
                                                log("Opened artifact URL; GitHub authentication may be required.")
                                            }
                                        } catch (e: Exception) { log("Download error: ${e.message}") }
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Text("  OPEN LATEST ARTIFACT") }
                                Text("This opens GitHub's artifact download URL; direct APK extraction is not included yet.", color = RED, fontSize = 11.sp)
                            }
                            "Console" -> NeonCard(neon) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("BUILD CONSOLE", color = CYAN, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { clipboard.setText(AnnotatedString(console)); log("Console copied to clipboard.") }) { Icon(Icons.Default.ContentCopy, "Copy", tint = GOLD) }
                                    IconButton(onClick = { saveLogPicker.launch("gi-build-console.txt") }) { Icon(Icons.Default.SaveAlt, "Export", tint = GREEN) }
                                    IconButton(onClick = { console = "Console cleared." }) { Icon(Icons.Default.DeleteSweep, "Clear", tint = RED) }
                                }
                                Text("RUN #${if (runId > 0) runId else "—"} • $status", color = MUTED, fontSize = 11.sp)
                                SelectionContainer { Text(console, color = Color(0xFFD5FBEA), fontSize = 11.sp, lineHeight = 17.sp) }
                            }
                            "Settings" -> {
                                NeonCard(neon) {
                                    Text("GITHUB CONNECTION", color = CYAN, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                    OutlinedTextField(repo, { repo = it }, label = { Text("Repository (owner/name)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(token, { token = it }, label = { Text("Personal Access Token") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = if (showToken) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { showToken = !showToken }) { Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } })
                                    Button(onClick = { val savedBranch = branch.ifBlank { "main" }.trim(); branch = savedBranch; store.put("repo", repo.trim()); store.put("branch", savedBranch); store.put("token", token); message = "Settings saved successfully on this device."; status = "READY"; log(message) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Text(" SAVE CONNECTION") }
                                    Text(message, color = if (status == "ERROR" || message.contains("failed", true) || message.contains("error", true)) RED else GREEN, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                                    OutlinedButton(onClick = { scope.launch { try { api("GET", "https://api.github.com/repos/${repository()}"); message = "GitHub connection successful"; log(message) } catch (e: Exception) { message = e.message ?: "Connection failed"; log("Connection test failed: $message") } } }, modifier = Modifier.fillMaxWidth()) { Text("TEST CONNECTION") }
                                }
                                NeonCard(neon) {
                                    Text("BUILD SETTINGS", color = GOLD, fontWeight = FontWeight.Bold)
                                    ToggleRow("Auto refresh build status", autoRefresh) { autoRefresh = it; store.putBool("autoRefresh", it) }
                                    ToggleRow("Auto download on success", autoDownload) { autoDownload = it; store.putBool("autoDownload", it) }
                                    Text("Auto-download is a saved preference only; it is not an automatic downloader yet.", color = MUTED, fontSize = 11.sp)
                                }
                                NeonCard(neon) {
                                    Text("APPEARANCE", color = GOLD, fontWeight = FontWeight.Bold)
                                    ToggleRow("Neon glow effects", neon) { neon = it; store.putBool("neon", it) }
                                    ToggleRow("Compact layout", compact) { compact = it; store.putBool("compact", it) }
                                }
                                NeonCard(neon) {
                                    Text("SECURITY", color = GOLD, fontWeight = FontWeight.Bold)
                                    Text("Token is stored using AndroidX encrypted preferences where supported. Clear app data to remove all stored settings.", color = MUTED, fontSize = 12.sp)
                                    TextButton(onClick = { token = ""; store.put("token", ""); log("Saved token cleared.") }) { Text("CLEAR SAVED TOKEN", color = RED) }
                                }
                            }
                            "Advanced Tools" -> NeonCard(neon) {
                                Text("ADVANCED TOOLS", color = CYAN, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Button(onClick = { scope.launch { try { val r = repository(); val result = api("GET", "https://api.github.com/repos/$r/actions/runs?per_page=1"); log("Diagnostics PASS. GitHub API response bytes=${result.length}."); message = "GitHub API reachable." } catch (e: Exception) { message = e.message ?: "Diagnostic failed"; log("DIAGNOSTIC ERROR: $message") } } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.HealthAndSafety, null); Text("  RUN DIAGNOSTICS") }
                                Text("403: check token scopes. 404: check repository or workflow filename. Build errors: inspect the actual GitHub Actions job logs.", color = MUTED, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("G.I SYSTEMS • GOLDEN CYBER / NEON", color = MUTED, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "gi-splash")
    val rotation by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(Modifier.fillMaxWidth().padding(vertical = 34.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(148.dp).graphicsLayer {
                    rotationY = rotation
                    scaleX = pulse
                    scaleY = pulse
                    cameraDistance = 12f * density
                    shadowElevation = 26.dp.toPx()
                }.background(Brush.linearGradient(listOf(CYAN, Color(0xFF123C55), GOLD)), RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Memory, contentDescription = "G.I logo", tint = BG, modifier = Modifier.size(92.dp))
            }
            Text("G.I APK BUILDER", color = CYAN, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("GOLDEN CYBER CORE", color = GOLD, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(modifier = Modifier.width(190.dp), color = GREEN, trackColor = PANEL)
            Text("POWERED BY I KO KO", color = CYAN, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CyberBackground() {
    val trans = rememberInfiniteTransition(label = "cyber")
    val drift by trans.animateFloat(0f, 38f, infiniteRepeatable(tween(7500, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF071421), Color(0xFF03070C), Color(0xFF0A111A))))
        val step = 30.dp.toPx()
        var x = 0f
        while (x < size.width) { drawLine(CYAN.copy(alpha = .05f), Offset(x + drift * .1f, 0f), Offset(x + drift * .1f, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(GOLD.copy(alpha = .035f), Offset(0f, y + drift * .1f), Offset(size.width, y + drift * .1f), 1f); y += step }
        repeat(28) { i ->
            val px = (i * 83f + drift).mod(size.width.coerceAtLeast(1f))
            val py = (i * 139f + drift * 1.7f).mod(size.height.coerceAtLeast(1f))
            drawCircle(if (i % 3 == 0) GOLD.copy(alpha = .25f) else CYAN.copy(alpha = .18f), if (i % 4 == 0) 2.1f else 1.1f, Offset(px, py))
        }
    }
}
@Composable
private fun NeonCard(glow: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = PANEL,
        border = BorderStroke(1.dp, if (glow) Brush.horizontalGradient(listOf(CYAN.copy(alpha = .7f), GOLD.copy(alpha = .45f), CYAN.copy(alpha = .2f))) else Brush.horizontalGradient(listOf(MUTED, MUTED))) ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}
@Composable
private fun StatCard(label: String, value: String, tint: Color, modifier: Modifier, glow: Boolean) {
    Box(modifier) { NeonCard(glow) { Text(label, color = MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(value, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Black) } }
}
@Composable
private fun ToggleRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
        Switch(checked, change)
    }
}

package com.gi.apkbuilder

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.zip.ZipInputStream

private val Bg = Color(0xFF080B0A)
private val Panel = Color(0xFF121916)
private val Gold = Color(0xFFFFC857)
private val Green = Color(0xFF49FF9A)
private val Muted = Color(0xFF9AA9A0)
private val Red = Color(0xFFFF6464)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GITheme { BuilderScreen(this) } }
    }
}

@Composable
private fun GITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, secondary = Green, background = Bg, surface = Panel,
            onPrimary = Bg, onSecondary = Bg, onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

private fun securePrefs(context: Context) = try {
    val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    EncryptedSharedPreferences.create(
        context, "gi_builder_secure",
        key,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
} catch (_: Exception) {
    context.getSharedPreferences("gi_builder_secure_fallback", Context.MODE_PRIVATE)
}

@Composable
private fun BuilderScreen(context: Context) {
    val prefs = remember { securePrefs(context) }
    var owner by remember { mutableStateOf(prefs.getString("owner", "") ?: "") }
    var repo by remember { mutableStateOf(prefs.getString("repo", "") ?: "") }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var branch by remember { mutableStateOf(prefs.getString("branch", "main") ?: "main") }
    var zipUri by remember { mutableStateOf<Uri?>(null) }
    var zipName by remember { mutableStateOf("No project ZIP selected") }
    var status by remember { mutableStateOf("READY") }
    var details by remember { mutableStateOf("Connect GitHub, import an Android Gradle project ZIP, then build.") }
    var busy by remember { mutableStateOf(false) }
    var runId by remember { mutableStateOf<Long?>(null) }
    var progressStage by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            zipUri = uri
            zipName = uri.lastPathSegment?.substringAfterLast('/') ?: "Project ZIP selected"
            status = "ZIP SELECTED"
            details = "Selected project archive. Make sure it contains settings.gradle(.kts) and an app module."
        }
    }

    fun saveSettings() {
        prefs.edit().putString("owner", owner.trim()).putString("repo", repo.trim())
            .putString("token", token.trim()).putString("branch", branch.trim()).apply()
    }

    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = Gold, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("G.I", color = Bg, fontWeight = FontWeight.Black, fontSize = 19.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("G.I APK BUILDER", color = Gold, fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text("GOLDEN EDITION  •  CLOUD BUILD CORE", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.Bolt, null, tint = Green, modifier = Modifier.size(30.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("ENGINE", "GITHUB ACTIONS", Green, Modifier.weight(1f))
            StatChip("STATUS", status, if (status.contains("FAIL", true)) Red else Gold, Modifier.weight(1f))
        }

        SectionCard("01  /  GITHUB CONNECTION", Icons.Default.Link) {
            OutlinedField("GitHub owner / username", owner, { owner = it }, singleLine = true)
            OutlinedField("Repository name", repo, { repo = it }, singleLine = true)
            OutlinedField("Fine-grained token", token, { token = it }, secret = true, singleLine = true)
            OutlinedField("Default branch", branch, { branch = it }, singleLine = true)
            GoldButton("SAVE CONNECTION SETTINGS", enabled = !busy) { saveSettings(); details = "Settings saved on this device." }
            GreenButton("TEST GITHUB CONNECTION", enabled = !busy) {
                scope.launch {
                    busy = true; status = "CONNECTING"
                    try {
                        saveSettings()
                        val result = withContext(Dispatchers.IO) { GitHubApi(owner, repo, token).testConnection() }
                        status = "CONNECTED"; details = result
                    } catch (e: Exception) {
                        status = "CONNECTION FAILED"; details = e.message ?: "Unknown error"
                    }
                    busy = false
                }
            }
        }

        SectionCard("02  /  PROJECT IMPORT", Icons.Default.FolderZip) {
            Text(zipName, color = Muted, fontSize = 13.sp)
            GoldButton("SELECT PROJECT ZIP", enabled = !busy) { zipPicker.launch(arrayOf("application/zip", "application/octet-stream")) }
            Text("ZIP must contain a Gradle Android project at its root, or inside one top-level folder.", color = Muted, fontSize = 11.sp)
        }

        SectionCard("03  /  BUILD CONSOLE", Icons.Default.Terminal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (busy) Gold else Green, RoundedCornerShape(50)))
                Spacer(Modifier.width(8.dp))
                Text(status, color = if (status.contains("FAIL", true)) Red else Green, fontWeight = FontWeight.Bold)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green, trackColor = Color(0xFF26332C))
            Text(details, color = Muted, fontSize = 12.sp)
            if (runId != null) Text("Workflow Run ID: $runId", color = Gold, fontSize = 12.sp)
            GoldButton("BUILD APK ON GITHUB", enabled = !busy) {
                scope.launch {
                    busy = true; runId = null; progressStage = 0
                    try {
                        saveSettings()
                        val uri = zipUri ?: throw IllegalStateException("Select a project ZIP first.")
                        if (owner.isBlank() || repo.isBlank() || token.isBlank()) throw IllegalStateException("Enter GitHub owner, repository and token first.")
                        status = "UPLOADING SOURCE"; details = "Uploading ZIP to repository..."
                        val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read selected ZIP") }
                        if (bytes.size > 45 * 1024 * 1024) throw IllegalStateException("ZIP is larger than 45 MB. Reduce its size and try again.")
                        withContext(Dispatchers.IO) { GitHubApi(owner, repo, token).uploadZip(bytes, branch) }
                        status = "STARTING WORKFLOW"; details = "Source uploaded. Starting android-build workflow..."
                        withContext(Dispatchers.IO) { GitHubApi(owner, repo, token).dispatch(branch) }
                        status = "WAITING FOR RUN"; details = "Workflow dispatched. Waiting for GitHub Actions..."
                        val api = GitHubApi(owner, repo, token)
                        var found: Long? = null
                        for (attempt in 0 until 30) {
                            delay(5000)
                            found = withContext(Dispatchers.IO) { api.findLatestRun() }
                            if (found != null) break
                        }
                        val id = found ?: throw IllegalStateException("Workflow run not found after 150 seconds. Check Actions tab and workflow_dispatch.")
                        runId = id
                        var completed = false
                        var conclusion = ""
                        status = "BUILD IN PROGRESS"
                        for (attempt in 0 until 180) {
                            val run = withContext(Dispatchers.IO) { api.getRun(id) }
                            val state = run.optString("status")
                            conclusion = run.optString("conclusion")
                            details = "GitHub Actions: $state" + if (conclusion.isNotBlank() && conclusion != "null") " • $conclusion" else ""
                            if (state == "completed") { completed = true; break }
                            delay(5000)
                        }
                        if (!completed) throw IllegalStateException("Build is taking too long. Run ID $id can be checked later in GitHub Actions.")
                        if (conclusion != "success") throw IllegalStateException("Build failed ($conclusion). Open Build Logs / GitHub Actions for details.")
                        status = "DOWNLOADING ARTIFACT"; details = "Build successful. Downloading APK artifact..."
                        val apk = withContext(Dispatchers.IO) { api.downloadApkArtifact(id) }
                        val name = "GI-Build-${id}.apk"
                        val out = withContext(Dispatchers.IO) {
                            val f = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), name)
                            f.parentFile?.mkdirs(); f.writeBytes(apk); f
                        }
                        status = "BUILD SUCCESSFUL"; details = "APK saved: ${out.absolutePath}"
                    } catch (e: Exception) {
                        status = "BUILD FAILED"; details = e.message ?: "Unexpected error"
                    }
                    busy = false
                }
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        if (runId == null) { details = "No run ID yet. Start a build first."; return@launch }
                        busy = true
                        try {
                            val api = GitHubApi(owner, repo, token)
                            details = withContext(Dispatchers.IO) { api.getRun(runId!!).toString(2) }
                        } catch (e: Exception) { details = e.message ?: "Could not fetch run" }
                        busy = false
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()
            ) { Text("REFRESH RUN DETAILS", color = Green) }
        }

        SectionCard("SECURITY NOTE", Icons.Default.Shield) {
            Text("Use a narrowly scoped fine-grained token. This app stores it locally, but a rooted or compromised device can expose secrets. Never share your token or a configured APK. Do not run ZIP projects from unknown sources.", color = Muted, fontSize = 12.sp)
        }
        Text("G.I SYSTEMS  •  CYBER GOLD / NEON GREEN", color = Color(0xFF53635A), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26372D))) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value.take(20), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26372D))) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Green, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            content()
        }
    }
}

@Composable
private fun OutlinedField(label: String, value: String, onValueChange: (String) -> Unit, secret: Boolean = false, singleLine: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = singleLine,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Green, unfocusedBorderColor = Color(0xFF3A4B40),
            focusedLabelColor = Gold, unfocusedLabelColor = Muted, cursorColor = Green
        )
    )
}

@Composable
private fun GoldButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg), shape = RoundedCornerShape(12.dp)) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

@Composable
private fun GreenButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg), shape = RoundedCornerShape(12.dp)) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

private class GitHubApi(private val owner: String, private val repo: String, private val token: String) {
    private val base = "https://api.github.com/repos/${owner.trim()}/${repo.trim()}"
    private fun request(path: String, method: String = "GET", body: JSONObject? = null, accept: String = "application/vnd.github+json"): Pair<Int, ByteArray> {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.setRequestProperty("User-Agent", "GI-APK-Builder-Android")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        conn.disconnect()
        if (code !in 200..299) throw IllegalStateException("GitHub API HTTP $code: ${bytes.toString(Charsets.UTF_8).take(800)}")
        return code to bytes
    }

    fun testConnection(): String {
        val (_, bytes) = request("")
        val obj = JSONObject(bytes.toString(Charsets.UTF_8))
        return "Connected to ${obj.optString("full_name")} • default branch ${obj.optString("default_branch")}"
    }

    fun uploadZip(bytes: ByteArray, branch: String) {
        val body = JSONObject()
            .put("message", "G.I APK Builder: upload project source")
            .put("branch", branch.ifBlank { "main" })
            .put("content", Base64.getEncoder().encodeToString(bytes))
        // GitHub Contents API has a 100 MB hard limit; practical client cap is lower.
        request("/contents/incoming/project.zip", "PUT", body)
    }

    fun dispatch(branch: String) {
        val body = JSONObject().put("ref", branch.ifBlank { "main" })
        request("/actions/workflows/android-build.yml/dispatches", "POST", body)
    }

    fun findLatestRun(): Long? {
        val (_, bytes) = request("/actions/workflows/android-build.yml/runs?per_page=5")
        val runs = JSONObject(bytes.toString(Charsets.UTF_8)).optJSONArray("workflow_runs") ?: return null
        for (i in 0 until runs.length()) {
            val obj = runs.getJSONObject(i)
            if (obj.optString("event") == "workflow_dispatch") return obj.optLong("id").takeIf { it > 0 }
        }
        return null
    }

    fun getRun(id: Long): JSONObject {
        val (_, bytes) = request("/actions/runs/$id")
        return JSONObject(bytes.toString(Charsets.UTF_8))
    }

    fun downloadApkArtifact(runId: Long): ByteArray {
        val (_, bytes) = request("/actions/runs/$runId/artifacts")
        val artifacts = JSONObject(bytes.toString(Charsets.UTF_8)).optJSONArray("artifacts")
            ?: throw IllegalStateException("No artifacts returned")
        var artifactId = -1L
        for (i in 0 until artifacts.length()) {
            val a = artifacts.getJSONObject(i)
            if (a.optString("name") == "gi-apk") { artifactId = a.optLong("id"); break }
        }
        if (artifactId < 0) throw IllegalStateException("Artifact 'gi-apk' not found. Check workflow output.")
        val conn = URL("$base/actions/artifacts/$artifactId/zip").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 20000; conn.readTimeout = 120000
        conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.use { it.readBytes() }?.toString(Charsets.UTF_8) ?: ""
            throw IllegalStateException("Artifact download HTTP $code: ${err.take(500)}")
        }
        val zip = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        val zis = ZipInputStream(zip.inputStream())
        zis.use { z ->
            while (true) {
                val entry = z.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".apk", true)) {
                    val out = ByteArrayOutputStream()
                    z.copyTo(out)
                    return out.toByteArray()
                }
            }
        }
        throw IllegalStateException("Artifact ZIP contains no APK")
    }
}

package com.gi.notetest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private data class Note(val title: String, val body: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("gi_notes", MODE_PRIVATE)
        val initialNotes = runCatching {
            val array = JSONArray(prefs.getString("notes", "[]"))
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                Note(item.optString("title"), item.optString("body"))
            }
        }.getOrDefault(emptyList())

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF7652D6),
                secondary = Color(0xFF00A896),
                background = Color(0xFFF6F4FB),
                surface = Color.White
            )) {
                NotesScreen(initialNotes) { notes ->
                    val array = JSONArray()
                    notes.forEach { note ->
                        array.put(JSONObject().put("title", note.title).put("body", note.body))
                    }
                    prefs.edit().putString("notes", array.toString()).apply()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesScreen(initialNotes: List<Note>, onNotesChanged: (List<Note>) -> Unit) {
    val notes = remember { mutableStateListOf<Note>().apply { addAll(initialNotes) } }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text("G.I Notes", fontWeight = FontWeight.Bold)
                Text("Simple • Private • Offline", style = MaterialTheme.typography.labelSmall)
            }
        })
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7FF))) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Create a note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = title, onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = body, onValueChange = { body = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Write your note…") }, minLines = 3)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        if (title.isNotBlank() || body.isNotBlank()) {
                            notes.add(0, Note(title.ifBlank { "Untitled note" }, body))
                            onNotesChanged(notes.toList())
                            title = ""
                            body = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.NoteAdd, contentDescription = null)
                        Text("  Save Note")
                    }
                }
            }

            Text("Your notes (${notes.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (notes.isEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📝", style = MaterialTheme.typography.headlineLarge)
                        Text("No notes yet")
                        Text("Create your first note above.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(notes) { index, note ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(note.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    if (note.body.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(note.body, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                IconButton(onClick = {
                                    notes.removeAt(index)
                                    onNotesChanged(notes.toList())
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete note", tint = Color(0xFFB3261E))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

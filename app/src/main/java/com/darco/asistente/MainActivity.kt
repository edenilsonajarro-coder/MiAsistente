package com.darco.asistente

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

private val Night = Color(0xFF08111F)
private val Cyan = Color(0xFF51E6F5)
private val Muted = Color(0xFF9FB4C9)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { AssistantScreen(onSpeak = { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "reply") }) }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("es", "SV")
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
private data class ChatLine(val who: String, val text: String, val user: Boolean)

@Composable
private fun AssistantScreen(onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("SISTEMA LISTO · MODO LOCAL") }
    val history = remember { mutableStateListOf(ChatLine("ASISTENTE", "Hola. Soy tu asistente personal. Esta es mi primera versión.", false)) }
    fun answer(raw: String) {
        val input = raw.trim()
        if (input.isEmpty()) return
        history.add(ChatLine("TÚ", input, true))
        val n = input.lowercase(Locale("es", "SV"))
        val reply = when {
            "hola" in n -> "Hola. Aquí estoy. ¿Qué hacemos hoy?"
            "cómo te llamas" in n || "tu nombre" in n -> "Por ahora me llamo Mi Asistente. Más adelante podrás personalizar mi nombre."
            "ayuda" in n || "qué puedes hacer" in n -> "Puedo conversar con respuestas básicas sin conexión y hablar usando la voz instalada en Android."
            "gracias" in n -> "De nada. Estoy aquí para ayudarte."
            "adiós" in n || "adios" in n -> "Hasta luego. Estaré aquí cuando vuelvas."
            else -> "He recibido tu mensaje. Esta versión aún no está conectada a una IA avanzada, pero funciona con respuestas básicas locales."
        }
        history.add(ChatLine("ASISTENTE", reply, false))
        onSpeak(reply)
        draft = ""
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) answer(spoken) else status = "NO SE DETECTÓ VOZ"
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSpeech(context, speechLauncher::launch) else status = "MICRÓFONO SIN PERMISO"
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF102439), Night, Color(0xFF050911)))).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).background(Color(0xFF153D50), CircleShape), contentAlignment = Alignment.Center) { Text("✦", color = Cyan, fontSize = 28.sp) }
            Column {
                Text("MI ASISTENTE", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("PERSONAL · ESPAÑOL", color = Muted, fontSize = 11.sp)
            }
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF0C1E30), RoundedCornerShape(20.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(90.dp).background(Brush.radialGradient(colors = listOf(Color(0xFF1E6A7B), Color(0xFF10263D))), CircleShape), contentAlignment = Alignment.Center) { Text("AI", color = Cyan, fontSize = 32.sp) }
            Spacer(Modifier.height(8.dp))
            Text("A TU SERVICIO", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(status, color = Cyan, fontSize = 10.sp)
        }
        Text("CONVERSACIÓN", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history) { line ->
                Column(Modifier.fillMaxWidth().background(if (line.user) Color(0xFF123448) else Color(0xFF0D1B2B), RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text(line.who, color = if (line.user) Cyan else Color(0xFF7FA7C2), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(line.text, color = Color(0xFFE7F0F8), fontSize = 14.sp)
                }
            }
        }
        OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Escribe aquí…", color = Muted) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF31516A), cursorColor = Cyan), shape = RoundedCornerShape(14.dp), maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSpeech(context, speechLauncher::launch) else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17384E))) { Text("HABLAR", color = Cyan) }
            Button(onClick = { answer(draft) }, enabled = draft.isNotBlank(), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Cyan)) { Text("ENVIAR", color = Night, fontWeight = FontWeight.Bold) }
        }
        Text("Esta versión no envía tus mensajes a un servidor de IA.", color = Muted, fontSize = 10.sp)
    }
}
private fun startSpeech(context: android.content.Context, launch: (Intent) -> Unit) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return
    launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-SV")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con tu asistente")
    })
}

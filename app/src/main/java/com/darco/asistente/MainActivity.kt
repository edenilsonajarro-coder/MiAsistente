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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Night = Color(0xFF07101D)
private val Panel = Color(0xFF101F31)
private val PanelRaised = Color(0xFF162B40)
private val Cyan = Color(0xFF58E5F4)
private val Blue = Color(0xFF6D91FF)
private val Muted = Color(0xFF9DB1C5)
private val TextMain = Color(0xFFEAF3FC)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { AssistantScreen(onSpeak = { text -> tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant-reply") }) }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("es", "SV")
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

private data class ChatLine(val who: String, val text: String, val user: Boolean, val time: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantScreen(onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mi_asistente", 0) }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("LISTO PARA AYUDARTE") }
    var voiceEnabled by remember { mutableStateOf(prefs.getBoolean("voice_enabled", true)) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("es", "SV")) }

    fun stamp() = timeFormat.format(Date())
    fun loadHistory(): List<ChatLine> {
        return try {
            val array = JSONArray(prefs.getString("history", "[]") ?: "[]")
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                ChatLine(item.optString("who"), item.optString("text"), item.optBoolean("user"), item.optString("time"))
            }
        } catch (_: Exception) { emptyList() }
    }
    val history = remember {
        mutableStateListOf<ChatLine>().apply {
            addAll(loadHistory())
            if (isEmpty()) add(ChatLine("MI ASISTENTE", "¡Hola! Soy tu asistente personal. Puedo ayudarte con ideas, organización y preguntas sencillas. El modo actual funciona sin enviar tus mensajes a un servidor.", false, stamp()))
        }
    }

    fun saveHistory() {
        val array = JSONArray()
        history.takeLast(100).forEach { line ->
            array.put(JSONObject().put("who", line.who).put("text", line.text).put("user", line.user).put("time", line.time))
        }
        prefs.edit().putString("history", array.toString()).apply()
    }
    fun replyFor(raw: String): String {
        val n = raw.lowercase(Locale("es", "SV")).trim()
        return when {
            n in listOf("hola", "holi", "buenas", "buenos días", "buenas tardes", "buenas noches") || n.startsWith("hola ") ->
                "¡Hola! Estoy aquí para ayudarte. ¿Qué te gustaría hacer?"
            "tu nombre" in n || "cómo te llamas" in n || "quién eres" in n ->
                "Soy Mi Asistente, tu asistente personal en español. Esta versión funciona localmente y puedes usarla sin crear una cuenta."
            "qué puedes hacer" in n || "ayuda" in n || "funciones" in n ->
                "Puedo responder algunas preguntas frecuentes, ayudarte a organizar tareas, darte ideas y hablar con la voz de Android. Esta versión aún no está conectada a una IA avanzada en internet."
            "hora" in n || "qué día" in n || "fecha" in n ->
                "Ahora son las ${SimpleDateFormat("HH:mm", Locale("es", "SV")).format(Date())} del ${SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "SV")).format(Date())}."
            "recordar" in n || "recuérdame" in n || "pendiente" in n || "tarea" in n ->
                "Puedo mantener esta conversación guardada en este teléfono. Para una tarea importante, escribe el pendiente con una fecha y consérvalo en tus notas o calendario; todavía no puedo enviar recordatorios automáticos."
            "idea" in n || "creativo" in n || "inspiración" in n ->
                "Probemos esto: define el objetivo en una frase, genera tres ideas distintas y elige la más sencilla para empezar. Si me dices el tema, te ayudo a desarrollarlo."
            "estudiar" in n || "aprender" in n ->
                "Podemos estudiar paso a paso: dime el tema, tu nivel y qué parte te cuesta. Te lo explicaré con ejemplos y después podemos practicar."
            "gracias" in n -> "De nada. Cuando necesites algo, escribe aquí y lo intentamos."
            "adiós" in n || "adios" in n || "hasta luego" in n -> "Hasta luego. Tu conversación queda guardada en este teléfono."
            else -> "Entiendo lo que escribes. En esta versión todavía no tengo un modelo de inteligencia artificial conectado, así que mis respuestas son limitadas. Cuando añadamos una conexión segura a una IA, podré conversar sobre muchos más temas."
        }
    }
    fun send(raw: String) {
        val input = raw.trim()
        if (input.isBlank()) return
        history.add(ChatLine("TÚ", input, true, stamp()))
        val answer = replyFor(input)
        history.add(ChatLine("MI ASISTENTE", answer, false, stamp()))
        draft = ""
        status = "RESPUESTA LISTA"
        saveHistory()
        if (voiceEnabled) onSpeak(answer)
        scope.launch { listState.animateScrollToItem((history.size - 1).coerceAtLeast(0)) }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) send(spoken) else status = "NO SE DETECTÓ VOZ"
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSpeech(context, speechLauncher::launch) else status = "MICRÓFONO SIN PERMISO"
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF122B43), Night, Color(0xFF050A12)))).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Cyan.copy(alpha = .25f), Blue.copy(alpha = .3f)))), contentAlignment = Alignment.Center) {
                Text("✦", color = Cyan, fontSize = 28.sp)
            }
            Column(Modifier.weight(1f)) {
                Text("MI ASISTENTE", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                Text("TU ESPACIO PERSONAL", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
            IconButton(onClick = {
                history.clear()
                history.add(ChatLine("MI ASISTENTE", "Conversación nueva. ¿En qué puedo ayudarte?", false, stamp()))
                saveHistory()
                status = "NUEVA CONVERSACIÓN"
            }) { Icon(Icons.Default.Add, contentDescription = "Nueva conversación", tint = Cyan) }
            IconButton(onClick = {
                history.clear()
                history.add(ChatLine("MI ASISTENTE", "He borrado el historial de conversación de esta aplicación.", false, stamp()))
                saveHistory()
                status = "HISTORIAL LIMPIADO"
            }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Limpiar historial", tint = Muted) }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF123A50), Color(0xFF102137), Color(0xFF14213A)))).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(Brush.radialGradient(colors = listOf(Color(0xFF236D83), Color(0xFF10263D)))), contentAlignment = Alignment.Center) {
                Text("AI", color = Cyan, fontSize = 28.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("A TU SERVICIO", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).background(Cyan, CircleShape))
                    Text(status, color = Cyan, fontSize = 9.sp, letterSpacing = .5.sp)
                }
                Text("Modo local · conversación privada", color = Muted, fontSize = 11.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CONVERSACIÓN", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            Text("${history.size} mensajes", color = Muted, fontSize = 10.sp)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(history) { line ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(if (line.user) Brush.linearGradient(listOf(Color(0xFF16435A), Color(0xFF123247))) else Brush.linearGradient(listOf(Panel, Color(0xFF0D1A2A))))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(line.who, color = if (line.user) Cyan else Color(0xFF8DAFC8), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.weight(1f))
                        Text(line.time, color = Muted.copy(alpha = .75f), fontSize = 9.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(line.text, color = TextMain, fontSize = 14.sp, lineHeight = 21.sp)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("PRUEBA UNA IDEA", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SuggestionChip("Dame una idea") { send("Dame una idea creativa") }
                SuggestionChip("Ayuda") { send("¿Qué puedes hacer?") }
                SuggestionChip("Fecha y hora") { send("¿Qué hora es y qué día es?") }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Escribe tu mensaje…", color = Muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF35516B),
                    cursorColor = Cyan, focusedContainerColor = Color(0xFF08111D), unfocusedContainerColor = Color(0xFF08111D)
                ),
                shape = RoundedCornerShape(18.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(draft) }),
                trailingIcon = {
                    IconButton(onClick = { send(draft) }, enabled = draft.isNotBlank()) {
                        Icon(Icons.Default.Send, contentDescription = "Enviar mensaje", tint = if (draft.isNotBlank()) Cyan else Muted)
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSpeech(context, speechLauncher::launch)
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
                ) { Icon(Icons.Default.Mic, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("HABLAR") }
                Button(
                    onClick = { voiceEnabled = !voiceEnabled; prefs.edit().putBoolean("voice_enabled", voiceEnabled).apply(); status = if (voiceEnabled) "VOZ ACTIVADA" else "VOZ DESACTIVADA" },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (voiceEnabled) Color(0xFF15384D) else Color(0xFF263244), contentColor = if (voiceEnabled) Cyan else Muted)
                ) {
                    Icon(if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff, contentDescription = null)
                    Spacer(Modifier.width(7.dp)); Text(if (voiceEnabled) "VOZ ON" else "VOZ OFF")
                }
            }
            Text("El historial se guarda en este teléfono. No hay IA online conectada todavía.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = PanelRaised, contentColor = Cyan) {
        Text(label, modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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

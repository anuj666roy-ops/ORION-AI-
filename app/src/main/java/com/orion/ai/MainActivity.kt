package com.orion.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : ComponentActivity() {

    private val geminiApiKey = "AQ.Ab8RN6L-9jcNeui2ZG-LS7VB8NxtJP67Unb2rR8ha3xTVmYAPA"
    private var isOnlineState = mutableStateOf(false)
    private var aiResponseText = mutableStateOf("Orion AI Standby. Clap or speak to activate.")
    
    private val client = OkHttpClient()
    private var audioRecord: AudioRecord? = null
    private var isListeningForClaps = true

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startClapDetection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
                )
            )
        } else {
            startClapDetection()
        }

        setContent {
            OrionTheme(isOnline = isOnlineState.value, responseText = aiResponseText.value) {
                // Voice / Command Trigger Handler
                triggerAction(it)
            }
        }
    }

    private fun startClapDetection() {
        CoroutineScope(Dispatchers.IO).launch {
            val sampleRate = 8000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                audioRecord?.startRecording()
                val buffer = ShortArray(bufferSize)

                while (isListeningForClaps) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        var maxAmplitude = 0
                        for (i in 0 until read) {
                            if (buffer[i] > maxAmplitude) {
                                maxAmplitude = buffer[i].toInt()
                            }
                        }
                        // Clap threshold detection
                        if (maxAmplitude > 25000) {
                            withContext(Dispatchers.Main) {
                                isOnlineState.value = true
                                aiResponseText.value = "Clap Detected! Orion AI is Online."
                            }
                            delay(3000) // Stay online for 3 seconds
                        }
                    }
                    delay(100)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun triggerAction(command: String) {
        val lower = command.lowercase()
        when {
            lower.contains("camera") || lower.contains("photo") -> {
                aiResponseText.value = "Opening Camera..."
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    aiResponseText.value = "Camera error: ${e.message}"
                }
            }
            lower.contains("song") || lower.contains("music") || lower.contains("play") -> {
                aiResponseText.value = "Playing your favorite song..."
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://open.spotify.com"))
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    aiResponseText.value = "Opening media player..."
                }
            }
            else -> {
                queryGemini(command)
            }
        }
    }

    private fun queryGemini(prompt: String) {
        aiResponseText.value = "Processing with Gemini..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiApiKey"
                val jsonBody = JSONObject().apply {
                    put("contents", org.json.JSONArray().put(
                        JSONObject().put("parts", org.json.JSONArray().put(
                            JSONObject().put("text", prompt)
                        ))
                    ))
                }
                val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(url).post(body).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        val json = JSONObject(responseData ?: "")
                        val text = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        withContext(Dispatchers.Main) {
                            aiResponseText.value = text
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            aiResponseText.value = "Gemini Error: ${response.code}"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    aiResponseText.value = "Error: ${e.localizedMessage}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isListeningForClaps = false
        audioRecord?.stop()
        audioRecord?.release()
    }
}

@Composable
fun OrionTheme(isOnline: Boolean, responseText: String, onCommandSubmit: (String) -> Unit) {
    var textInput by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "holo")
    val glowColor = if (isOnline) Color(0xFF00E5FF) else Color(0xFFFF1744)

    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF050505) // Deep Futuristic Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A0A0A), Color(0xFF1A0005))
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Futuristic Holographic Orb Header
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .shadow(16.dp * animatedAlpha, CircleShape)
                        .border(3.dp, glowColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isOnline) "ONLINE" else "STANDBY",
                            color = glowColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ORION",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // AI Response Console Box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101015)),
                    border = BorderStroke(1.dp, Color(0xFFFF1744).copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = responseText,
                            color = Color(0xFF00E5FF),
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Command Input Field
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Command Orion (e.g. Play music, Open camera)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFFFF1744),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Execute Button
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onCommandSubmit(textInput)
                            textInput = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("EXECUTE COMMAND", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

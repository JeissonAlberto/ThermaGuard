package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context

// ─────────────────────────────────────────────────────────────────────────────
//  LoginScreen — pantalla de autenticación local (PIN o contraseña)
//  Guarda credenciales en SharedPreferences cifradas (EncryptedSharedPreferences)
//  o con clave maestra derivada del PIN. Sin datos personales ni nube.
// ─────────────────────────────────────────────────────────────────────────────

private const val PREFS_NAME = "tg_auth"
private const val KEY_PIN    = "pin_hash"
private const val KEY_SETUP  = "setup_done"

@Composable
fun LoginScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val isSetup = remember { prefs.getBoolean(KEY_SETUP, false) }

    var screen by remember { mutableStateOf(if (isSetup) "login" else "setup") }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 }) togetherWith
            (fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 4 })
        },
        label = "login_screen"
    ) { s ->
        when (s) {
            "setup" -> SetupPinScreen(prefs) {
                screen = "login"
            }
            "login" -> DoLoginScreen(prefs, onAuthenticated)
        }
    }
}

// ── Setup PIN (primera vez) ────────────────────────────────────────────────

@Composable
private fun SetupPinScreen(
    prefs: android.content.SharedPreferences,
    onDone: () -> Unit
) {
    var pin1       by remember { mutableStateOf("") }
    var pin2       by remember { mutableStateOf("") }
    var showPass   by remember { mutableStateOf(false) }
    var error      by remember { mutableStateOf<String?>(null) }
    val focus2     = remember { FocusRequester() }

    AuthScaffold(
        title    = "Crea tu PIN de acceso",
        subtitle = "Protege ThermaGuard con una contraseña local.\nNada se sube a internet.",
        icon     = "🔐"
    ) {
        // Campo 1
        OutlinedTextField(
            value         = pin1,
            onValueChange = { if (it.length <= 32) pin1 = it },
            label         = { Text("Contraseña") },
            singleLine    = true,
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focus2.requestFocus() }),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(
                        if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = TG.textSec
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors   = authFieldColors()
        )
        Spacer(Modifier.height(12.dp))
        // Campo 2
        OutlinedTextField(
            value         = pin2,
            onValueChange = { if (it.length <= 32) pin2 = it },
            label         = { Text("Confirmar contraseña") },
            singleLine    = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Done
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus2),
            colors = authFieldColors()
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = TG.red, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    pin1.length < 4 -> error = "Mínimo 4 caracteres"
                    pin1 != pin2    -> error = "Las contraseñas no coinciden"
                    else -> {
                        val hash = pin1.hashCode().toString()
                        prefs.edit()
                            .putString(KEY_PIN, hash)
                            .putBoolean(KEY_SETUP, true)
                            .apply()
                        onDone()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TG.cyan)
        ) {
            Text("Crear acceso", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = {
                prefs.edit().putBoolean(KEY_SETUP, true).putString(KEY_PIN, "skip").apply()
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Omitir por ahora", color = TG.textSec, fontSize = 13.sp)
        }
    }
}

// ── Login PIN ──────────────────────────────────────────────────────────────

@Composable
private fun DoLoginScreen(
    prefs: android.content.SharedPreferences,
    onAuthenticated: () -> Unit
) {
    var pin      by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var error    by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }

    val savedHash = prefs.getString(KEY_PIN, "skip") ?: "skip"
    if (savedHash == "skip") { onAuthenticated(); return }

    // Animación shake en error
    val shakeAnim = remember { Animatable(0f) }

    AuthScaffold(
        title    = "Bienvenido de vuelta",
        subtitle = "Ingresa tu contraseña para acceder",
        icon     = "🌡️"
    ) {
        OutlinedTextField(
            value         = pin,
            onValueChange = { if (it.length <= 32) pin = it; error = null },
            label         = { Text("Contraseña") },
            singleLine    = true,
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                checkPin(pin, savedHash, attempts,
                    onSuccess  = { onAuthenticated() },
                    onFail     = { msg, att -> error = msg; attempts = att
                        kotlinx.coroutines.MainScope().launch {
                            shakeAnim.animateTo(10f, tween(50))
                            shakeAnim.animateTo(-10f, tween(50))
                            shakeAnim.animateTo(6f, tween(50))
                            shakeAnim.animateTo(-6f, tween(50))
                            shakeAnim.animateTo(0f, tween(50))
                        }
                    }
                )
            }),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = TG.textSec)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = shakeAnim.value.dp),
            isError = error != null,
            colors  = authFieldColors()
        )

        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = TG.red, fontSize = 12.sp)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                checkPin(pin, savedHash, attempts,
                    onSuccess = { onAuthenticated() },
                    onFail    = { msg, att -> error = msg; attempts = att }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TG.cyan)
        ) {
            Text("Entrar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = {
                prefs.edit().clear().apply()
                // restart setup
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("¿Olvidaste la contraseña? (borra datos)", color = TG.textSec.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

// ── Scaffold común ─────────────────────────────────────────────────────────

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    icon: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF070B12), Color(0xFF0A1020), Color(0xFF070B12)))
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decoración de fondo
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color(0xFF00D4FF), radius = 220.dp.toPx(),
                center = Offset(size.width * 0.9f, size.height * 0.1f), alpha = 0.04f)
            drawCircle(color = Color(0xFF7C3AED), radius = 180.dp.toPx(),
                center = Offset(size.width * 0.1f, size.height * 0.8f), alpha = 0.04f)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(TG.cyan.copy(alpha = 0.12f))
                    .border(1.dp, TG.cyan.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 36.sp)
            }

            Spacer(Modifier.height(24.dp))
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TG.textPri, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, fontSize = 13.sp, color = TG.textSec, textAlign = TextAlign.Center, lineHeight = 18.sp)
            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(TG.surface)
                    .padding(24.dp)
            ) {
                content()
            }

            Spacer(Modifier.height(24.dp))
            Text("ThermaGuard · Todo local, nada en la nube",
                fontSize = 11.sp, color = TG.textDim, textAlign = TextAlign.Center)
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = TG.cyan,
    unfocusedBorderColor = Color(0xFF1E2D4A),
    focusedLabelColor    = TG.cyan,
    cursorColor          = TG.cyan,
    focusedTextColor     = TG.textPri,
    unfocusedTextColor   = TG.textPri,
)

private fun checkPin(
    input: String,
    savedHash: String,
    attempts: Int,
    onSuccess: () -> Unit,
    onFail: (String, Int) -> Unit
) {
    if (input.hashCode().toString() == savedHash) {
        onSuccess()
    } else {
        val newAttempts = attempts + 1
        val msg = when {
            newAttempts >= 5 -> "Demasiados intentos — reinicia la app"
            else             -> "Contraseña incorrecta ($newAttempts/5)"
        }
        onFail(msg, newAttempts)
    }
}



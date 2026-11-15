package com.langhost.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.langhost.data.AppLockSettings
import com.langhost.ui.theme.*
import kotlinx.coroutines.flow.first

@Composable
fun LockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppLockSettings(context) }
    var enabled by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    LaunchedEffect(Unit) {
        enabled = settings.biometricEnabled.first()
        checked = true
        if (!enabled) unlocked = true
    }

    if (!checked) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    if (!enabled || unlocked) {
        content()
        return
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text("LanGhost", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Разблокируйте приложение", color = TextSecondary)
            authError?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    val activity = context as? FragmentActivity
                    if (activity == null) {
                        authError = "Не удалось открыть системную проверку. Приложение остаётся заблокированным."
                        return@Button
                    }
                    val availability = BiometricManager.from(context).canAuthenticate(authenticators)
                    if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
                        authError = "Биометрия или PIN устройства сейчас недоступны. Проверьте настройки блокировки устройства."
                        return@Button
                    }
                    try {
                        val prompt = BiometricPrompt(
                            activity,
                            ContextCompat.getMainExecutor(context),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    authError = null
                                    unlocked = true
                                }

                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    authError = "Проверка не выполнена. Попробуйте ещё раз."
                                }
                            }
                        )
                        val info = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("LanGhost")
                            .setSubtitle("Биометрия или PIN устройства")
                            .setAllowedAuthenticators(authenticators)
                            .build()
                        prompt.authenticate(info)
                    } catch (_: Exception) {
                        authError = "Не удалось запустить системную проверку. Приложение остаётся заблокированным."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Разблокировать")
            }
        }
    }
}

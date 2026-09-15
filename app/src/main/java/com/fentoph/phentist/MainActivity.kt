package com.fentoph.phentist

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> SecurityState.cameraGranted = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        cameraPermission.launch(Manifest.permission.CAMERA)
        setContent { PhentistApp() }
    }
}

object SecurityState { var cameraGranted by mutableStateOf(false) }
data class LibraryItem(val id: String, val title: String, val price: String, val purchased: Boolean)

@Composable
fun PhentistApp() {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    val googleSignIn = remember { GoogleSignIn(context) }
    val scope = rememberCoroutineScope()
    val session by authRepository.session.collectAsState(initial = AuthSession(false, null, null, null, null))
    var paymentOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Phentist") }) }) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Text("Ta'lim materiallari", style = MaterialTheme.typography.headlineMedium) }
                item { Text("Sotib olingan materiallar faqat hisobingiz orqali o'qiladi.") }

                if (!session.signedIn) {
                    item {
                        Button(enabled = !busy, onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                googleSignIn.signIn(context as ComponentActivity).fold(
                                    onSuccess = { identity ->
                                        PhentistApi.signInWithGoogle(identity.idToken).fold(
                                            onSuccess = { result ->
                                                authRepository.saveVerifiedGoogleIdentity(result.userId, result.email, result.displayName, result.accessToken)
                                                message = "Google orqali tizimga kirdingiz."
                                            },
                                            onFailure = { message = it.message ?: "Kirish amalga oshmadi." }
                                        )
                                    },
                                    onFailure = { message = it.message ?: "Google orqali kirish amalga oshmadi." }
                                )
                                busy = false
                            }
                        }) { Text(if (busy) "Kutilmoqda..." else "Google orqali kirish") }
                    }
                } else {
                    item { Text("Hisob: ${session.email}") }
                }

                item { LibraryCard(LibraryItem("demo", "Protected document", "25 000 UZS", false), onPurchase = { paymentOpen = true }) }

                if (paymentOpen) {
                    item {
                        PaymentForm(
                            enabled = session.signedIn && !busy,
                            onSubmit = { name, surname, location, phone ->
                                val token = session.accessToken ?: return@PaymentForm
                                busy = true
                                message = null
                                scope.launch {
                                    PhentistApi.submitPayment(token, name, surname, location, phone).fold(
                                        onSuccess = { result ->
                                            message = "To'lov ma'lumoti qabul qilindi. ID: ${result.id}"
                                            paymentOpen = false
                                        },
                                        onFailure = { message = it.message ?: "To'lovni yuborib bo'lmadi." }
                                    )
                                    busy = false
                                }
                            }
                        )
                    }
                }

                message?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.primary) } }
                item {
                    Text(
                        if (SecurityState.cameraGranted) "Ekran himoyasi yoqilgan." else "Kamera ruxsati talab qilinadi.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryCard(item: LibraryItem, onPurchase: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleLarge)
            Text(item.price)
            Button(onClick = onPurchase) { Text("Sotib olish") }
        }
    }
}

@Composable
fun PaymentForm(
    enabled: Boolean,
    onSubmit: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("To'lov ma'lumotlari", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Ism") }, singleLine = true)
            OutlinedTextField(surname, { surname = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Familiya") }, singleLine = true)
            OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Manzil") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Telefon raqami") }, singleLine = true)
            Text("Xizmat e-maili: aslbekqoziboyev536@gmail.com", style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = enabled && name.isNotBlank() && surname.isNotBlank() && location.isNotBlank() && phone.isNotBlank(),
                onClick = { onSubmit(name.trim(), surname.trim(), location.trim(), phone.trim()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("To'lovni davom ettirish") }
        }
    }
}

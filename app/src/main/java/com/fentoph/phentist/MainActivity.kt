package com.fentoph.phentist

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val PhentistNavy = Color(0xFF102A43)
private val PhentistBlue = Color(0xFF2563EB)
private val PhentistBlueDark = Color(0xFF1D4ED8)
private val PhentistSky = Color(0xFFEAF2FF)
private val PhentistSurface = Color(0xFFF7F9FC)
private val PhentistText = Color(0xFF172033)
private val PhentistMuted = Color(0xFF667085)
private val PhentistSuccess = Color(0xFF147D64)

class MainActivity : ComponentActivity() {
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        SecurityState.cameraGranted = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
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

    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = PhentistBlue,
            onPrimary = Color.White,
            background = PhentistSurface,
            surface = Color.White,
            onSurface = PhentistText,
            onBackground = PhentistText,
            outline = Color(0xFFD0D5DD)
        )
    ) {
        Scaffold(
            containerColor = PhentistSurface,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(listOf(PhentistBlue, PhentistBlueDark))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("P", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.size(10.dp))
                            Text("Phentist", fontWeight = FontWeight.Bold, color = PhentistNavy)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                item {
                    HeroHeader(
                        signedIn = session.signedIn,
                        email = session.email,
                        onSignIn = {
                            busy = true
                            message = null
                            scope.launch {
                                googleSignIn.signIn(context as ComponentActivity).fold(
                                    onSuccess = { identity ->
                                        PhentistApi.signInWithGoogle(identity.idToken).fold(
                                            onSuccess = { result ->
                                                authRepository.saveVerifiedGoogleIdentity(
                                                    result.userId,
                                                    result.email,
                                                    result.displayName,
                                                    result.accessToken
                                                )
                                                message = "Google orqali tizimga kirdingiz."
                                            },
                                            onFailure = { message = it.message ?: "Kirish amalga oshmadi." }
                                        )
                                    },
                                    onFailure = { message = it.message ?: "Google orqali kirish amalga oshmadi." }
                                )
                                busy = false
                            }
                        },
                        busy = busy
                    )
                }

                item {
                    SectionTitle("O'quv materiallari", "Tanlangan materiallarni xavfsiz o'qing")
                }

                item {
                    LibraryCard(
                        LibraryItem("demo", "Protected document", "25 000 UZS", false),
                        onPurchase = { paymentOpen = true }
                    )
                }

                if (paymentOpen) {
                    item {
                        PaymentForm(enabled = session.signedIn && !busy) { name, surname, location, phone ->
                            val token = session.accessToken
                            if (token.isNullOrBlank()) {
                                message = "Iltimos, qayta Google orqali kiring."
                            } else {
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
                        }
                    }
                }

                message?.let { text ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = PhentistSky,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text,
                                modifier = Modifier.padding(14.dp),
                                color = PhentistNavy,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                item { SecurityBanner() }
                item { Spacer(Modifier.height(10.dp)) }
            }
        }
    }
}

@Composable
private fun HeroHeader(
    signedIn: Boolean,
    email: String?,
    onSignIn: () -> Unit,
    busy: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(PhentistNavy, Color(0xFF183E68))))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Bilimingizni bir joyda saqlang", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Premium ta'lim materiallariga kiring va ularni himoyalangan o'quv muhitida o'qing.",
            color = Color(0xFFDCE8F7),
            style = MaterialTheme.typography.bodyMedium
        )
        if (signedIn) {
            Surface(color = Color(0x332563EB), shape = RoundedCornerShape(12.dp)) {
                Text("Hisob: ${email ?: "Google account"}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Button(
                onClick = onSignIn,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = PhentistBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (busy) "Kutilmoqda..." else "Google orqali kirish", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PhentistText)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PhentistMuted)
    }
}

@Composable
fun LibraryCard(item: LibraryItem, onPurchase: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Surface(color = PhentistSky, shape = RoundedCornerShape(10.dp)) {
                    Text("TA'LIM", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = PhentistBlueDark, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Text("PDF", color = PhentistMuted, style = MaterialTheme.typography.labelMedium)
            }
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PhentistText)
            Text("Himoyalangan o'quv materiali", style = MaterialTheme.typography.bodyMedium, color = PhentistMuted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(item.price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PhentistNavy)
                Button(onClick = onPurchase, shape = RoundedCornerShape(12.dp)) { Text("Sotib olish", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun PaymentForm(enabled: Boolean, onSubmit: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("To'lovni rasmiylashtirish", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Ma'lumotlaringizni kiriting. Xizmat e-maili avtomatik biriktiriladi.", style = MaterialTheme.typography.bodySmall, color = PhentistMuted)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Ism") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(surname, { surname = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Familiya") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Manzil") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Telefon raqami") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            Surface(color = Color(0xFFF2F4F7), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Xizmat e-maili: aslbekqoziboyev536@gmail.com", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = PhentistMuted)
            }
            Button(
                enabled = enabled && name.isNotBlank() && surname.isNotBlank() && location.isNotBlank() && phone.isNotBlank(),
                onClick = { onSubmit(name.trim(), surname.trim(), location.trim(), phone.trim()) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("To'lovni davom ettirish", fontWeight = FontWeight.SemiBold) }
            if (!enabled) {
                Text("To'lovni davom ettirish uchun Google orqali kiring.", color = PhentistMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SecurityBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE8F6F1)),
                contentAlignment = Alignment.Center
            ) { Text("✓", color = PhentistSuccess, fontWeight = FontWeight.Bold) }
            Column {
                Text("Himoyalangan o'qish", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (SecurityState.cameraGranted) "Ekran himoyasi yoqilgan." else "Kamera ruxsati talab qilinadi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PhentistMuted
                )
            }
        }
    }
}

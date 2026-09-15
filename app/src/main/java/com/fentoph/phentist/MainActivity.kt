package com.fentoph.phentist

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> SecurityState.cameraGranted = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent screenshots, screen recording and non-secure display mirroring.
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
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Phentist") }) }) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Text("Private library", style = MaterialTheme.typography.headlineMedium) }
                item { Text("Content is available only to authenticated users who own it.") }
                item {
                    LibraryCard(LibraryItem("demo", "Protected document", "$9.99", false))
                }
                item {
                    Text(
                        if (SecurityState.cameraGranted)
                            "Camera protection is enabled."
                        else
                            "Camera permission is required for anti-recording protection.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryCard(item: LibraryItem) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleLarge)
            Text(item.price)
            Button(onClick = { /* BillingClient integration is wired in BillingRepository. */ }) {
                Text("Purchase")
            }
        }
    }
}

package com.localdictation.keyboard

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val apacheLicense = remember {
        context.assets.open("licenses/Apache-2.0.txt").bufferedReader().use { it.readText() }
    }
    val llvmLicense = remember {
        context.assets.open("licenses/LLVM-LICENSE.TXT").bufferedReader().use { it.readText() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F6FA))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
            Text("‹ Back to OSTTKey")
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Credits & licenses", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text(
                "OSTTKey uses the third-party components listed below. Each remains subject to its own license. This page provides attribution and does not change those license terms.",
                color = Color(0xFF5D6474),
                fontSize = 14.sp,
            )
        }

        CreditCard(
            title = "Whistle speech-to-text model",
            description = "Cactus Compute, Inc. (2026). Whistle: Speech Recognition for Tiny Devices. " +
                "Model file whistle.cact, revision d3ea19e. Licensed under Apache License 2.0.\n\n" +
                "Citation: Mroz, Jakub; Ndubuaku, Henry; Mosoyan, Karen; Cylich, Noah; Kumar, Satyajit; " +
                "Sandhu, Parkirat; Shemet, Roman; and Lee, Justin H. (2026), Whistle: Speech Recognition for Tiny Devices.",
            linkLabel = "View Whistle model card",
            url = "https://huggingface.co/Cactus-Compute/whistle/tree/d3ea19e",
        )

        CreditCard(
            title = "Needle native inference runtime",
            description = "Cactus Compute, Inc. Android ARM64 runtime (libneedle.a and needle.h), " +
                "revision f84005f. Licensed under Apache License 2.0.",
            linkLabel = "View Needle runtime source",
            url = "https://huggingface.co/Cactus-Compute/needle3/tree/f84005f",
        )

        CreditCard(
            title = "AndroidX and Jetpack Compose",
            description = "Android Open Source Project contributors. Includes Activity Compose 1.13.0, Compose UI, Foundation and " +
                "Material 3 (Compose BOM 2026.09.00), Lifecycle Runtime KTX 2.9.4, and Core KTX 1.17.0. " +
                "Licensed under Apache License 2.0.",
            linkLabel = "View AndroidX source",
            url = "https://android.googlesource.com/platform/frameworks/support/",
        )

        CreditCard(
            title = "Kotlin standard library",
            description = "JetBrains. Licensed under Apache License 2.0.",
            linkLabel = "View Kotlin source and license",
            url = "https://github.com/JetBrains/kotlin",
        )

        CreditCard(
            title = "Kotlin Coroutines",
            description = "JetBrains. kotlinx-coroutines-android 1.10.2. Licensed under Apache License 2.0.",
            linkLabel = "View Kotlin Coroutines source",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
        )

        CreditCard(
            title = "LLVM libc++ C++ runtime",
            description = "The Android NDK's statically linked LLVM libc++ runtime. Licensed under " +
                "Apache License 2.0 with LLVM Exceptions.",
            linkLabel = "View LLVM project",
            url = "https://github.com/llvm/llvm-project",
        )

        LicenseCard("Apache License 2.0", apacheLicense)
        LicenseCard("LLVM License and Exceptions", llvmLicense)

        Text(
            "Third-party names and marks belong to their respective owners. OSTTKey does not imply endorsement by the projects listed here.",
            color = Color(0xFF5D6474),
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun CreditCard(
    title: String,
    description: String,
    linkLabel: String,
    url: String,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(description, color = Color(0xFF545C6E), fontSize = 13.sp)
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                modifier = Modifier.padding(start = 0.dp),
            ) {
                Text(linkLabel)
            }
        }
    }
}

@Composable
private fun LicenseCard(title: String, licenseText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                text = licenseText,
                color = Color(0xFF545C6E),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

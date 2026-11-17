package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile

@Composable
fun ProfileDialog(
    userEmail: String,
    fallbackName: String,
    profile: UserProfile?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (displayName: String, shopName: String, address: String, phone: String) -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember(profile) {
        mutableStateOf(profile?.displayName?.ifBlank { fallbackName } ?: fallbackName)
    }
    var shopName by remember(profile) { mutableStateOf(profile?.shopName ?: "") }
    var address by remember(profile) {
        mutableStateOf(
            profile?.deliveryAddress?.ifBlank { "Jl. Kemang Raya No. 12, Jakarta Selatan" }
                ?: "Jl. Kemang Raya No. 12, Jakarta Selatan"
        )
    }
    var phone by remember(profile) { mutableStateOf(profile?.phone ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil & Alamat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = userEmail.ifBlank { "Akun Google Terhubung" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 80) displayName = it },
                    label = { Text("Nama Tampilan") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { if (it.length <= 80) shopName = it },
                    label = { Text("Nama Toko (Opsional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { if (it.length <= 250) address = it },
                    label = { Text("Alamat Pengantaran Default") },
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 30) phone = it },
                    label = { Text("Nomor HP / WhatsApp") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Keluar Akun",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(displayName, shopName, address, phone)
                    onDismiss()
                },
                enabled = !isBusy && displayName.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

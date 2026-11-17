package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.DrinkOptions
import com.example.data.model.UserProfile
import com.example.ui.components.DrinkArtwork
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.formatRupiah
import com.example.ui.state.CartTotals
import com.example.ui.state.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartCheckoutScreen(
    cartState: UiState<List<CartItem>>,
    totals: CartTotals,
    userProfile: UserProfile?,
    promoCode: String,
    selectedPaymentMethod: String,
    isBusy: Boolean,
    onPromoCodeChange: (String) -> Unit,
    onPaymentMethodSelect: (String) -> Unit,
    onUpdateQuantity: (cartItemId: String, newQuantity: Long) -> Unit,
    onRemoveItem: (cartItemId: String) -> Unit,
    onPlaceOrder: (deliveryAddress: String) -> Unit,
    onBrowseDrinks: () -> Unit
) {
    var addressInput by remember {
        mutableStateOf(
            userProfile?.deliveryAddress?.ifBlank { "Jl. Kemang Raya No. 12, Jakarta Selatan" }
                ?: "Jl. Kemang Raya No. 12, Jakarta Selatan"
        )
    }

    LaunchedEffect(userProfile?.deliveryAddress) {
        val remoteAddr = userProfile?.deliveryAddress.orEmpty()
        if (remoteAddr.isNotBlank() && addressInput == "Jl. Kemang Raya No. 12, Jakarta Selatan") {
            addressInput = remoteAddr
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Text(
            text = "Keranjang & Checkout",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        when (cartState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                EmptyStatePanel(
                    icon = Icons.Rounded.ShoppingBag,
                    title = "Gagal memuat keranjang",
                    subtitle = cartState.message
                )
            }
            is UiState.Success -> {
                val items = cartState.data
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyStatePanel(
                            icon = Icons.Rounded.ShoppingBag,
                            title = stringResource(id = R.string.empty_cart_title),
                            subtitle = stringResource(id = R.string.empty_cart_subtitle),
                            action = {
                                OutlinedButton(onClick = onBrowseDrinks) {
                                    Text("Jelajahi Minuman")
                                }
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            CartItemCard(
                                item = item,
                                onUpdateQuantity = { q -> onUpdateQuantity(item.id, q) },
                                onRemove = { onRemoveItem(item.id) }
                            )
                        }

                        // Delivery Address Card
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Alamat Pengantaran",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = addressInput,
                                        onValueChange = { if (it.length <= 250) addressInput = it },
                                        placeholder = { Text("Masukkan alamat lengkap pengantaran") },
                                        shape = RoundedCornerShape(14.dp),
                                        maxLines = 2,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("checkout_address_input")
                                    )
                                }
                            }
                        }

                        // Promo Code Card
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.LocalOffer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Kode Promo",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = promoCode,
                                        onValueChange = { if (it.length <= 25) onPromoCodeChange(it) },
                                        placeholder = { Text("Ketik SEGAR5K atau HEMAT10K") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("promo_code_input")
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SuggestionChip(
                                            onClick = { onPromoCodeChange("SEGAR5K") },
                                            label = { Text("Pakai SEGAR5K") }
                                        )
                                        SuggestionChip(
                                            onClick = { onPromoCodeChange("HEMAT10K") },
                                            label = { Text("Pakai HEMAT10K") }
                                        )
                                    }
                                    if (totals.promoHint.isNotBlank()) {
                                        Text(
                                            text = totals.promoHint,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (totals.discount > 0L) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.secondary
                                            },
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Payment Method Card
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Payment,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Metode Pembayaran",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        DrinkOptions.paymentMethods.forEach { method ->
                                            FilterChip(
                                                selected = selectedPaymentMethod == method,
                                                onClick = { onPaymentMethodSelect(method) },
                                                label = { Text(method) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Summary Breakdown Card
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Ringkasan Pembayaran",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    PriceRow("Subtotal (${totals.totalItems} cup)", formatRupiah(totals.subtotal))
                                    PriceRow(
                                        "Ongkos Kirim",
                                        if (totals.deliveryFee == 0L) "Gratis" else formatRupiah(totals.deliveryFee)
                                    )
                                    if (totals.discount > 0L) {
                                        PriceRow(
                                            "Diskon Promo (${totals.appliedPromo})",
                                            "-${formatRupiah(totals.discount)}",
                                            highlight = true
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Total Bayar",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = formatRupiah(totals.totalAmount),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sticky Checkout Footer
                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Tagihan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatRupiah(totals.totalAmount),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = { onPlaceOrder(addressInput) },
                                enabled = !isBusy && addressInput.trim().length >= 5,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("checkout_submit_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = stringResource(id = R.string.checkout_now),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartItemCard(
    item: CartItem,
    onUpdateQuantity: (Long) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrinkArtwork(
                imageKey = item.imageKey,
                contentDescription = item.drinkName,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.shopName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.drinkName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.sugarLevel} • ${item.iceLevel}" +
                        if (item.topping != "Tanpa Topping") " • +${item.topping}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formatRupiah(item.lineTotal),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Hapus ${item.drinkName}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onUpdateQuantity(item.quantity - 1L) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Kurangi",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = item.quantity.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { onUpdateQuantity(item.quantity + 1L) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Tambah",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

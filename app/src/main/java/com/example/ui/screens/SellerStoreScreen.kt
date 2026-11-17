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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DrinkCategory
import com.example.data.model.DrinkListing
import com.example.data.model.DrinkOptions
import com.example.data.model.UserProfile
import com.example.ui.components.DrinkArtwork
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.StatusPill
import com.example.ui.components.formatRupiah
import com.example.ui.state.UiState

@Composable
fun SellerStoreScreen(
    userProfile: UserProfile?,
    myDrinksState: UiState<List<DrinkListing>>,
    isBusy: Boolean,
    onSaveDrink: (
        existingId: String?,
        shopName: String,
        name: String,
        description: String,
        category: String,
        imageKey: String,
        price: Long,
        originalPrice: Long,
        sizeLabel: String,
        calories: Long,
        stock: Long,
        onSuccess: () -> Unit
    ) -> Unit,
    onToggleStatus: (drinkId: String, active: Boolean) -> Unit,
    onDeleteDrink: (drinkId: String) -> Unit
) {
    var editingDrink by remember { mutableStateOf<DrinkListing?>(null) }
    var showFormSheet by remember { mutableStateOf(false) }
    var deletingDrink by remember { mutableStateOf<DrinkListing?>(null) }

    val defaultShopName = userProfile?.shopName?.ifBlank { "Kedai ${userProfile.displayName}" }
        ?: "Kedai Minuman Saya"

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingDrink = null
                    showFormSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_seller_drink_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Tambah minuman untuk dijual")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = "Toko Minuman Saya",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            when (myDrinksState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    EmptyStatePanel(
                        icon = Icons.Rounded.Storefront,
                        title = "Gagal memuat toko Anda",
                        subtitle = myDrinksState.message
                    )
                }
                is UiState.Success -> {
                    val drinks = myDrinksState.data
                    val activeCount = drinks.count { it.status == "ACTIVE" }

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = defaultShopName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Setiap menu aktif otomatis tampil di beranda marketplace SegarKu.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "$activeCount/${drinks.size}",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Menu Aktif",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        if (drinks.isEmpty()) {
                            item {
                                EmptyStatePanel(
                                    icon = Icons.Rounded.Storefront,
                                    title = stringResource(id = R.string.empty_store_title),
                                    subtitle = stringResource(id = R.string.empty_store_subtitle),
                                    action = {
                                        Button(
                                            onClick = {
                                                editingDrink = null
                                                showFormSheet = true
                                            }
                                        ) {
                                            Text("Jual Minuman Pertama")
                                        }
                                    }
                                )
                            }
                        } else {
                            items(drinks, key = { it.id }) { drink ->
                                SellerDrinkCard(
                                    drink = drink,
                                    onEdit = {
                                        editingDrink = drink
                                        showFormSheet = true
                                    },
                                    onToggleActive = { active -> onToggleStatus(drink.id, active) },
                                    onDelete = { deletingDrink = drink }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }

    if (showFormSheet) {
        SellerDrinkFormSheet(
            initialDrink = editingDrink,
            defaultShopName = defaultShopName,
            isBusy = isBusy,
            onDismiss = { showFormSheet = false },
            onSubmit = { shop, name, desc, cat, imgKey, price, origPrice, size, cal, stock ->
                onSaveDrink(
                    editingDrink?.id,
                    shop,
                    name,
                    desc,
                    cat,
                    imgKey,
                    price,
                    origPrice,
                    size,
                    cal,
                    stock
                ) {
                    showFormSheet = false
                }
            }
        )
    }

    if (deletingDrink != null) {
        val target = deletingDrink!!
        AlertDialog(
            onDismissRequest = { deletingDrink = null },
            title = { Text("Hapus Menu?") },
            text = { Text("Menu \"${target.name}\" akan dihapus permanen dari toko dan marketplace.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDrink(target.id)
                        deletingDrink = null
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingDrink = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun SellerDrinkCard(
    drink: DrinkListing,
    onEdit: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val isActive = drink.status == "ACTIVE"
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
                imageKey = drink.imageKey,
                contentDescription = drink.name,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPill(
                        text = if (isActive) "Aktif" else "Dijeda",
                        containerColor = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = drink.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = drink.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatRupiah(drink.price)} • Stok ${drink.stock} • ${drink.sizeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = isActive,
                    onCheckedChange = onToggleActive
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit ${drink.name}", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Hapus ${drink.name}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SellerDrinkFormSheet(
    initialDrink: DrinkListing?,
    defaultShopName: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        shopName: String,
        name: String,
        description: String,
        category: String,
        imageKey: String,
        price: Long,
        originalPrice: Long,
        sizeLabel: String,
        calories: Long,
        stock: Long
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var shopName by remember { mutableStateOf(initialDrink?.shopName ?: defaultShopName) }
    var name by remember { mutableStateOf(initialDrink?.name ?: "") }
    var description by remember { mutableStateOf(initialDrink?.description ?: "") }
    var category by remember { mutableStateOf(initialDrink?.category ?: DrinkCategory.KOPI.label) }
    var imageKey by remember { mutableStateOf(initialDrink?.imageKey ?: "coffee") }
    var priceText by remember { mutableStateOf(initialDrink?.price?.toString() ?: "25000") }
    var originalPriceText by remember {
        mutableStateOf(
            if ((initialDrink?.originalPrice ?: 0L) > 0L) initialDrink!!.originalPrice.toString() else ""
        )
    }
    var sizeLabel by remember { mutableStateOf(initialDrink?.sizeLabel ?: "Reguler 350ml") }
    var caloriesText by remember { mutableStateOf(initialDrink?.calories?.toString() ?: "180") }
    var stockText by remember { mutableStateOf(initialDrink?.stock?.toString() ?: "30") }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initialDrink == null) "Tambah Minuman Baru" else "Edit Menu Minuman",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = shopName,
                onValueChange = { if (it.length <= 80) shopName = it },
                label = { Text("Nama Kedai / Toko") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text("Nama Minuman") },
                placeholder = { Text("Contoh: Es Matcha Oat Latte") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("seller_drink_name_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 400) description = it },
                label = { Text("Deskripsi Rasa & Bahan") },
                placeholder = { Text("Jelaskan keunikan minuman racikanmu...") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("seller_drink_desc_input")
            )

            Text("Kategori Minuman", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrinkCategory.sellableCategories.forEach { cat ->
                    FilterChip(
                        selected = category == cat.label,
                        onClick = { category = cat.label },
                        label = { Text(cat.label) }
                    )
                }
            }

            Text("Pilih Visual Minuman", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrinkOptions.imagePresets.forEach { preset ->
                    FilterChip(
                        selected = imageKey == preset.key,
                        onClick = { imageKey = preset.key },
                        label = { Text(preset.label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit).take(7) },
                    label = { Text("Harga (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = originalPriceText,
                    onValueChange = { originalPriceText = it.filter(Char::isDigit).take(7) },
                    label = { Text("Harga Coret (Opsional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = sizeLabel,
                    onValueChange = { if (it.length <= 40) sizeLabel = it },
                    label = { Text("Ukuran") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = { caloriesText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Kalori") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(0.9f)
                )
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Stok") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(0.9f)
                )
            }

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val price = priceText.toLongOrNull() ?: 0L
                    val orig = originalPriceText.toLongOrNull() ?: 0L
                    val cal = caloriesText.toLongOrNull() ?: 150L
                    val stock = stockText.toLongOrNull() ?: 20L
                    when {
                        shopName.trim().length < 2 -> validationError = "Nama toko minimal 2 karakter."
                        name.trim().length < 2 -> validationError = "Nama minuman minimal 2 karakter."
                        description.trim().length < 5 -> validationError = "Deskripsi minimal 5 karakter."
                        price !in 1000L..1_000_000L -> validationError = "Harga minimal Rp1.000."
                        else -> {
                            validationError = null
                            onSubmit(
                                shopName,
                                name,
                                description,
                                category,
                                imageKey,
                                price,
                                orig,
                                sizeLabel,
                                cal.coerceIn(0L, 2000L),
                                stock.coerceIn(0L, 10000L)
                            )
                        }
                    }
                },
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_seller_drink_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (initialDrink == null) "Publikasikan ke Marketplace" else "Simpan Perubahan")
                }
            }
        }
    }
}

package com.example.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.MarketplaceRepository
import com.example.data.model.CartItem
import com.example.data.model.CuratedCatalog
import com.example.data.model.DrinkCategory
import com.example.data.model.DrinkListing
import com.example.data.model.DrinkOptions
import com.example.data.model.DrinkOrder
import com.example.data.model.OrderLineItem
import com.example.data.model.OrderStatus
import com.example.data.model.ToppingOption
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class CartTotals(
    val subtotal: Long = 0L,
    val deliveryFee: Long = 0L,
    val discount: Long = 0L,
    val totalAmount: Long = 0L,
    val totalItems: Long = 0L,
    val appliedPromo: String = "",
    val promoHint: String = ""
)

class MarketplaceViewModel(
    private val repository: MarketplaceRepository,
    private val currentUserId: String,
    private val currentUserName: String,
    private val currentUserEmail: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(DrinkCategory.SEMUA)
    val selectedCategory: StateFlow<DrinkCategory> = _selectedCategory.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _promoCodeInput = MutableStateFlow("")
    val promoCodeInput: StateFlow<String> = _promoCodeInput.asStateFlow()

    private val _selectedPaymentMethod = MutableStateFlow(DrinkOptions.paymentMethods.first())
    val selectedPaymentMethod: StateFlow<String> = _selectedPaymentMethod.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserName, currentUserEmail)
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(errorLabel: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, errorLabel, rootError)
                emit(UiState.Error(rootError.localizedMessage ?: errorLabel))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Gagal memuat profil pengguna", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    private val activeCommunityDrinks: StateFlow<UiState<List<DrinkListing>>> =
        repository.observeActiveDrinks(currentUserId)
            .toUiStateFlow("Gagal memuat katalog minuman")

    val favoriteIds: StateFlow<Set<String>> = repository.observeFavorites(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(emptySet())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val catalogDrinks: StateFlow<UiState<List<DrinkListing>>> = combine(
        activeCommunityDrinks,
        _searchQuery,
        _selectedCategory,
        _favoritesOnly,
        favoriteIds
    ) { remoteState, query, category, favOnly, favs ->
        when (remoteState) {
            is UiState.Error -> {
                // Still show curated catalog if remote fails transiently, or surface error if desired
                val combined = filterDrinks(CuratedCatalog.partnerDrinks, query, category, favOnly, favs)
                UiState.Success(combined)
            }
            is UiState.Loading -> {
                val combined = filterDrinks(CuratedCatalog.partnerDrinks, query, category, favOnly, favs)
                UiState.Success(combined)
            }
            is UiState.Success -> {
                val merged = (remoteState.data.sortedByDescending { it.createdAt ?: Timestamp(0, 0) } +
                    CuratedCatalog.partnerDrinks).distinctBy { it.id }
                UiState.Success(filterDrinks(merged, query, category, favOnly, favs))
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Success(CuratedCatalog.partnerDrinks)
    )

    val cartItems: StateFlow<UiState<List<CartItem>>> = repository.observeCart(currentUserId)
        .map { items -> items.sortedByDescending { it.createdAt ?: Timestamp(0, 0) } }
        .toUiStateFlow("Gagal memuat keranjang")

    val cartCount: StateFlow<Int> = cartItems
        .map { state ->
            when (state) {
                is UiState.Success -> state.data.sumOf { it.quantity.toInt() }
                else -> 0
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0
        )

    val cartTotals: StateFlow<CartTotals> = combine(cartItems, _promoCodeInput) { state, promoRaw ->
        val items = (state as? UiState.Success)?.data ?: emptyList()
        if (items.isEmpty()) {
            CartTotals()
        } else {
            val subtotal = items.sumOf { it.lineTotal }
            val totalItems = items.sumOf { it.quantity }
            val deliveryFee = if (subtotal >= FREE_DELIVERY_THRESHOLD) 0L else STANDARD_DELIVERY_FEE
            val normalizedPromo = promoRaw.trim().uppercase()
            val (discount, appliedPromo, hint) = when (normalizedPromo) {
                "SEGAR5K" -> Triple(5000L.coerceAtMost(subtotal), "SEGAR5K", "Diskon Rp5.000 aktif!")
                "HEMAT10K" -> {
                    if (subtotal >= 45_000L) {
                        Triple(10_000L, "HEMAT10K", "Diskon Rp10.000 aktif!")
                    } else {
                        Triple(0L, "", "Minimal belanja Rp45.000 untuk kode HEMAT10K")
                    }
                }
                "" -> Triple(0L, "", "")
                else -> Triple(0L, "", "Kode promo tidak ditemukan (coba SEGAR5K atau HEMAT10K)")
            }
            val totalAmount = (subtotal + deliveryFee - discount).coerceAtLeast(0L)
            CartTotals(
                subtotal = subtotal,
                deliveryFee = deliveryFee,
                discount = discount,
                totalAmount = totalAmount,
                totalItems = totalItems,
                appliedPromo = appliedPromo,
                promoHint = hint
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CartTotals()
    )

    val orders: StateFlow<UiState<List<DrinkOrder>>> = repository.observeOrders(currentUserId)
        .map { list -> list.sortedByDescending { it.createdAt ?: Timestamp(0, 0) } }
        .toUiStateFlow("Gagal memuat pesanan")

    val mySellerDrinks: StateFlow<UiState<List<DrinkListing>>> = repository.observeMySellerDrinks(currentUserId)
        .map { list -> list.sortedByDescending { it.createdAt ?: Timestamp(0, 0) } }
        .toUiStateFlow("Gagal memuat menu toko Anda")

    fun observeOrderItems(orderId: String): Flow<List<OrderLineItem>> =
        repository.observeOrderItems(currentUserId, orderId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptyList())
            }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: DrinkCategory) {
        _selectedCategory.value = category
    }

    fun toggleFavoritesFilter() {
        _favoritesOnly.value = !_favoritesOnly.value
    }

    fun onPromoCodeChanged(code: String) {
        _promoCodeInput.value = code
    }

    fun onPaymentMethodSelected(method: String) {
        _selectedPaymentMethod.value = method
    }

    fun toggleFavorite(drinkId: String) {
        viewModelScope.launch {
            val isFav = favoriteIds.value.contains(drinkId)
            repository.toggleFavorite(drinkId, isFav)
                .onFailure { _messages.emit("Gagal memperbarui favorit: ${it.localizedMessage ?: "Kesalahan"}") }
        }
    }

    fun addToCart(
        drink: DrinkListing,
        quantity: Long,
        sugarLevel: String,
        iceLevel: String,
        topping: ToppingOption,
        notes: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.addToCart(
                drink = drink,
                quantity = quantity,
                sugarLevel = sugarLevel,
                iceLevel = iceLevel,
                toppingName = topping.name,
                toppingPrice = topping.extraPrice,
                notes = notes
            ).onSuccess {
                _messages.emit("${drink.name} ditambahkan ke keranjang")
            }.onFailure {
                _messages.emit("Gagal menambah ke keranjang: ${it.localizedMessage ?: "Coba lagi"}")
            }
            _isBusy.value = false
        }
    }

    fun updateCartQuantity(cartItemId: String, newQuantity: Long) {
        viewModelScope.launch {
            repository.updateCartQuantity(cartItemId, newQuantity)
                .onFailure { _messages.emit("Gagal mengubah jumlah: ${it.localizedMessage ?: "Coba lagi"}") }
        }
    }

    fun removeFromCart(cartItemId: String) {
        viewModelScope.launch {
            repository.removeFromCart(cartItemId)
                .onSuccess { _messages.emit("Item dihapus dari keranjang") }
                .onFailure { _messages.emit("Gagal menghapus item") }
        }
    }

    fun updateProfile(
        displayName: String,
        shopName: String,
        deliveryAddress: String,
        phone: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateUserProfile(displayName, shopName, deliveryAddress, phone)
                .onSuccess { _messages.emit("Profil & alamat berhasil disimpan") }
                .onFailure { _messages.emit("Gagal menyimpan profil: ${it.localizedMessage ?: "Coba lagi"}") }
            _isBusy.value = false
        }
    }

    fun placeOrder(deliveryAddress: String, onOrderSuccess: () -> Unit) {
        val items = (cartItems.value as? UiState.Success)?.data ?: emptyList()
        if (items.isEmpty()) {
            viewModelScope.launch { _messages.emit("Keranjang masih kosong") }
            return
        }
        val cleanAddress = deliveryAddress.trim()
        if (cleanAddress.length < 5) {
            viewModelScope.launch { _messages.emit("Mohon isi alamat pengantaran minimal 5 karakter") }
            return
        }
        val totals = cartTotals.value
        viewModelScope.launch {
            _isBusy.value = true
            repository.placeOrder(
                cartItems = items,
                subtotal = totals.subtotal,
                deliveryFee = totals.deliveryFee,
                discount = totals.discount,
                totalAmount = totals.totalAmount,
                promoCode = totals.appliedPromo,
                paymentMethod = _selectedPaymentMethod.value,
                deliveryAddress = cleanAddress
            ).onSuccess { orderNumber ->
                _promoCodeInput.value = ""
                _messages.emit("Pesanan $orderNumber berhasil dibuat!")
                onOrderSuccess()
            }.onFailure {
                _messages.emit("Checkout gagal: ${it.localizedMessage ?: "Periksa koneksi Anda"}")
            }
            _isBusy.value = false
        }
    }

    fun advanceOrderStatus(orderId: String, nextStatus: OrderStatus) {
        viewModelScope.launch {
            repository.advanceOrderStatus(orderId, nextStatus)
                .onSuccess { _messages.emit("Status pesanan: ${nextStatus.label}") }
                .onFailure { _messages.emit("Gagal memperbarui status pesanan") }
        }
    }

    fun saveSellerDrink(
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
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val sellerDisplayName = userProfile.value?.displayName?.ifBlank { currentUserName } ?: currentUserName
            repository.saveSellerDrink(
                existingId = existingId,
                sellerName = sellerDisplayName,
                shopName = shopName,
                name = name,
                description = description,
                category = category,
                imageKey = imageKey,
                price = price,
                originalPrice = originalPrice,
                sizeLabel = sizeLabel,
                calories = calories,
                stock = stock
            ).onSuccess {
                _messages.emit(if (existingId == null) "Minuman berhasil dipublikasikan ke marketplace!" else "Menu minuman diperbarui")
                onSuccess()
            }.onFailure {
                _messages.emit("Gagal menyimpan menu: ${it.localizedMessage ?: "Periksa data Anda"}")
            }
            _isBusy.value = false
        }
    }

    fun toggleDrinkStatus(drinkId: String, active: Boolean) {
        viewModelScope.launch {
            repository.toggleDrinkStatus(drinkId, active)
                .onSuccess { _messages.emit(if (active) "Menu diaktifkan kembali" else "Menu dijeda sementara") }
                .onFailure { _messages.emit("Gagal mengubah status menu") }
        }
    }

    fun deleteSellerDrink(drinkId: String) {
        viewModelScope.launch {
            repository.deleteSellerDrink(drinkId)
                .onSuccess { _messages.emit("Menu minuman dihapus dari toko") }
                .onFailure { _messages.emit("Gagal menghapus menu") }
        }
    }

    private fun filterDrinks(
        source: List<DrinkListing>,
        query: String,
        category: DrinkCategory,
        favoritesOnly: Boolean,
        favs: Set<String>
    ): List<DrinkListing> {
        val q = query.trim().lowercase()
        return source.filter { drink ->
            val matchCategory = category == DrinkCategory.SEMUA || drink.category == category.label
            val matchQuery = q.isEmpty() ||
                drink.name.lowercase().contains(q) ||
                drink.shopName.lowercase().contains(q) ||
                drink.description.lowercase().contains(q) ||
                drink.category.lowercase().contains(q)
            val matchFav = !favoritesOnly || favs.contains(drink.id)
            matchCategory && matchQuery && matchFav
        }
    }

    private companion object {
        const val TAG = "MarketplaceVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val FREE_DELIVERY_THRESHOLD = 50_000L
        const val STANDARD_DELIVERY_FEE = 8_000L
    }
}

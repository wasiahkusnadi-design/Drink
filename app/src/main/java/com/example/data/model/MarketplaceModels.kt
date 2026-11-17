package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val shopName: String = "",
    val deliveryAddress: String = "",
    val phone: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DrinkListing(
    @DocumentId val id: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val shopName: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = DrinkCategory.KOPI.label,
    val imageKey: String = "coffee",
    val price: Long = 0L,
    val originalPrice: Long = 0L,
    val sizeLabel: String = "Reguler 350ml",
    val calories: Long = 180L,
    val rating: Double = 4.9,
    val soldCount: Long = 0L,
    val stock: Long = 50L,
    val status: String = "ACTIVE",
    val featured: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isPartnerCatalog: Boolean
        get() = sellerId.startsWith("segarku_partner_")
}

@Keep
@IgnoreExtraProperties
data class CartItem(
    @DocumentId val id: String = "",
    val drinkId: String = "",
    val drinkName: String = "",
    val shopName: String = "",
    val sellerId: String = "",
    val category: String = "",
    val imageKey: String = "coffee",
    val unitPrice: Long = 0L,
    val toppingPrice: Long = 0L,
    val quantity: Long = 1L,
    val sugarLevel: String = "Normal",
    val iceLevel: String = "Normal",
    val topping: String = "Tanpa Topping",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val lineTotal: Long
        get() = (unitPrice + toppingPrice) * quantity
}

@Keep
@IgnoreExtraProperties
data class FavoriteItem(
    @DocumentId val id: String = "",
    val drinkId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DrinkOrder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val orderNumber: String = "",
    val summaryTitle: String = "",
    val totalItems: Long = 0L,
    val subtotal: Long = 0L,
    val deliveryFee: Long = 0L,
    val discount: Long = 0L,
    val totalAmount: Long = 0L,
    val promoCode: String = "",
    val paymentMethod: String = "QRIS",
    val deliveryAddress: String = "",
    val status: String = OrderStatus.DIPROSES.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val orderStatus: OrderStatus
        get() = OrderStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val drinkId: String = "",
    val drinkName: String = "",
    val shopName: String = "",
    val imageKey: String = "coffee",
    val unitPrice: Long = 0L,
    val toppingPrice: Long = 0L,
    val quantity: Long = 1L,
    val customizationLabel: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class DrinkCategory(val label: String) {
    SEMUA("Semua"),
    KOPI("Kopi"),
    TEH_MATCHA("Teh & Matcha"),
    JUS_SEGAR("Jus & Segar"),
    BOBA_SUSU("Boba & Susu");

    companion object {
        val sellableCategories = listOf(KOPI, TEH_MATCHA, JUS_SEGAR, BOBA_SUSU)
        fun fromLabel(label: String): DrinkCategory =
            entries.firstOrNull { it.label == label } ?: KOPI
    }
}

enum class OrderStatus(val label: String) {
    DIPROSES("Sedang Diracik"),
    DIKIRIM("Dalam Pengantaran"),
    SELESAI("Pesanan Selesai"),
    DIBATALKAN("Dibatalkan");

    companion object {
        fun fromRaw(raw: String): OrderStatus =
            entries.firstOrNull { it.name == raw } ?: DIPROSES
    }
}

data class ToppingOption(
    val name: String,
    val extraPrice: Long
)

data class ImagePresetOption(
    val key: String,
    val label: String
)

object DrinkOptions {
    val sugarLevels = listOf("Normal", "Less Sugar", "No Sugar")
    val iceLevels = listOf("Normal", "Less Ice", "No Ice")
    val toppings = listOf(
        ToppingOption("Tanpa Topping", 0L),
        ToppingOption("Boba Brown Sugar", 5000L),
        ToppingOption("Cheese Foam", 6000L),
        ToppingOption("Grass Jelly", 4000L),
        ToppingOption("Popping Lychee", 5500L)
    )
    val paymentMethods = listOf(
        "QRIS",
        "GoPay / E-Wallet",
        "Transfer Bank",
        "COD (Bayar di Tempat)"
    )
    val imagePresets = listOf(
        ImagePresetOption("coffee", "Es Kopi"),
        ImagePresetOption("matcha", "Matcha & Teh"),
        ImagePresetOption("citrus", "Jus & Citrus"),
        ImagePresetOption("berry", "Berry Hibiscus"),
        ImagePresetOption("boba", "Boba & Susu")
    )
}

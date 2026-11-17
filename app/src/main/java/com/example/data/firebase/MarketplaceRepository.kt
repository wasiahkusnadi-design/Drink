package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.DrinkListing
import com.example.data.model.DrinkOrder
import com.example.data.model.FavoriteItem
import com.example.data.model.OrderLineItem
import com.example.data.model.OrderStatus
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MarketplaceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
    }

    private fun <T> Flow<T>.handleStreamError(
        uid: String,
        operationType: OperationType,
        path: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // 1. Observe all ACTIVE marketplace drinks from sellers
    fun observeActiveDrinks(uid: String): Flow<List<DrinkListing>> {
        val path = "drinks"
        return db.collection(path)
            .whereEqualTo("status", "ACTIVE")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    DrinkListing::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .handleStreamError(uid, OperationType.LIST, path)
    }

    // 2. Observe current user's own seller listings (ACTIVE & PAUSED)
    fun observeMySellerDrinks(uid: String): Flow<List<DrinkListing>> {
        val path = "drinks"
        return db.collection(path)
            .whereEqualTo("sellerId", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    DrinkListing::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .handleStreamError(uid, OperationType.LIST, path)
    }

    // 3. Observe user's private profile
    fun observeUserProfile(uid: String): Flow<UserProfile?> {
        val path = "users/$uid"
        return db.collection("users").document(uid)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .handleStreamError(uid, OperationType.GET, path)
    }

    // 4. Observe user's cart items
    fun observeCart(uid: String): Flow<List<CartItem>> {
        val path = "users/$uid/cart"
        return db.collection("users").document(uid).collection("cart")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CartItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .handleStreamError(uid, OperationType.LIST, path)
    }

    // 5. Observe user's favorite drink IDs
    fun observeFavorites(uid: String): Flow<Set<String>> {
        val path = "users/$uid/favorites"
        return db.collection("users").document(uid).collection("favorites")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FavoriteItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.drinkId.ifBlank { it.id } }.toSet()
            }
            .handleStreamError(uid, OperationType.LIST, path)
    }

    // 6. Observe user's placed orders
    fun observeOrders(uid: String): Flow<List<DrinkOrder>> {
        val path = "users/$uid/orders"
        return db.collection("users").document(uid).collection("orders")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    DrinkOrder::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .handleStreamError(uid, OperationType.LIST, path)
    }

    // 7. Observe line items for a specific order
    fun observeOrderItems(uid: String, orderId: String): Flow<List<OrderLineItem>> {
        val path = "users/$uid/orders/$orderId/items"
        return db.collection("users").document(uid)
            .collection("orders").document(orderId)
            .collection("items")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    OrderLineItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .handleStreamError(uid, OperationType.LIST, path)
    }

    // Ensure user profile exists on sign-in
    suspend fun ensureUserProfile(
        displayName: String,
        email: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val safeName = displayName.trim().ifBlank { "Pelanggan SegarKu" }.take(80)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to email.trim().take(120),
                    "shopName" to "",
                    "deliveryAddress" to "Jl. Kemang Raya No. 12, Jakarta Selatan",
                    "phone" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    // Update user profile (delivery address, phone, shop name, display name)
    suspend fun updateUserProfile(
        displayName: String,
        shopName: String,
        deliveryAddress: String,
        phone: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanName = displayName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Nama tampilan tidak boleh kosong." }
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "email" to (auth.currentUser?.email ?: "").take(120),
                    "shopName" to shopName.trim().take(80),
                    "deliveryAddress" to deliveryAddress.trim().take(250),
                    "phone" to phone.trim().take(30),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to cleanName,
                        "shopName" to shopName.trim().take(80),
                        "deliveryAddress" to deliveryAddress.trim().take(250),
                        "phone" to phone.trim().take(30),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // Add customized drink to cart
    suspend fun addToCart(
        drink: DrinkListing,
        quantity: Long,
        sugarLevel: String,
        iceLevel: String,
        toppingName: String,
        toppingPrice: Long,
        notes: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/cart"
        return try {
            val uid = requireUserId()
            require(drink.id.isNotBlank()) { "ID minuman tidak valid." }
            require(quantity in 1L..99L) { "Jumlah harus antara 1 dan 99." }
            val docRef = db.collection("users").document(uid).collection("cart").document()
            docPath = docRef.path
            val payload = mapOf(
                "drinkId" to drink.id.take(128),
                "drinkName" to drink.name.take(80),
                "shopName" to drink.shopName.take(80),
                "sellerId" to drink.sellerId.take(128),
                "category" to drink.category.take(40),
                "imageKey" to drink.imageKey,
                "unitPrice" to drink.price.coerceIn(1000L, 1_000_000L),
                "toppingPrice" to toppingPrice.coerceIn(0L, 200_000L),
                "quantity" to quantity,
                "sugarLevel" to sugarLevel,
                "iceLevel" to iceLevel,
                "topping" to toppingName.take(60),
                "notes" to notes.trim().take(160),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCartQuantity(cartItemId: String, newQuantity: Long): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/cart/$cartItemId"
        return try {
            val uid = requireUserId()
            require(cartItemId.isNotBlank()) { "ID keranjang tidak valid." }
            val docRef = db.collection("users").document(uid)
                .collection("cart").document(cartItemId)
            docPath = docRef.path
            if (newQuantity <= 0L) {
                docRef.delete().await()
            } else {
                require(newQuantity <= 99L) { "Maksimal 99 cup per item." }
                docRef.update(
                    mapOf(
                        "quantity" to newQuantity,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeFromCart(cartItemId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/cart/$cartItemId"
        return try {
            val uid = requireUserId()
            require(cartItemId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("cart").document(cartItemId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(drinkId: String, currentlyFavorite: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/favorites/$drinkId"
        return try {
            val uid = requireUserId()
            require(drinkId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("favorites").document(drinkId)
            docPath = docRef.path
            if (currentlyFavorite) {
                docRef.delete().await()
            } else {
                docRef.set(
                    mapOf(
                        "drinkId" to drinkId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    // Atomic checkout: creates order + order line items + clears cart in one WriteBatch
    suspend fun placeOrder(
        cartItems: List<CartItem>,
        subtotal: Long,
        deliveryFee: Long,
        discount: Long,
        totalAmount: Long,
        promoCode: String,
        paymentMethod: String,
        deliveryAddress: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/orders"
        return try {
            val uid = requireUserId()
            require(cartItems.isNotEmpty()) { "Keranjang kosong." }
            val cleanAddress = deliveryAddress.trim().take(250)
            require(cleanAddress.length >= 5) { "Alamat pengantaran minimal 5 karakter." }

            val userRef = db.collection("users").document(uid)
            val orderRef = userRef.collection("orders").document()
            docPath = orderRef.path
            val orderId = orderRef.id
            val orderNumber = "SGK-${(1000..9999).random()}-${UUID.randomUUID().toString().take(4).uppercase()}"
            val totalItems = cartItems.sumOf { it.quantity }.coerceIn(1L, 500L)
            val firstDrinkName = cartItems.first().drinkName
            val summaryTitle = if (cartItems.size == 1) {
                firstDrinkName.take(120)
            } else {
                "$firstDrinkName & ${cartItems.size - 1} lainnya".take(120)
            }

            val batch = db.batch()
            val orderPayload = mapOf(
                "userId" to uid,
                "orderNumber" to orderNumber,
                "summaryTitle" to summaryTitle,
                "totalItems" to totalItems,
                "subtotal" to subtotal.coerceIn(1000L, 50_000_000L),
                "deliveryFee" to deliveryFee.coerceIn(0L, 500_000L),
                "discount" to discount.coerceIn(0L, 5_000_000L),
                "totalAmount" to totalAmount.coerceIn(0L, 50_000_000L),
                "promoCode" to promoCode.trim().take(30),
                "paymentMethod" to paymentMethod,
                "deliveryAddress" to cleanAddress,
                "status" to OrderStatus.DIPROSES.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(orderRef, orderPayload)

            cartItems.forEach { item ->
                val itemRef = orderRef.collection("items").document()
                val customLabel = buildString {
                    append("Gula: ${item.sugarLevel}, Es: ${item.iceLevel}")
                    if (item.topping != "Tanpa Topping") {
                        append(", +${item.topping}")
                    }
                    if (item.notes.isNotBlank()) {
                        append(" (${item.notes})")
                    }
                }.take(160)

                batch.set(
                    itemRef,
                    mapOf(
                        "orderId" to orderId,
                        "drinkId" to item.drinkId,
                        "drinkName" to item.drinkName.take(80),
                        "shopName" to item.shopName.take(80),
                        "imageKey" to item.imageKey,
                        "unitPrice" to item.unitPrice,
                        "toppingPrice" to item.toppingPrice,
                        "quantity" to item.quantity,
                        "customizationLabel" to customLabel,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                if (item.id.isNotBlank()) {
                    batch.delete(userRef.collection("cart").document(item.id))
                }
            }

            batch.commit().await()
            Result.success(orderNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun advanceOrderStatus(
        orderId: String,
        nextStatus: OrderStatus
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/orders/$orderId"
        return try {
            val uid = requireUserId()
            require(orderId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("orders").document(orderId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to nextStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // Seller: create or update beverage listing
    suspend fun saveSellerDrink(
        existingId: String?,
        sellerName: String,
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
    ): Result<Unit> {
        var docPath = "drinks"
        return try {
            val uid = requireUserId()
            val cleanShop = shopName.trim().take(80)
            val cleanName = name.trim().take(80)
            val cleanDesc = description.trim().take(400)
            val cleanSize = sizeLabel.trim().ifBlank { "Reguler 350ml" }.take(40)
            val cleanSeller = sellerName.trim().ifBlank { "Penjual SegarKu" }.take(80)

            require(cleanShop.length >= 2) { "Nama toko minimal 2 karakter." }
            require(cleanName.length >= 2) { "Nama minuman minimal 2 karakter." }
            require(cleanDesc.length >= 5) { "Deskripsi minimal 5 karakter." }
            require(price in 1000L..1_000_000L) { "Harga harus antara Rp1.000 - Rp1.000.000." }
            require(originalPrice in 0L..1_000_000L) { "Harga coret tidak valid." }
            require(calories in 0L..2000L) { "Kalori harus antara 0 - 2000 kkal." }
            require(stock in 0L..10000L) { "Stok harus antara 0 - 10.000." }

            if (existingId.isNullOrBlank()) {
                val docRef = db.collection("drinks").document()
                docPath = docRef.path
                val payload = mapOf(
                    "sellerId" to uid,
                    "sellerName" to cleanSeller,
                    "shopName" to cleanShop,
                    "name" to cleanName,
                    "description" to cleanDesc,
                    "category" to category,
                    "imageKey" to imageKey,
                    "price" to price,
                    "originalPrice" to originalPrice,
                    "sizeLabel" to cleanSize,
                    "calories" to calories,
                    "rating" to 5.0,
                    "soldCount" to 0L,
                    "stock" to stock,
                    "status" to "ACTIVE",
                    "featured" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val docRef = db.collection("drinks").document(existingId)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "sellerName" to cleanSeller,
                        "shopName" to cleanShop,
                        "name" to cleanName,
                        "description" to cleanDesc,
                        "category" to category,
                        "imageKey" to imageKey,
                        "price" to price,
                        "originalPrice" to originalPrice,
                        "sizeLabel" to cleanSize,
                        "calories" to calories,
                        "stock" to stock,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleDrinkStatus(drinkId: String, active: Boolean): Result<Unit> {
        var docPath = "drinks/$drinkId"
        return try {
            requireUserId()
            require(drinkId.isNotBlank())
            val docRef = db.collection("drinks").document(drinkId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to if (active) "ACTIVE" else "PAUSED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSellerDrink(drinkId: String): Result<Unit> {
        var docPath = "drinks/$drinkId"
        return try {
            requireUserId()
            require(drinkId.isNotBlank())
            val docRef = db.collection("drinks").document(drinkId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}

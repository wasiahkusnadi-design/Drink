package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.firebase.MarketplaceRepository
import com.example.data.model.DrinkListing
import com.example.data.model.DrinkOptions
import com.example.ui.components.DrinkDetailBottomSheet
import com.example.ui.screens.CartCheckoutScreen
import com.example.ui.screens.HomeMarketplaceScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.ProfileDialog
import com.example.ui.screens.SellerStoreScreen
import com.example.ui.state.MarketplaceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_HOME = "home"
const val ROUTE_CART = "cart"
const val ROUTE_ORDERS = "orders"
const val ROUTE_STORE = "store"

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val mainDestinations = listOf(
    NavDestinationItem(ROUTE_HOME, "Beranda", Icons.Rounded.Home, Icons.Outlined.Home),
    NavDestinationItem(ROUTE_CART, "Keranjang", Icons.Rounded.ShoppingBag, Icons.Outlined.ShoppingBag),
    NavDestinationItem(ROUTE_ORDERS, "Pesanan", Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
    NavDestinationItem(ROUTE_STORE, "Toko Saya", Icons.Rounded.Storefront, Icons.Outlined.Storefront)
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            MainMarketplaceShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "Pelanggan SegarKu",
                userEmail = user.email ?: ""
            )
        }
    }
}

@Composable
private fun MainMarketplaceShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String
) {
    val viewModel: MarketplaceViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MarketplaceViewModel(
                    repository = MarketplaceRepository(db),
                    currentUserId = currentUserId,
                    currentUserName = userDisplayName,
                    currentUserEmail = userEmail
                )
            }
        }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val catalogState by viewModel.catalogDrinks.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val cartState by viewModel.cartItems.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val cartTotals by viewModel.cartTotals.collectAsStateWithLifecycle()
    val promoCode by viewModel.promoCodeInput.collectAsStateWithLifecycle()
    val selectedPaymentMethod by viewModel.selectedPaymentMethod.collectAsStateWithLifecycle()
    val ordersState by viewModel.orders.collectAsStateWithLifecycle()
    val mySellerDrinksState by viewModel.mySellerDrinks.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var selectedDrink by remember { mutableStateOf<DrinkListing?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        mainDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
                                    if (dest.route == ROUTE_CART && cartCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge { Text(cartCount.toString()) }
                                            }
                                        ) {
                                            Icon(icon, contentDescription = dest.label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = dest.label)
                                    }
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail {
                        mainDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
                                    if (dest.route == ROUTE_CART && cartCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge { Text(cartCount.toString()) }
                                            }
                                        ) {
                                            Icon(icon, contentDescription = dest.label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = dest.label)
                                    }
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME
                    ) {
                        composable(ROUTE_HOME) {
                            HomeMarketplaceScreen(
                                userDisplayName = userDisplayName,
                                userProfile = userProfile,
                                catalogState = catalogState,
                                favoriteIds = favoriteIds,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategory,
                                favoritesOnly = favoritesOnly,
                                onSearchQueryChange = viewModel::onSearchQueryChanged,
                                onCategorySelect = viewModel::onCategorySelected,
                                onToggleFavoritesOnly = viewModel::toggleFavoritesFilter,
                                onToggleDrinkFavorite = viewModel::toggleFavorite,
                                onOpenDrinkDetail = { selectedDrink = it },
                                onQuickAdd = { drink ->
                                    viewModel.addToCart(
                                        drink = drink,
                                        quantity = 1L,
                                        sugarLevel = DrinkOptions.sugarLevels.first(),
                                        iceLevel = DrinkOptions.iceLevels.first(),
                                        topping = DrinkOptions.toppings.first(),
                                        notes = ""
                                    )
                                },
                                onOpenProfile = { showProfileDialog = true }
                            )
                        }

                        composable(ROUTE_CART) {
                            CartCheckoutScreen(
                                cartState = cartState,
                                totals = cartTotals,
                                userProfile = userProfile,
                                promoCode = promoCode,
                                selectedPaymentMethod = selectedPaymentMethod,
                                isBusy = isBusy,
                                onPromoCodeChange = viewModel::onPromoCodeChanged,
                                onPaymentMethodSelect = viewModel::onPaymentMethodSelected,
                                onUpdateQuantity = viewModel::updateCartQuantity,
                                onRemoveItem = viewModel::removeFromCart,
                                onPlaceOrder = { address ->
                                    viewModel.placeOrder(address) {
                                        navigateToTab(ROUTE_ORDERS)
                                    }
                                },
                                onBrowseDrinks = { navigateToTab(ROUTE_HOME) }
                            )
                        }

                        composable(ROUTE_ORDERS) {
                            OrdersScreen(
                                ordersState = ordersState,
                                observeOrderItems = viewModel::observeOrderItems,
                                onAdvanceStatus = viewModel::advanceOrderStatus,
                                onBrowseDrinks = { navigateToTab(ROUTE_HOME) }
                            )
                        }

                        composable(ROUTE_STORE) {
                            SellerStoreScreen(
                                userProfile = userProfile,
                                myDrinksState = mySellerDrinksState,
                                isBusy = isBusy,
                                onSaveDrink = viewModel::saveSellerDrink,
                                onToggleStatus = viewModel::toggleDrinkStatus,
                                onDeleteDrink = viewModel::deleteSellerDrink
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedDrink != null) {
        val drink = selectedDrink!!
        DrinkDetailBottomSheet(
            drink = drink,
            isFavorite = favoriteIds.contains(drink.id),
            onToggleFavorite = { viewModel.toggleFavorite(drink.id) },
            onDismiss = { selectedDrink = null },
            onAddToCart = { quantity, sugar, ice, topping, notes ->
                viewModel.addToCart(drink, quantity, sugar, ice, topping, notes)
                selectedDrink = null
            }
        )
    }

    if (showProfileDialog) {
        ProfileDialog(
            userEmail = userEmail,
            fallbackName = userDisplayName,
            profile = userProfile,
            isBusy = isBusy,
            onDismiss = { showProfileDialog = false },
            onSave = { name, shop, address, phone ->
                viewModel.updateProfile(name, shop, address, phone)
            },
            onSignOut = {
                showProfileDialog = false
                signOut(credentialManager, scope)
            }
        )
    }
}

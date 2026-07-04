package com.kaysyndikayte.allowancetracker.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Category(val displayName: String, val icon: ImageVector) {
    FOOD("Food & Dining", Icons.Filled.Restaurant),
    TRANSPORT("Transport", Icons.Filled.DirectionsCar),
    SHOPPING("Shopping", Icons.Filled.ShoppingBag),
    ENTERTAINMENT("Entertainment", Icons.Filled.Movie),
    BILLS("Bills & Utilities", Icons.Filled.Receipt),
    HEALTH("Health", Icons.Filled.LocalHospital),
    GROCERIES("Groceries", Icons.Filled.LocalGroceryStore),
    EDUCATION("Education", Icons.Filled.School),
    RENT("Rent", Icons.Filled.Home),
    SUBSCRIPTIONS("Subscriptions", Icons.Filled.Subscriptions),
    OTHER("Other", Icons.Filled.Category)
}
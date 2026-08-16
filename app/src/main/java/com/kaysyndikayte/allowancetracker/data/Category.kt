package com.kaysyndikayte.allowancetracker.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Category(val displayName: String, val icon: ImageVector) {
    SELECT_CATEGORY("Select a category", Icons.Filled.QuestionMark),
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
    PERSONAL("Personal", Icons.Filled.Person),
    OTHER("Other", Icons.Filled.Category);

    companion object {
        /** Category names are stored as plain strings in the transactions table, so a value
         *  written by an older build (or a since-renamed constant) arrives as something
         *  valueOf() throws on -- which took down the entire list being rendered. */
        fun fromName(name: String): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
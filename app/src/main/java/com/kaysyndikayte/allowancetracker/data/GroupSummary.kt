package com.kaysyndikayte.allowancetracker.data

data class GroupSummary(
    val id: String,
    val name: String,
    val memberCount: Int,
    val iconName: String? = "staff"
)
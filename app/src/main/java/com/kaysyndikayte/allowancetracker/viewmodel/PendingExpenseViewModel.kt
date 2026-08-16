package com.kaysyndikayte.allowancetracker.viewmodel

import androidx.lifecycle.ViewModel
import com.kaysyndikayte.allowancetracker.repository.MemberProfile
import java.math.BigDecimal

class PendingExpenseViewModel : ViewModel() {
    var groupId: String = ""
    var reason: String = ""
    var category: String = "OTHER"
    var totalAmount: BigDecimal = BigDecimal.ZERO
    var paidBy: String = ""
    var participants: List<Pair<String, String>> = emptyList() // id to name

    fun reset() {
        groupId = ""; reason = ""; category = "OTHER"
        totalAmount = BigDecimal.ZERO; paidBy = ""; participants = emptyList()
    }
}
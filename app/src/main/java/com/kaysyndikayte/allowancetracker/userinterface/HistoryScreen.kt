package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.utils.DateUtils
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import androidx.compose.foundation.clickable

@Composable
fun BlinkingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "blinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(Color.Green, CircleShape)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: AllowanceViewModel, onBack: () -> Unit, onSelectRange: () -> Unit) {
    val ranges by viewModel.allDateRanges.collectAsState()
    val money = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())
    val today = LocalDate.now().toEpochDay()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Allowance Periods") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(ranges) { range ->
                val isActive = (!range.isForceEnded) && (today <= range.endEpochDay)
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(range.name)
                            if (isActive) {
                                Spacer(Modifier.width(8.dp))
                                BlinkingDot()
                            }
                        }
                    },
                    supportingContent = {
                        Column {
                            Text(DateUtils.formatRange(range.startEpochDay, range.endEpochDay))
                            Text("Allowance: ${money.format(range.allowanceAmount)}")
                        }
                    },
                    modifier = Modifier.clickable {
                        viewModel.selectRange(range.id)
                        onSelectRange()
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

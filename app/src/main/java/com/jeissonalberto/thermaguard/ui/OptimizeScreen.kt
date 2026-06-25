package com.jeissonalberto.thermaguard.ui
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import com.jeissonalberto.thermaguard.domain.ThermalViewModel

@Composable
fun OptimizeScreen(viewModel: ThermalViewModel) {
    val recs by viewModel.coolingRecs.collectAsState()
    LazyColumn {
        items(recs) { rec ->
            Text(text = rec.title)
        }
    }
}

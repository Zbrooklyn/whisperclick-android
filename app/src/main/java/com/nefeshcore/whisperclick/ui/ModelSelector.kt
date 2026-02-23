package com.nefeshcore.whisperclick.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModelSelector(
    currentModel: String,
    onModelSelected: (String) -> Unit
) {
    val models = listOf("Tiny (Fast)", "Base (Balanced)", "Small (Accurate)")
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Select Whisper Model:")
        models.forEach { model ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModelSelected(model) }
                    .padding(8.dp)
            ) {
                RadioButton(
                    selected = (model == currentModel),
                    onClick = { onModelSelected(model) }
                )
                Text(
                    text = model,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

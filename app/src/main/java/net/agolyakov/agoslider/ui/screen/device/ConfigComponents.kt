package net.agolyakov.agoslider.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ----------------------------------------------------------------------------
// Reusable per-axis (X, C, B) configuration components
// ----------------------------------------------------------------------------
@Composable
fun ConfigTriple(
    title: String,
    values: Triple<Int, Int, Int>,
    onValueChange: (Int, Int, Int) -> Unit
) {
    var x by remember(values.first) { mutableStateOf(values.first.toString()) }
    var c by remember(values.second) { mutableStateOf(values.second.toString()) }
    var b by remember(values.third) { mutableStateOf(values.third.toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = x, onValueChange = { x = it }, label = { Text("X") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = c, onValueChange = { c = it }, label = { Text("C") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = b, onValueChange = { b = it }, label = { Text("B") }, modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                val xi = x.toIntOrNull() ?: values.first
                val ci = c.toIntOrNull() ?: values.second
                val bi = b.toIntOrNull() ?: values.third
                onValueChange(xi, ci, bi)
            }) { Text("Set") }
        }
    }
}

@Composable
fun FloatTriple(
    title: String,
    values: Triple<Float, Float, Float>,
    onValueChange: (Float, Float, Float) -> Unit
) {
    var x by remember(values.first) { mutableStateOf(values.first.toString()) }
    var c by remember(values.second) { mutableStateOf(values.second.toString()) }
    var b by remember(values.third) { mutableStateOf(values.third.toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = x, onValueChange = { x = it }, label = { Text("X") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = c, onValueChange = { c = it }, label = { Text("C") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = b, onValueChange = { b = it }, label = { Text("B") }, modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                val xf = x.toFloatOrNull() ?: values.first
                val cf = c.toFloatOrNull() ?: values.second
                val bf = b.toFloatOrNull() ?: values.third
                onValueChange(xf, cf, bf)
            }) { Text("Set") }
        }
    }
}

@Composable
fun BoolTriple(
    title: String,
    values: Triple<Boolean, Boolean, Boolean>,
    onValueChange: (Boolean, Boolean, Boolean) -> Unit
) {
    var x by remember(values.first) { mutableStateOf(values.first) }
    var c by remember(values.second) { mutableStateOf(values.second) }
    var b by remember(values.third) { mutableStateOf(values.third) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row { Text("X: "); Switch(checked = x, onCheckedChange = { x = it }) }
                Row { Text("C: "); Switch(checked = c, onCheckedChange = { c = it }) }
                Row { Text("B: "); Switch(checked = b, onCheckedChange = { b = it }) }
            }
            Button(onClick = { onValueChange(x, c, b) }) { Text("Set") }
        }
    }
}

package app.fenek.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class Choice(val key: String, val title: String, val supporting: String? = null)

/** A read-only field that opens a list of choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceField(
    choices: List<Choice>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = choices.firstOrNull { it.key == selectedKey } ?: choices.first()
    Box(modifier) {
        OutlinedCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(selected.title, style = MaterialTheme.typography.bodyLarge)
                    selected.supporting?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(choice.title)
                            choice.supporting?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(choice.key)
                    },
                )
            }
        }
    }
}

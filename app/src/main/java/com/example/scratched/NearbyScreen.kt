package com.example.scratched

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scratched.meshMVP.transport.NearbyProfile

@Composable
internal fun ProfileAvatar(profile: NearbyProfile, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (surface, ink) = when (profile.id and 3) {
        0 -> colors.primaryContainer to colors.onPrimaryContainer
        1 -> colors.secondaryContainer to colors.onSecondaryContainer
        2 -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.inverseSurface to colors.inverseOnSurface
    }
    Box(modifier.size(48.dp).background(surface, CircleShape), contentAlignment = Alignment.Center) {
        Text(profile.name.take(1).uppercase(), color = ink, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun NearbyScreen(profile: NearbyProfile, people: List<NearbyPerson>, issue: String?,
    open: (NearbyPerson) -> Unit, rename: (String) -> Unit, retry: () -> Unit, modifier: Modifier = Modifier) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Phoenix", color = colors.primary, fontWeight = FontWeight.Bold)
            Text("Люди рядом", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            Surface(onClick = { editing = true }, shape = RoundedCornerShape(22.dp), color = colors.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(profile)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Ваше имя", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        Text("${profile.name} · ${profile.tag}", style = MaterialTheme.typography.titleMedium)
                    }
                    Icon(Icons.Default.Edit, contentDescription = "Изменить имя")
                }
            }
            Spacer(Modifier.height(22.dp))
        }
        if (issue != null) item {
            Text(issue, color = colors.error)
            TextButton(onClick = retry) { Text("Попробовать снова") }
        }
        if (people.isEmpty() && issue == null) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PeopleOutline, null, Modifier.size(40.dp), tint = colors.primary)
                Text("Пока никого рядом", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
                Text("Здесь появятся люди, открывшие Phoenix поблизости.",
                    Modifier.padding(top = 6.dp), color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        items(people, key = { it.profile.id }) { person ->
            Surface(onClick = { open(person) }, shape = RoundedCornerShape(18.dp), color = colors.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(person.profile)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(person.profile.name, style = MaterialTheme.typography.titleMedium)
                        Text("Рядом · ${person.profile.tag}", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ChevronRight, "Открыть диалог")
                }
            }
        }
    }
    if (editing) {
        var name by rememberSaveable { mutableStateOf(profile.name) }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Ваше имя") },
            text = {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Имя в Phoenix") },
                    supportingText = { Text("До 8 букв или цифр. Можно писать по-русски.") })
            },
            confirmButton = {
                TextButton(onClick = { rename(name.trim()); editing = false },
                    enabled = NearbyProfile.validName(name.trim())) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Отмена") } })
    }
}

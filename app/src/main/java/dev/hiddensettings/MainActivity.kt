package dev.hiddensettings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val Background = Color(0xFF14181F)
private val Cell = Color(0xFF1E2430)
private val CellFocused = Color(0xFF3D7BD9)
private val Muted = Color(0xFFA3ABB5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sections = loadSections(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Background, surface = Background)) {
                HiddenSettingsScreen(sections, onOpen = ::open)
            }
        }
    }

    private fun open(entry: Entry) {
        val intent = resolve(this, entry)
        if (intent == null) {
            toast("\"${entry.title}\" isn't available on this TV")
            return
        }
        try {
            // Opens in the Settings app's own task, so it doesn't stack on top of this app and
            // relaunching Hidden Settings always shows its own list. Clearing that task first stops
            // a screen left open earlier (e.g. Add Wi-Fi) from reappearing when Back is pressed.
            startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } catch (e: ActivityNotFoundException) {
            toast("\"${entry.title}\" isn't available on this TV")
        } catch (e: SecurityException) {
            toast("The system won't let apps open \"${entry.title}\"")
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

@Composable
private fun HiddenSettingsScreen(sections: List<Section>, onOpen: (Entry) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val firstSection = remember { FocusRequester() }
    val firstEntry = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstSection.requestFocus() } }

    // 48/27dp keeps everything inside the TV's overscan-safe area.
    Column(Modifier.fillMaxSize().background(Background).padding(horizontal = 48.dp, vertical = 27.dp)) {
        Text("Hidden Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Settings screens your TV has but doesn't show in its menus", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.width(260.dp).fillMaxHeight(),
            ) {
                items(sections.size) { i ->
                    Cell(
                        title = sections[i].title,
                        subtitle = "${sections[i].entries.size} screens",
                        highlighted = i == selected,
                        onClick = { selected = i },
                        modifier = Modifier
                            .then(if (i == 0) Modifier.focusRequester(firstSection) else Modifier)
                            // Right always lands on the top entry, not whichever one is level with this row.
                            .focusProperties { right = firstEntry }
                            .onFocusChanged { if (it.isFocused) selected = i },
                    )
                }
            }
            Spacer(Modifier.width(24.dp))
            val listState = rememberLazyListState()
            LaunchedEffect(selected) { listState.scrollToItem(0) }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                val entries = sections.getOrNull(selected)?.entries.orEmpty()
                items(entries.size, key = { entries[it].title + entries[it].subtitle }) { j ->
                    Cell(
                        title = entries[j].title,
                        subtitle = entries[j].subtitle,
                        onClick = { onOpen(entries[j]) },
                        modifier = if (j == 0) Modifier.focusRequester(firstEntry) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun Cell(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(
                when {
                    focused -> CellFocused
                    highlighted -> CellFocused.copy(alpha = 0.35f)
                    else -> Cell
                },
                shape,
            )
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = if (focused) Color.White.copy(alpha = 0.85f) else Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

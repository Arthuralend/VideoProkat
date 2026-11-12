package am.arthur.vportal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import am.arthur.vportal.data.PortalConfig

@Composable
fun ConnectScreen(
    initial: String,
    onConnect: (String) -> Unit,
    onDiagnostics: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldFocus = remember { FocusRequester() }

    fun submit() {
        if (PortalConfig.parse(text) == null) {
            error = "Вставьте строку вида portal::[key:…]http://…/api/v1/"
        } else {
            error = null
            onConnect(text)
        }
    }

    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    Column(
        Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("V", color = Accent, fontSize = 64.sp, fontWeight = FontWeight.Black)
            Text("Portal", fontSize = 56.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Огромный выбор фильмов, сериалов, мультфильмов для детей и взрослых,\n" +
                "тв-шоу и познавательных программ",
            color = TextDim,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = null },
            singleLine = true,
            label = { Text("Строка подключения к медиатеке") },
            placeholder = { Text("portal::[key:…]http://…/api/v1/") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }, onDone = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                focusedLabelColor = Accent,
                cursorColor = Accent,
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
            ),
            modifier = Modifier.width(820.dp),
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = ErrorRed, fontSize = 15.sp)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton("Подключить", ::submit, icon = Icons.Filled.PlayArrow, primary = true, modifier = Modifier.focusRequester(fieldFocus))
            TvButton("Диагностика", {
                if (PortalConfig.parse(text) != null) onDiagnostics(text)
                else error = "Сначала вставьте строку подключения"
            }, icon = Icons.Filled.Info)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Вставьте строку из личного кабинета (без кавычек) и нажмите «Подключить»",
            color = TextDim,
            fontSize = 15.sp,
        )
    }
}

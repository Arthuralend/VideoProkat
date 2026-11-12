package am.arthur.vportal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import am.arthur.vportal.data.Diagnostics
import am.arthur.vportal.data.PortalConfig

@Composable
fun DiagnosticsScreen(raw: String) {
    val results by produceState<List<Diagnostics.Result>?>(null, raw) {
        val portal = PortalConfig.parse(raw)
        value = if (portal == null) listOf(Diagnostics.Result("Ошибка", "Не удалось разобрать адрес: $raw"))
        else Diagnostics.run(portal)
    }
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 32.dp)) {
        Text("Диагностика портала", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Ответы сервера на разные способы подключения. Листайте стрелками пульта; " +
                "сфотографируйте экраны и пришлите — по ним настраивается подключение. Назад — выход.",
            color = TextDim, fontSize = 16.sp,
        )
        Spacer(Modifier.height(16.dp))
        val list = results
        if (list == null) {
            Loader()
        } else {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(list) { i, r ->
                    DiagCard(r, if (i == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
        }
    }
}

@Composable
private fun DiagCard(r: Diagnostics.Result, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(18.dp)
    ) {
        Text(r.title, color = if (r.title.contains("200")) Accent else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(r.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFCDD1D8), lineHeight = 18.sp)
    }
}

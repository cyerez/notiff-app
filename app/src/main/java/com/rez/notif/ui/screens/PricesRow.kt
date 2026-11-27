package com.rez.notif.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rez.notif.classify.Prices
import com.rez.notif.data.PriceStore
import com.rez.notif.i18n.t
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun priceIcon(item: Prices.Item): Int = when (item) {
    Prices.Item.DOLLAR -> com.rez.notif.R.drawable.ic_price_dollar
    Prices.Item.GOLD18 -> com.rez.notif.R.drawable.ic_price_gold
    Prices.Item.COIN -> com.rez.notif.R.drawable.ic_price_coin
}

/** Dollar, 18k gold and Emami coin on the home screen; fetched at most every 3 hours (the app's only internet use). */
@Composable
fun PricesRow() {
    val g = LocalGlass.current
    val context = LocalContext.current
    var saved by remember { mutableStateOf(PriceStore.saved(context)) }
    LaunchedEffect(Unit) {
        val fresh = withContext(Dispatchers.IO) { runCatching { PriceStore.refreshIfOld(context) }.getOrDefault(false) }
        if (fresh) saved = PriceStore.saved(context)
    }
    if (saved.quotes.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Prices.Item.entries.forEach { item ->
            val q = saved.quotes.firstOrNull { it.item == item }
            GlassCard(Modifier.weight(1f), contentPadding = 10.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(priceIcon(item)), null, Modifier.size(18.dp))
                        androidx.compose.foundation.layout.Spacer(Modifier.size(5.dp))
                        Text(t(item.label), style = MaterialTheme.typography.labelSmall, color = g.textDim, maxLines = 1)
                    }
                    if (q == null) {
                        Text("—", color = g.text)
                    } else {
                        val (num, million) = Prices.shortToman(q.rial)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                fa(num.replace('.', '٫')) + (if (million) t(" م") else t(" هزار")),
                                style = MaterialTheme.typography.titleSmall, color = g.text, fontWeight = FontWeight.Bold, maxLines = 1,
                            )
                            when {
                                q.changePct > 0.05 -> Text(" ▲", style = MaterialTheme.typography.labelSmall, color = g.moneyIn)
                                q.changePct < -0.05 -> Text(" ▼", style = MaterialTheme.typography.labelSmall, color = g.moneyOut)
                            }
                        }
                    }
                }
            }
        }
    }
}

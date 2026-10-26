@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.hotelreception

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
// ВЕРХНЯЯ ПАНЕЛЬ.
//  - Главная: логотип Qonaev слева, название раздела справа.
//  - Остальные экраны: название по центру, на внутренних экранах ещё стрелка «Назад».
// Название раздела выделено крупным жирным шрифтом основного цвета темы.
// =====================================================================

// Что сейчас показывает панель. Когда оно меняется, старая панель плавно гаснет, новая появляется
private data class BarState(val title: String, val isHome: Boolean, val hasBack: Boolean)

@Composable
fun HotelTopBar(title: String, isHome: Boolean, onBack: (() -> Unit)?) {
    // Crossfade длится столько же, сколько смена страницы в HotelScreen (220 мс),
    // поэтому панель и страница меняются синхронно
    Crossfade(
        targetState = BarState(title, isHome, onBack != null),
        animationSpec = tween(durationMillis = 220)
    ) { bar ->
        if (bar.isHome) {
            TopAppBar(
                title = {
                    Row(
                        // Справа отступ, как слева: без него название прилипает к краю экрана
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HotelLogo()
                        SectionTitle(bar.title)
                    }
                }
            )
        } else {
            CenterAlignedTopAppBar(
                title = { SectionTitle(bar.title) },
                navigationIcon = {
                    if (bar.hasBack && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(HotelIcons.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    }
}

// Название раздела: крупный жирный текст без фона
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

// Логотип: знак «Q» и рядом слово «onaev» засечным шрифтом, похожим на букву в знаке.
// Вместе читается «Qonaev». Всё тёмно-синее, поэтому в тёмной теме кладём на белую плашку.
private val LogoBlue = Color(0xFF0C4273)

@Composable
private fun HotelLogo() {
    val content = @Composable {
        Row(verticalAlignment = Alignment.Bottom) {
            Image(
                painter = painterResource(R.drawable.logo_qonaev),
                contentDescription = "Qonaev",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(40.dp)
            )
            Text(
                text = "onaev",
                color = LogoBlue,
                maxLines = 1,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                // Сдвиг влево и вверх: слово встаёт вплотную к знаку, на уровне низа буквы Q
                modifier = Modifier.offset(x = (-3).dp, y = (-4).dp)
            )
        }
    }
    if (isSystemInDarkTheme()) {
        Surface(color = Color.White, shape = RoundedCornerShape(8.dp)) {
            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) { content() }
        }
    } else {
        content()
    }
}

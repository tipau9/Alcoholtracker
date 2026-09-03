package de.tipau.promille.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.AppSans
import de.tipau.promille.TabularFigures
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * 1:1 Port of iOS inline Time Wheel Picker (.datePickerStyle(.wheel)).
 * Shows two parallel columns for hours (00..23) and minutes (00..59)
 * with a dark selection overlay and smooth snap physics.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimeWheelPicker(
    selectedHour: Int,
    selectedMinute: Int,
    onTimeChanged: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 126.dp,
    itemHeight: Dp = 42.dp
) {
    val hours = remember { (0..23).toList() }
    val minutes = remember { (0..59).toList() }

    val hourState = rememberLazyListState(initialFirstVisibleItemIndex = selectedHour.coerceIn(0, 23))
    val minuteState = rememberLazyListState(initialFirstVisibleItemIndex = selectedMinute.coerceIn(0, 59))
    val hourSnap = rememberSnapFlingBehavior(lazyListState = hourState)
    val minuteSnap = rememberSnapFlingBehavior(lazyListState = minuteState)

    val coroutineScope = rememberCoroutineScope()

    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemHeightPx = remember(density, itemHeight) { with(density) { itemHeight.toPx() } }
    val haptics = rememberHapticManager()

    val activeHour by remember {
        derivedStateOf {
            val offset = hourState.firstVisibleItemScrollOffset
            val extra = if (itemHeightPx > 0) ((offset + itemHeightPx / 2) / itemHeightPx).toInt() else 0
            (hourState.firstVisibleItemIndex + extra).coerceIn(0, 23)
        }
    }

    val activeMinute by remember {
        derivedStateOf {
            val offset = minuteState.firstVisibleItemScrollOffset
            val extra = if (itemHeightPx > 0) ((offset + itemHeightPx / 2) / itemHeightPx).toInt() else 0
            (minuteState.firstVisibleItemIndex + extra).coerceIn(0, 59)
        }
    }

    LaunchedEffect(activeHour) {
        if (activeHour != selectedHour) {
            haptics.selection()
            onTimeChanged(activeHour, selectedMinute)
        }
    }

    LaunchedEffect(activeMinute) {
        if (activeMinute != selectedMinute) {
            haptics.selection()
            onTimeChanged(selectedHour, activeMinute)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Selection highlight bar in middle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.background.copy(alpha = 0.6f))
                .border(0.5.dp, AppColors.border, RoundedCornerShape(8.dp))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hours Wheel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height),
                contentAlignment = Alignment.Center
            ) {
                LazyColumn(
                    state = hourState,
                    flingBehavior = hourSnap,
                    contentPadding = PaddingValues(vertical = (height - itemHeight) / 2),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(hours.size) { index ->
                        val h = hours[index]
                        val isSelected = activeHour == index
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .clickable {
                                    coroutineScope.launch {
                                        hourState.animateScrollToItem(index)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format("%02d", h),
                                color = if (isSelected) AppColors.text else AppColors.textDim.copy(alpha = 0.35f),
                                fontSize = if (isSelected) 22.sp else 17.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = AppSans,
                                style = TabularFigures,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Colon separator
            Text(
                text = ":",
                color = AppColors.textDim,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Minutes Wheel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height),
                contentAlignment = Alignment.Center
            ) {
                LazyColumn(
                    state = minuteState,
                    flingBehavior = minuteSnap,
                    contentPadding = PaddingValues(vertical = (height - itemHeight) / 2),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(minutes.size) { index ->
                        val m = minutes[index]
                        val isSelected = activeMinute == index
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .clickable {
                                    coroutineScope.launch {
                                        minuteState.animateScrollToItem(index)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format("%02d", m),
                                color = if (isSelected) AppColors.text else AppColors.textDim.copy(alpha = 0.35f),
                                fontSize = if (isSelected) 22.sp else 17.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = AppSans,
                                style = TabularFigures,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

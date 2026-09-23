package dev.milinko.workoutapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.workoutapp.db.entitys.Exercise
import dev.milinko.workoutapp.ui.components.ExerciseHistoryRow
import dev.milinko.workoutapp.viewmodel.ExerciseViewModel
import java.text.SimpleDateFormat
import java.util.*



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: ExerciseViewModel = hiltViewModel()) {
    val filteredHistory by viewModel.filteredHistory.collectAsState()
    val currentExerciseFilter by viewModel.statsExerciseFilter.collectAsState()
    val currentDateFilter by viewModel.statsDateFilter.collectAsState()
    val distinctExerciseNames by viewModel.distinctExerciseNames.collectAsState()
    val totalsByExercise by viewModel.totalsByExercise.collectAsState()
    val exerciseFilterOptions = remember(distinctExerciseNames) { listOf("All") + distinctExerciseNames }

    // Reps only add up meaningfully when they're all the same exercise - with "All" selected the
    // history mixes push-ups, pull-ups, manual entries, etc., so a combined rep count/graph would
    // be meaningless. Both are shown only once a single exercise is picked from the dropdown.
    val isSingleExerciseSelected = currentExerciseFilter != "All"
    val totalReps = filteredHistory.sumOf { it.numOf }
    val totalEntries = filteredHistory.size

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Statistics") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = "Filters", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExerciseFilterDropdown(
                            selectedFilter = currentExerciseFilter,
                            options = exerciseFilterOptions,
                            onFilterSelected = { viewModel.setStatsExerciseFilter(it) },
                            modifier = Modifier.weight(1f)
                        )
                        DateFilterDropdown(
                            selectedFilter = currentDateFilter,
                            onFilterSelected = { viewModel.setStatsDateFilter(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                // Stat Summary - Total Reps only makes sense once one exercise is isolated
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isSingleExerciseSelected) {
                        StatCard(
                            title = "Total Reps",
                            value = totalReps.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    StatCard(
                        title = "Entries",
                        value = totalEntries.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Accumulated totals per exercise, across the FULL history (unaffected by filters above)
            if (totalsByExercise.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Ukupno po vežbi",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            totalsByExercise.forEach { (name, total) ->
                                StatCard(
                                    title = name,
                                    value = total.toString(),
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Graph - only meaningful for a single isolated exercise (see isSingleExerciseSelected above)
            if (isSingleExerciseSelected && filteredHistory.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "$currentExerciseFilter - Last 7 Active Days",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        WorkoutBarChart(
                            exercises = filteredHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        )
                    }
                }
            }

            item {
                // History Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "History",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$totalEntries entries (long-press to delete)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (filteredHistory.isEmpty()) {
                item {
                    Text(
                        text = "No entries for this filter yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(filteredHistory, key = { it.id }) { exercise ->
                ExerciseHistoryRow(
                    exercise = exercise,
                    onDelete = { viewModel.deleteExercise(it) }
                )
            }
        }
    }
}

@Composable
fun ExerciseFilterDropdown(
    selectedFilter: String,
    options: List<String>,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = selectedFilter, fontSize = 14.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.45f)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onFilterSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DateFilterDropdown(
    selectedFilter: ExerciseViewModel.DateFilter,
    onFilterSelected: (ExerciseViewModel.DateFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = selectedFilter.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 14.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.45f)
        ) {
            ExerciseViewModel.DateFilter.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onFilterSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun WorkoutBarChart(
    exercises: List<Exercise>,
    modifier: Modifier = Modifier
) {
    // Group by date and sum reps, then lay out a fixed window of the last 7 calendar
    // days (today back to 6 days ago) - including days with 0 reps - instead of just the
    // last 7 dates that happen to have any logged activity, so the "Last 7 Days" label
    // is actually accurate and gaps in training show up as empty bars, not skipped days.
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val displayFormat = SimpleDateFormat("dd.MM", Locale.getDefault())
    val repsByDateKey = exercises.groupBy { dateFormat.format(it.date) }
        .mapValues { entry -> entry.value.sumOf { it.numOf } }

    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val groupedData = (6 downTo 0).map { daysAgo ->
        val day = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        val key = dateFormat.format(day.time)
        displayFormat.format(day.time) to (repsByDateKey[key] ?: 0)
    }

    // coerceAtLeast(1f): with the fixed 7-day window groupedData is never empty, but every
    // day in it can legitimately be 0 reps (e.g. filtered history has no activity this week) -
    // guard against dividing by 0 below.
    val maxReps = (groupedData.maxOfOrNull { it.second } ?: 1).toFloat().coerceAtLeast(1f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 32.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = (width / (groupedData.size * 2))
        val spaceBetween = barWidth

        val textPaint = Paint().apply {
            color = onSurfaceColor.toArgb()
            textSize = 10.sp.toPx()
            textAlign = Paint.Align.CENTER
        }

        groupedData.forEachIndexed { index, data ->
            val barHeight = (data.second / maxReps) * height
            val x = index * (barWidth + spaceBetween) + (spaceBetween / 2)
            val y = height - barHeight

            // Draw Bar
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )

            // Draw Value label above bar
            drawContext.canvas.nativeCanvas.drawText(
                data.second.toString(),
                x + barWidth / 2,
                y - 8.dp.toPx(),
                textPaint
            )

            // Draw Date label below bar
            drawContext.canvas.nativeCanvas.drawText(
                data.first,
                x + barWidth / 2,
                height + 20.dp.toPx(),
                textPaint
            )
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 12.sp)
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

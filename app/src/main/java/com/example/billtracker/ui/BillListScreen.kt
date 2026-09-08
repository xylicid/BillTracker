@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.billtracker.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.billtracker.data.Biller
import com.example.billtracker.data.SortMode
import com.example.billtracker.ui.theme.AccentGreen
import com.example.billtracker.ui.theme.DangerRed
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillListScreen(viewModel: BillerViewModel = viewModel()) {
    val billers by viewModel.billers.collectAsState()
    val resetDay by viewModel.resetDay.collectAsState()
    val income by viewModel.income.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var editingBiller by remember { mutableStateOf<Biller?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val paidCount = billers.count { it.isPaid }
    val totalCount = billers.size
    val totalDue = billers.filter { !it.isPaid }.mapNotNull { it.amount }.sum()
    val remainingAfterBills = income - totalDue

    val monthLabel = remember {
        LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
            " " + LocalDate.now().year
    }

    // Local mirror of the list used while a drag is in progress, so the flow re-emitting
    // doesn't fight with the in-flight reorder. Synced back to the real list when idle.
    var localList by remember { mutableStateOf(billers) }
    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(
        lazyListState = listState,
        canDrag = { itemInfo ->
            sortMode == SortMode.MANUAL && localList.getOrNull(itemInfo.index)?.isPaid == false
        },
        onMove = { from, to ->
            localList = localList.toMutableList().apply { add(to, removeAt(from)) }
        }
    )
    val isDragging by remember { derivedStateOf { dragDropState.draggingItemIndex != null } }

    LaunchedEffect(billers, isDragging) {
        if (!isDragging) localList = billers
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(monthLabel, fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
                Text(
                    text = "$paidCount of $totalCount paid",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add biller")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            SummaryBar(totalDue = totalDue, income = income, remaining = remainingAfterBills)
        }
    ) { padding ->
        if (billers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No bills yet. Tap + to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .dragContainer(dragDropState) {
                        viewModel.reorder(localList.filter { !it.isPaid })
                    },
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(localList, key = { _, item -> item.id }) { index, biller ->
                    val isBeingDragged = dragDropState.draggingItemIndex == index

                    val wiggle = if (isBeingDragged) {
                        val infiniteTransition = rememberInfiniteTransition(label = "wiggle")
                        val angle by infiniteTransition.animateFloat(
                            initialValue = -1.5f,
                            targetValue = 1.5f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(150, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "wiggleAngle"
                        )
                        angle
                    } else {
                        0f
                    }

                    SwipeableBillRow(
                        biller = biller,
                        onToggle = { viewModel.togglePaid(biller) },
                        onEdit = { editingBiller = biller },
                        onDelete = {
                            viewModel.deleteBiller(biller)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Deleted ${biller.name}",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreBiller(biller)
                                }
                            }
                        },
                        modifier = Modifier
                            .let { base ->
                                if (isBeingDragged) base else base.animateItemPlacement(tween(300))
                            }
                            .graphicsLayer {
                                translationY = if (isBeingDragged) dragDropState.draggingItemOffset else 0f
                                rotationZ = wiggle
                                scaleX = if (isBeingDragged) 1.03f else 1f
                                scaleY = if (isBeingDragged) 1.03f else 1f
                                shadowElevation = if (isBeingDragged) 12f else 0f
                            }
                            .zIndex(if (isBeingDragged) 1f else 0f)
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        BillerFormDialog(
            title = "Add a bill",
            confirmLabel = "Add",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, amount, dueDate ->
                viewModel.addBiller(name, amount, dueDate)
                showAddDialog = false
            }
        )
    }

    editingBiller?.let { biller ->
        BillerFormDialog(
            title = "Edit bill",
            confirmLabel = "Save",
            initialName = biller.name,
            initialAmount = biller.amount,
            initialDueDate = biller.dueDate,
            onDismiss = { editingBiller = null },
            onConfirm = { name, amount, dueDate ->
                viewModel.updateBiller(biller, name, amount, dueDate)
                editingBiller = null
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            resetDay = resetDay,
            income = income,
            sortMode = sortMode,
            onDismiss = { showSettingsDialog = false },
            onSave = { newResetDay, newIncome, newSortMode ->
                viewModel.setResetDay(newResetDay)
                viewModel.setIncome(newIncome)
                viewModel.setSortMode(newSortMode)
                showSettingsDialog = false
            }
        )
    }
}

@Composable
private fun SummaryBar(totalDue: Double, income: Double, remaining: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryItem(label = "Income", value = income, modifier = Modifier.weight(1f))
            SummaryItem(label = "Due", value = totalDue, modifier = Modifier.weight(1f))
            SummaryItem(
                label = "Remaining",
                value = remaining,
                valueColor = if (remaining < 0) DangerRed else AccentGreen,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = formatMoney(value),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableBillRow(
    biller: Biller,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    // Don't actually dismiss the row for edit — just snap back.
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val isEdit = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
            val isDelete = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            val bgColor = when {
                isDelete -> MaterialTheme.colorScheme.error
                isEdit -> AccentGreen
                else -> MaterialTheme.colorScheme.background
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isEdit) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                when {
                    isDelete -> Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError
                    )
                    isEdit -> Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) {
        BillRow(biller = biller, onToggle = onToggle)
    }
}

@Composable
private fun BillRow(biller: Biller, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = biller.isPaid,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = AccentGreen)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = biller.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (biller.isPaid) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (biller.isPaid) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            biller.dueDate?.let { epochDay ->
                val dueDate = LocalDate.ofEpochDay(epochDay)
                val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dueDate)
                val status = when {
                    biller.isPaid -> "Paid • Due ${formatDate(dueDate)}"
                    daysUntil < 0 -> "Overdue • Due ${formatDate(dueDate)}"
                    daysUntil == 0L -> "Due today • ${formatDate(dueDate)}"
                    daysUntil == 1L -> "Due tomorrow • ${formatDate(dueDate)}"
                    daysUntil <= 7L -> "Due in $daysUntil days • ${formatDate(dueDate)}"
                    else -> "Due ${formatDate(dueDate)}"
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        !biller.isPaid && daysUntil < 0 -> DangerRed
                        !biller.isPaid && daysUntil <= 7 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        if (biller.amount != null) {
            Text(
                text = formatMoney(biller.amount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillerFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialAmount: Double? = null,
    initialDueDate: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double?, dueDate: Long?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var amountText by remember { mutableStateOf(initialAmount?.let { formatPlainNumber(it) } ?: "") }
    var dueDateText by remember { mutableStateOf(initialDueDate?.let { formatDate(LocalDate.ofEpochDay(it)) } ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }

    fun parseDate(text: String): LocalDate? {
        val digits = text.filter(Char::isDigit)
        if (digits.length != 6) return null
        val month = digits.substring(0, 2).toIntOrNull() ?: return null
        val day = digits.substring(2, 4).toIntOrNull() ?: return null
        val year = 2000 + (digits.substring(4, 6).toIntOrNull() ?: return null)
        return try { LocalDate.of(year, month, day) } catch (_: DateTimeParseException) { null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Amex, Water Bill") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (optional)") },
                    placeholder = { Text("e.g. 120.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { raw ->
                        val digits = raw.filter(Char::isDigit).take(6)
                        dueDateText = when {
                            digits.length <= 2 -> digits
                            digits.length <= 4 -> digits.substring(0, 2) + "/" + digits.substring(2)
                            else -> digits.substring(0, 2) + "/" + digits.substring(2, 4) + "/" + digits.substring(4)
                        }
                        dateError = false
                    },
                    label = { Text("Due date (MM/DD/YY)") },
                    placeholder = { Text("e.g. 09/15/26") },
                    singleLine = true,
                    isError = dateError,
                    supportingText = if (dateError) {{ Text("Enter a valid date") }} else null,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Choose due date")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                TextButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Open calendar") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedDate = if (dueDateText.isBlank()) null else parseDate(dueDateText)
                    if (dueDateText.isNotBlank() && parsedDate == null) {
                        dateError = true
                    } else {
                        onConfirm(name, amountText.toDoubleOrNull(), parsedDate?.toEpochDay())
                    }
                },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val initialMillis = initialDueDate?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        dueDateText = formatDate(selected)
                        dateError = false
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(
    resetDay: Int,
    income: Double,
    sortMode: SortMode,
    onDismiss: () -> Unit,
    onSave: (resetDay: Int, income: Double, sortMode: SortMode) -> Unit
) {
    var resetDayText by remember { mutableStateOf(resetDay.toString()) }
    var incomeText by remember { mutableStateOf(if (income == 0.0) "" else income.toString()) }
    var sortModeSelection by remember { mutableStateOf(sortMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = resetDayText,
                    onValueChange = { resetDayText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Reset day (1-28)") },
                    placeholder = { Text("e.g. 1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = incomeText,
                    onValueChange = { incomeText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monthly income") },
                    placeholder = { Text("e.g. 3500") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Sort bills by", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sortModeSelection == SortMode.MANUAL,
                        onClick = { sortModeSelection = SortMode.MANUAL }
                    )
                    Text(
                        "Manual (hold + drag)",
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sortModeSelection == SortMode.DUE_DATE,
                        onClick = { sortModeSelection = SortMode.DUE_DATE }
                    )
                    Text("Due date")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val day = (resetDayText.toIntOrNull() ?: 1).coerceIn(1, 28)
                val inc = incomeText.toDoubleOrNull() ?: 0.0
                onSave(day, inc, sortModeSelection)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatMoney(value: Double): String = String.format(Locale.US, "$%,.2f", value)

private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MM/dd/yy"))

private fun formatPlainNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun ordinal(day: Int): String {
    if (day in 11..13) return "${day}th"
    return when (day % 10) {
        1 -> "${day}st"
        2 -> "${day}nd"
        3 -> "${day}rd"
        else -> "${day}th"
    }
}

package com.combo.monkeycompanion.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v10.38.16: the shared Material 3 kit for every Set-Tai app screen (Studio, Diary, Settings, …), so they all
 * look and behave the same: one top bar with back, cards with a title, rows with a switch, a pick-one menu, notes.
 */

@Composable
internal fun SettaiTheme(activity: Activity, content: @Composable () -> Unit) {
    val dark = com.combo.monkeycompanion.theme.ThemeSkin.isDark(activity)
    MaterialTheme(colorScheme = SettaiDesignTokens.scheme(dark), content = content)
}

/**
 * v10.38.21: settings live in SharedPreferences (not Compose state). Each screen passes its change counter as
 * [SettaiScreen]'s `rev`; every [SCard] re-reads its content when it changes, so a switch / radio / choice
 * always shows the value you just picked (without this, Compose could skip the card and keep the old one).
 */
internal val LocalSettingsRev = androidx.compose.runtime.compositionLocalOf { 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettaiScreen(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    rev: Int = 0,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.runtime.CompositionLocalProvider(LocalSettingsRev provides rev) {
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("←", fontSize = 22.sp) } },
            actions = { actions() }
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(SettaiDesignTokens.sectionSpacingDp.dp),
            content = content
        )
    }
}

@Composable
internal fun SCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val rev = LocalSettingsRev.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(SettaiDesignTokens.cornerCardDp.dp)) {
        androidx.compose.runtime.key(rev) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) SNote(subtitle)
                content()
            }
        }
    }
}

@Composable
internal fun SNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SSubhead(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun SSwitch(label: String, checked: Boolean, help: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (help != null) SNote(help)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun SRadio(label: String, selected: Boolean, help: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (help != null) SNote(help)
        }
    }
}

/** Pick one from a list: shows the current choice; tap to open the menu. */
@Composable
internal fun SChoice(label: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(options.getOrElse(selected) { options.firstOrNull().orEmpty() }, modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(i) })
                }
            }
        }
    }
}

@Composable
internal fun SSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Text("$label: $value%", style = MaterialTheme.typography.bodyLarge)
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat())
    }
}

@Composable
internal fun SField(
    value: String, onChange: (String) -> Unit, label: String, placeholder: String? = null,
    secret: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, modifier = modifier,
        label = { Text(label) },
        placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri)
    )
}

@Composable
internal fun SPrimary(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

@Composable
internal fun SSecondary(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

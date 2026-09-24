package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "密码",
) {
    var visible by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { visible = false }
    LaunchedEffect(value.isEmpty()) { if (value.isEmpty()) visible = false }
    OutlinedTextField(
        value, onValueChange, modifier, enabled = enabled,
        label = { Text(label) }, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            Row {
                if (value.isNotEmpty()) IconAction(Glyph.Close, "清空$label", enabled) {
                    visible = false
                    onValueChange("")
                }
                IconAction(if (visible) Glyph.EyeSlash else Glyph.Eye, if (visible) "隐藏$label" else "显示$label", enabled) {
                    visible = !visible
                }
            }
        },
    )
}

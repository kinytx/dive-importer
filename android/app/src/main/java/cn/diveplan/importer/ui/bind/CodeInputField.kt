package cn.diveplan.importer.ui.bind

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 6 位 OTP-style 输入框 —— 6 个独立框，光标永远在最后一位之后。
 *
 * 实现技巧：底下是一个**透明 BasicTextField**（吃键盘焦点 + IME 数字键盘），
 * 上面铺 6 个可视化方框；方框只是 [Text] 展示对应位的数字。
 *
 * 这样省去手动管 focus / 自动跳转下一位 / 退格回退一位 等细节 —— 系统 TextField 全包了。
 *
 * @param value 当前 6 位值（已经归一化好）
 * @param onValueChange 输入变化回调（父层用 ViewModel.onCodeChange）
 * @param onSubmit 用户在 IME 上按 Done 时触发（父层等价于点击「确认绑定」）
 * @param enabled 提交中时设 false，禁止再编辑
 */
@Composable
fun CodeInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(modifier = modifier) {
        // 1) 上层：6 个可视化方框
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (i in 0 until 6) {
                val ch = value.getOrNull(i)?.toString() ?: ""
                val active = i == value.length && enabled
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(
                            color = if (active)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ch,
                        style = TextStyle(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.W700,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
        }

        // 2) 下层：透明 BasicTextField 吃键盘
        BasicTextField(
            value = TextFieldValue(value, selection = androidx.compose.ui.text.TextRange(value.length)),
            onValueChange = { tfv -> onValueChange(tfv.text) },
            modifier = Modifier
                .matchParentSize()
                .focusRequester(focusRequester),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent),
            singleLine = true,
        )
    }
}

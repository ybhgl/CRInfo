package com.crinfo.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LocalPreferenceInGroup = staticCompositionLocalOf { false }

/** 一组偏好项共用一张卡片；卡片自带标题（SmallTitle）与 12dp 水平边距。 */
@Composable
fun PreferenceGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        title?.let { SmallTitle(text = it) }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            insideMargin = PaddingValues(0.dp),
        ) {
            CompositionLocalProvider(LocalPreferenceInGroup provides true) { content() }
        }
    }
}

/** 组内时直接铺行，组外时自包一张卡。 */
@Composable
fun PreferenceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (LocalPreferenceInGroup.current) Column(modifier.fillMaxWidth(), content = content)
    else PreferenceGroup(modifier = modifier, content = content)
}

@Composable
fun BasicListItem(
    headlineText: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingImageVector: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val leading: (@Composable () -> Unit)? = leadingImageVector?.let { icon ->
        {
            Icon(
                icon,
                null,
                modifier = Modifier.padding(end = 16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
    PreferenceCard(modifier) {
        if (onClick != null && trailingContent == null) {
            ArrowPreference(
                title = headlineText,
                summary = supportingText,
                enabled = enabled,
                holdDownState = holdDownState,
                onClick = onClick,
                startAction = leading,
            )
        } else {
            top.yukonga.miuix.kmp.basic.BasicComponent(
                title = headlineText,
                summary = supportingText,
                enabled = enabled,
                holdDownState = holdDownState,
                onClick = onClick,
                startAction = leading,
                endActions = trailingContent?.let { trailing -> { trailing() } },
            )
        }
    }
}

@Composable
fun SwitchListItem(
    checked: Boolean,
    headlineText: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingImageVector: ImageVector? = null,
    enabled: Boolean = true,
) {
    PreferenceCard(modifier) {
        SwitchPreference(
            checked = checked,
            onCheckedChange = onCheckedChange,
            title = headlineText,
            summary = supportingText,
            enabled = enabled,
            startAction = leadingImageVector?.let { icon ->
                {
                    Icon(
                        icon,
                        null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            },
        )
    }
}

/**
 * 表单里的轻量开关行：一句标签 + 一枚 Switch。
 *
 * 查询首页这类**不是设置页**的地方放开关用它，别去套 [SwitchListItem]（那是带卡片与内边距的偏好行）。
 * 「模糊查询」「联网查询」两处共用这一份形态，避免同一个应用里长出两种仿制开关。
 */
@Composable
fun InlineSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

data class SelectionItem<T>(val label: String, val value: T)

/**
 * 下拉选择偏好行。
 *
 * [itemIcon] 非空时改走 Miuix 的 `DropdownEntry` 重载：每个选项文字前挂一个自定义图标
 * （主题颜色的圆形色指示器就挂在这里）；不传时保持普通 `List<String>` 路径。
 */
@Composable
fun <T> DropdownListItem(
    headlineText: String,
    value: T?,
    selections: List<SelectionItem<T>>,
    onValueChanged: (index: Int, value: T) -> Unit,
    modifier: Modifier = Modifier,
    leadingImageVector: ImageVector? = null,
    enabled: Boolean = true,
    itemIcon: (@Composable (Modifier, T) -> Unit)? = null,
) {
    val startAction: (@Composable () -> Unit)? = leadingImageVector?.let { icon ->
        {
            Icon(
                icon,
                null,
                modifier = Modifier.padding(end = 16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
    PreferenceCard(modifier) {
        val iconFor = itemIcon
        if (iconFor == null) {
            WindowDropdownPreference(
                title = headlineText,
                items = selections.map { it.label },
                selectedIndex = selections.indexOfFirst { it.value == value },
                enabled = enabled,
                startAction = startAction,
                onSelectedIndexChange = { index ->
                    selections.getOrNull(index)?.let { onValueChanged(index, it.value) }
                },
            )
        } else {
            // DropdownEntry 路径没有 selectedIndex：选中态与点击都落在每个 DropdownItem 上
            WindowDropdownPreference(
                title = headlineText,
                entries = listOf(
                    DropdownEntry(
                        items = selections.mapIndexed { index, item ->
                            DropdownItem(
                                text = item.label,
                                enabled = enabled,
                                selected = item.value == value,
                                onClick = { onValueChanged(index, item.value) },
                                icon = { iconModifier -> iconFor(iconModifier, item.value) },
                            )
                        },
                    ),
                ),
                enabled = enabled,
                startAction = startAction,
            )
        }
    }
}

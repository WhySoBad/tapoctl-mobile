package ch.wsb.tapoctl.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import ch.wsb.tapoctl.ui.theme.BoldText

@Composable
fun CardWithTitle(
    title: String,
    titleSuffix: String? = null,
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = 10.dp,
                    end = 16.dp,
                    bottom = 16.dp
                )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = BoldText,
                    )
                    if (titleSuffix != null) {
                        Text(
                            text = titleSuffix,
                            style = BoldText,
                        )
                    }
                }

                Column {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToggleButtonRow(
    value: Boolean,
    activeLabel: String,
    inactiveLabel: String,
    onChange: (value: Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                Button(
                    modifier = Modifier.weight(1F),
                    onClick = { onChange(false) },
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 0.dp,
                        bottomStart = 12.dp,
                        bottomEnd = 0.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        contentColor = if (!value) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                    ),
                ) {
                    Text(
                        text = inactiveLabel,
                        style = BoldText
                    )
                }
                Button(
                    modifier = Modifier.weight(1F),
                    onClick = { onChange(true) },
                    shape = RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 12.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 12.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        contentColor = if (value) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text(
                        text = activeLabel,
                        style = BoldText
                    )
                }
            }
        }
    }
}

package com.dream.tv.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dream.tv.ui.theme.LocalGlassColors

/**
 * 底部玻璃胶囊 Tab 栏（拾音式）——悬浮玻璃条 + 选中强调色光点。
 */
data class TabItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun GlassTabBar(
    tabs: List<TabItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        GlassSurface(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 14.dp, start = 24.dp, end = 24.dp)
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    val isSel = tab.id == selected
                    val tint by animateColorAsState(
                        if (isSel) MaterialTheme.colorScheme.primary else LocalGlassColors.current.textSecondary,
                        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                        label = "tabTint",
                    )
                    val scale by animateFloatAsState(
                        if (isSel) 1f else 0.94f,
                        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                        label = "tabScale",
                    )
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clickable(interactionSource = interaction, indication = null) { onSelect(tab.id) }
                            .scale(scale),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSel) {
                                // 选中光晕（强调色柔光底）
                                Box(
                                    Modifier
                                        .size(40.dp, 28.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                                )
                                            ),
                                            CircleShape
                                        )
                                )
                            }
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                            color = tint,
                        )
                    }
                }
            }
        }
    }
}

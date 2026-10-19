package dev.sumanth.goodbuy.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.sumanth.goodbuy.model.Product
import dev.sumanth.goodbuy.model.TrackedGroup

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackedGroupCard(
    group: TrackedGroup,
    groupedProducts: List<Product>,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: (TrackedGroup) -> Unit = {},
    onLongClick: (TrackedGroup) -> Unit = {},
    onUngroupClick: (TrackedGroup) -> Unit = {}
) {
    val bestProduct = groupedProducts.minByOrNull { it.newPrice }
    val primaryProduct = groupedProducts.firstOrNull()
    val leastPrice = bestProduct?.newPrice ?: 0.0
    val bestStore = bestProduct?.displayStore ?: ""
    val latestTrackedTime = groupedProducts.maxOfOrNull { it.lastTrackedTime } ?: 0L

    val storesIncluded = groupedProducts.map { it.displayStore }.distinct().joinToString(" & ")

    val borderColor = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.primary.copy(alpha = 0.5f)

    Card(
        shape = MaterialTheme.shapes.medium,
        backgroundColor = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.12f)
            else MaterialTheme.colors.surface,
        elevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.5.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { onClick(group) },
                onLongClick = { onLongClick(group) }
            )
    ) {
        Column {
            // Top Image Box with Group Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
            ) {
                if (primaryProduct != null) {
                    AsyncImage(
                        model = primaryProduct.imageUrl,
                        contentDescription = group.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }

                // Group Badge Overlay (Top Left)
                Surface(
                    color = MaterialTheme.colors.primary,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 4.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${groupedProducts.size} Grouped",
                            color = MaterialTheme.colors.onPrimary,
                            style = MaterialTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // Included Stores Badge (Bottom Left)
                Surface(
                    color = MaterialTheme.colors.secondary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(topEnd = 4.dp, bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        text = storesIncluded,
                        color = MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Selection Checkbox or Ungroup Button (Top Right)
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .background(
                                color = MaterialTheme.colors.surface.copy(alpha = 0.9f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not Selected",
                            tint = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { onUngroupClick(group) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(32.dp)
                            .background(
                                color = MaterialTheme.colors.surface.copy(alpha = 0.85f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallSplit,
                            contentDescription = "Ungroup items",
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Group Details & Price Highlight Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = if (group.name.isNotBlank()) group.name else (primaryProduct?.title ?: "Grouped Products"),
                    style = MaterialTheme.typography.subtitle2.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colors.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Prominent Least Current Price Box
                Surface(
                    color = MaterialTheme.colors.secondary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalOffer,
                                    contentDescription = "Best Deal",
                                    tint = MaterialTheme.colors.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LEAST CURRENT PRICE",
                                    style = MaterialTheme.typography.caption.copy(
                                        fontSize = 9.sp,
                                        letterSpacing = 0.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colors.primary
                                )
                            }

                            if (latestTrackedTime > 0L) {
                                Text(
                                    text = formatRelativeTime(latestTrackedTime),
                                    style = MaterialTheme.typography.caption.copy(
                                        fontSize = 9.sp
                                    ),
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatPrice(leastPrice, bestProduct?.link ?: ""),
                                style = MaterialTheme.typography.subtitle1.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = MaterialTheme.colors.onSurface
                            )

                            if (bestStore.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colors.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Best on $bestStore",
                                        style = MaterialTheme.typography.caption.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colors.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

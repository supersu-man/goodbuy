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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.sumanth.goodbuy.model.Product

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackedProductCard(
    product: Product,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: (Product) -> Unit = {},
    onLongClick: (Product) -> Unit = {},
    onRemoveClick: (Product) -> Unit = {}
) {
    val borderColor = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.12f)

    Card(
        shape = MaterialTheme.shapes.medium,
        backgroundColor = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.12f)
            else MaterialTheme.colors.surface,
        elevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { onClick(product) },
                onLongClick = { onLongClick(product) }
            )
    ) {
        Column {
            // Image Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
            ) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )

                // Store Badge (Bottom Left)
                Surface(
                    color = MaterialTheme.colors.secondary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(topEnd = 4.dp, bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        text = product.displayStore,
                        color = MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Selection Checkbox Overlay or Remove Button (Top Right)
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
                        onClick = { onRemoveClick(product) },
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
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove from tracking",
                            tint = MaterialTheme.colors.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.subtitle2.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colors.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = formatPrice(product.newPrice, product.link),
                            style = MaterialTheme.typography.subtitle1.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colors.primary
                        )

                        if (product.oldPrice > product.newPrice) {
                            Text(
                                text = formatPrice(product.oldPrice, product.link),
                                style = MaterialTheme.typography.caption.copy(
                                    textDecoration = TextDecoration.LineThrough
                                ),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colors.secondary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = MaterialTheme.colors.onSurface,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = formatRelativeTime(product.lastTrackedTime),
                                style = MaterialTheme.typography.caption.copy(fontSize = 10.sp),
                                color = MaterialTheme.colors.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

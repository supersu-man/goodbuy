package dev.sumanth.goodbuy.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
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
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ProductCard(
    product: Product,
    modifier: Modifier = Modifier,
    isTracked: Boolean = false,
    onProductClick: (Product) -> Unit = {},
    onTrackClick: (Product) -> Unit = {},
) {
    Card(
        onClick = { onProductClick(product) },
        shape = MaterialTheme.shapes.medium,
        elevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Image with Overlays (Discount Badge & Track Status Badge)
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

                // Discount Badge (Top Left)
                if (product.discountPercentage > 0) {
                    Surface(
                        color = MaterialTheme.colors.error,
                        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 4.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "-${product.discountPercentage}%",
                            color = MaterialTheme.colors.onError,
                            style = MaterialTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

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

                // Track Toggle Button (Top Right)
                Surface(
                    onClick = { onTrackClick(product) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(34.dp),
                    shape = CircleShape,
                    color = if (isTracked) MaterialTheme.colors.primary else MaterialTheme.colors.surface.copy(alpha = 0.9f),
                    contentColor = if (isTracked) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isTracked) Icons.Default.BookmarkAdded else Icons.Default.AddAlert,
                            contentDescription = if (isTracked) "Tracked" else "Add to Tracking",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Product Details Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Category Tag
                Text(
                    text = product.category.uppercase(Locale.US),
                    style = MaterialTheme.typography.caption.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Product Title
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

                Spacer(modifier = Modifier.height(4.dp))

                // Rating & Review Count
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = product.rating.toString(),
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onSurface
                    )
                    Text(
                        text = " (${product.reviewCount})",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Price Section and Track Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        // New Price
                        Text(
                            text = formatPrice(product.newPrice, product.link),
                            style = MaterialTheme.typography.subtitle1.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colors.primary
                        )

                        // Old Price (Strikethrough)
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

                    // Bottom Track Button
                    Surface(
                        onClick = { onTrackClick(product) },
                        shape = MaterialTheme.shapes.small,
                        color = if (isTracked) MaterialTheme.colors.primary.copy(alpha = 0.12f) else MaterialTheme.colors.secondary.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isTracked) Icons.Default.BookmarkAdded else Icons.Default.AddAlert,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isTracked) MaterialTheme.colors.onSurface else MaterialTheme.colors.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isTracked) "Tracked" else "Track",
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isTracked) MaterialTheme.colors.onSurface else MaterialTheme.colors.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatPrice(price: Double, link: String = ""): String {
    val isINR = link.contains("flipkart", ignoreCase = true) || price >= 50.0 || (price % 1.0 == 0.0)
    val symbol = if (isINR) "₹" else "$"
    return if (price % 1.0 == 0.0) {
        "$symbol${String.format(Locale.US, "%,.0f", price)}"
    } else {
        "$symbol${String.format(Locale.US, "%,.2f", price)}"
    }
}

fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Just now"
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 0) return "Just now"

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}


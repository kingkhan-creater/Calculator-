package com.example.feature.media.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.feature.media.VaultMediaItem
import com.example.feature.media.VaultMediaType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnailItem(
    item: VaultMediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerRadius = 12.dp
    val borderModifier = if (isSelected) {
        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(cornerRadius))
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius))
            .then(borderModifier)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("media_item_${item.id}")
    ) {
        // Thumbnail Image
        val imageModel = item.thumbnailFile ?: item.file
        AsyncImage(
            model = imageModel,
            contentDescription = item.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video Badge with Duration
        if (item.mediaType == VaultMediaType.VIDEO) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        if (item.durationMs > 0) {
                            Text(
                                text = formatDuration(item.durationMs),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Selection Indicator / Checkbox
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.45f)
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

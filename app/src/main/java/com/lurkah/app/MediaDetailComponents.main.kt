@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.lurkah.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PostDetailBottomSheet(
    initialIndex: Int,
    viewModel: MainViewModel,
    autoReplay: Boolean,
    onDismiss: () -> Unit,
    onDoubleClick: (Int) -> Unit
) {
    val safeInitialPage = initialIndex.coerceIn(0, (viewModel.posts.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { viewModel.posts.size })
    val autoPlayVideos by viewModel.autoPlayVideos.collectAsState()

    LaunchedEffect(pagerState.currentPage) {
        val currentPost = viewModel.posts.getOrNull(pagerState.currentPage)
        currentPost?.let { post ->
            viewModel.loadCommentsForPost(post.id)
            viewModel.loadFullAlbumDetails(post.id, pagerState.currentPage)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight(0.9f)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) { page ->
            val post = viewModel.posts.getOrNull(page) ?: return@HorizontalPager
            val isCurrentPage = pagerState.currentPage == page

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "${post.typeLabel} • File size: ${post.formattedSize}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var tagToBlock by remember { mutableStateOf<String?>(null) }

                    if (post.tags.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(post.tags.take(5)) { tag ->
                                SuggestionChip(
                                    onClick = { tagToBlock = tag },
                                    label = { Text("#$tag") }
                                )
                            }
                        }
                    }

                    tagToBlock?.let { tag ->
                        AlertDialog(
                            onDismissRequest = { tagToBlock = null },
                            title = { Text("Tag blockieren?") },
                            text = { Text("Möchtest du den Tag '#$tag' blockieren? Beiträge mit diesem Tag werden nicht mehr angezeigt.") },
                            confirmButton = {
                                Button(onClick = {
                                    viewModel.addBlacklistTag(tag)
                                    tagToBlock = null
                                }) { Text("Blockieren") }
                            },
                            dismissButton = {
                                TextButton(onClick = { tagToBlock = null }) { Text("Abbrechen") }
                            }
                        )
                    }

                    // Fester Container, der verhindert, dass die LazyColumn das Layout beim Laden kollabieren lässt
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val imgurImages = post.images

                        if (!imgurImages.isNullOrEmpty()) {
                            imgurImages.forEachIndexed { imgIndex, img ->
                                val itemUrl = img.mp4 ?: img.link
                                val isItemVideo = (img.type ?: "").startsWith("video/") || itemUrl?.endsWith(".mp4") == true

                                if (isItemVideo && itemUrl != null) {
                                    VideoPlayer(
                                        videoUrl = itemUrl,
                                        isMuted = false,
                                        autoReplay = autoReplay,
                                        autoPlayVideos = autoPlayVideos && isCurrentPage,
                                        showControls = true,
                                        onDoubleClick = { onDoubleClick(page) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 250.dp, max = 350.dp)
                                    )
                                } else if (itemUrl != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(itemUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "${post.title} - ${imgIndex + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 250.dp, max = 350.dp)
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = { onDoubleClick(page) }
                                                )
                                            }
                                    )
                                }
                            }
                        } else if (post.isVideo && post.mediaUrl != null) {
                            VideoPlayer(
                                videoUrl = post.mediaUrl!!,
                                isMuted = false,
                                autoReplay = autoReplay,
                                autoPlayVideos = autoPlayVideos && isCurrentPage,
                                showControls = true,
                                onDoubleClick = { onDoubleClick(page) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 250.dp, max = 350.dp)
                            )
                        } else if (post.mediaUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(post.mediaUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = post.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 250.dp, max = 350.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { onDoubleClick(page) }
                                        )
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Comments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }

                if (viewModel.isLoadingComments) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (viewModel.selectedPostComments.isEmpty()) {
                    item {
                        Text(
                            text = "No comments available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                items(
                    items = viewModel.selectedPostComments,
                    key = { comment -> comment.id }
                ) { comment ->
                    CommentItem(
                        comment = comment,
                        depth = 0,
                        onImageReferenceClick = { targetIndex ->
                            val currentPost = viewModel.posts.getOrNull(pagerState.currentPage)
                            val maxImages = currentPost?.images?.size ?: 1
                            val safeIndex = targetIndex.coerceIn(0, maxImages - 1)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenMediaViewer(
    initialIndex: Int,
    posts: List<ImgurPost>,
    autoReplay: Boolean,
    onDismiss: (Int) -> Unit // Übergibt den aktuellen Index beim Schließen
) {
    val safeInitialPage = initialIndex.coerceIn(0, (posts.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { posts.size })

    val viewModel: MainViewModel = viewModel()
    val autoPlayVideos by viewModel.autoPlayVideos.collectAsState()

    Dialog(
        onDismissRequest = { onDismiss(pagerState.currentPage) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val post = posts.getOrNull(page) ?: return@HorizontalPager
                val isCurrentPage = pagerState.currentPage == page
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val imgurImages = post.images

                        if (!imgurImages.isNullOrEmpty()) {
                            // Im Vollbild für Alben das erste oder aktive Bild/Video anzeigen
                            val firstImg = imgurImages.firstOrNull()
                            val itemUrl = firstImg?.mp4 ?: firstImg?.link ?: post.mediaUrl
                            val isItemVideo = (firstImg?.type ?: "").startsWith("video/") || itemUrl?.endsWith(".mp4") == true

                            if (isItemVideo && itemUrl != null) {
                                VideoPlayer(
                                    videoUrl = itemUrl,
                                    isMuted = false,
                                    autoReplay = autoReplay,
                                    autoPlayVideos = autoPlayVideos && isCurrentPage,
                                    showControls = true,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (itemUrl != null) {
                                AsyncImage(
                                    model = itemUrl,
                                    contentDescription = post.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    if (scale > 1f) {
                                                        scale = 1f
                                                        offsetX = 0f
                                                        offsetY = 0f
                                                    } else {
                                                        scale = 2.5f
                                                    }
                                                }
                                            )
                                        }
                                )
                            }
                        } else if (post.isVideo && post.mediaUrl != null) {
                            VideoPlayer(
                                videoUrl = post.mediaUrl!!,
                                isMuted = false,
                                autoReplay = autoReplay,
                                autoPlayVideos = autoPlayVideos && isCurrentPage,
                                showControls = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (post.mediaUrl != null) {
                            AsyncImage(
                                model = post.mediaUrl,
                                contentDescription = post.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (scale > 1f) {
                                                    scale = 1f
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                } else {
                                                    scale = 2.5f
                                                }
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = { onDismiss(pagerState.currentPage) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(50))
            ) {
                Text(text = "✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: ImgurComment,
    depth: Int = 0,
    onImageReferenceClick: (Int) -> Unit // Neuer Callback für den Bildindex
) {
    val maxDepth = 4
    val currentIndent = depth.coerceAtMost(maxDepth)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        if (currentIndent > 0) {
            repeat(currentIndent) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .align(Alignment.CenterStart)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (depth > 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "@${comment.author}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                val score = comment.ups - comment.downs
                Text(
                    text = "▲ $score",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Kommentartext parsen und Bild-Referenzen klickbar machen
            CommentTextWithImageLinks(commentText = comment.comment, onImageReferenceClick = onImageReferenceClick)

            comment.children?.forEach { childComment ->
                Spacer(modifier = Modifier.height(4.dp))
                CommentItem(comment = childComment, depth = depth + 1, onImageReferenceClick = onImageReferenceClick)
            }
        }
    }
}

@Composable
fun CommentTextWithImageLinks(
    commentText: String,
    onImageReferenceClick: (Int) -> Unit
) {
    // Regex, der nach # gefolgt von Zahlen sucht (z.B. #1, #10)
    val regex = Regex("#(\\d+)")
    val matches = regex.findAll(commentText).toList()

    if (matches.isEmpty()) {
        Text(text = commentText, style = MaterialTheme.typography.bodyMedium)
        return
    }

    // Wir teilen den Text auf und bauen eine Zeile/FlowRow mit klickbaren Elementen
    // Für eine einfache Umsetzung nutzen wir Annotations oder splitten den Text textuell.
    // Ein sehr robuster Weg in Compose ist die Nutzung von Text mit ClickableText oder einer Kombination.

    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        var lastIndex = 0
        matches.forEach { match ->
            val range = match.range
            append(commentText.substring(lastIndex, range.first))

            val imageNumberStr = match.groupValues[1]
            pushStringAnnotation(tag = "IMAGE_REF", annotation = imageNumberStr)
            withStyle(
                style = androidx.compose.ui.text.SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(match.value)
            }
            pop()
            lastIndex = range.last + 1
        }
        if (lastIndex < commentText.length) {
            append(commentText.substring(lastIndex))
        }
    }

    androidx.compose.foundation.text.ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "IMAGE_REF", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    val pageNumber = annotation.item.toIntOrNull()
                    if (pageNumber != null && pageNumber > 0) {
                        // Imgur zählt meist von 1 aufwärts, Arrays in Kotlin von 0 -> pageNumber - 1
                        onImageReferenceClick(pageNumber - 1)
                    }
                }
        }
    )
}
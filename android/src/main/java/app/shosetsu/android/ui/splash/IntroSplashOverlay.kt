package app.shosetsu.android.ui.splash

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.shosetsu.android.R
import kotlin.math.floor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private const val ICON_PHASE_ONE_MS = 980
private const val ICON_PHASE_TWO_MS = 220
private const val ICON_PHASE_THREE_MS = 180
private const val WORDMARK_DELAY_AFTER_ICON_MS = 200L
private const val WORDMARK_REVEAL_MS = 1320
private const val SPLASH_HOLD_MS = 1800L
private const val OVERLAY_FADE_OUT_MS = 420
private const val ANIMATION_START_FALLBACK_MS = 180L
private const val FORCE_FINISH_TIMEOUT_MS = 5000L
private val SplashBackgroundColor = Color(0xFF081328)

@Composable
fun MonogatariIntroSplash(onFinished: () -> Unit) {
	val engine = remember { ParticleEngine(maxParticles = 960) }
	val density = LocalDensity.current
	val revealBandWidthPx = with(density) { 22.dp.toPx() }
	val iconScale = remember { Animatable(0f) }
	val iconRotation = remember { Animatable(0f) }
	val overlayAlpha = remember { Animatable(1f) }
	val wordmarkProgress = remember { Animatable(0f) }
	val wordmarkAlpha = remember { Animatable(0f) }

	var particleTick by remember { mutableStateOf(0) }
	var animationStarted by remember { mutableStateOf(false) }
	var revealStarted by remember { mutableStateOf(false) }
	var wordmarkHeight by remember { mutableStateOf(0f) }
	var wordmarkWidth by remember { mutableStateOf(0f) }
	var finishDispatched by remember { mutableStateOf(false) }

	fun finishOverlay(reason: String) {
		if (finishDispatched) return
		finishDispatched = true
		Log.d("Splash", "IntroSplashOverlay onFinished dispatched ($reason)")
		onFinished()
	}

	DisposableEffect(Unit) {
		Log.d("Splash", "IntroSplashOverlay visible")
		onDispose {
			Log.d("Splash", "IntroSplashOverlay hidden")
		}
	}

	LaunchedEffect(Unit) {
		coroutineScope {
			launch {
				withFrameNanos { }
				delay(16)
				animationStarted = true
				Log.d("Splash", "IntroSplashOverlay animationStarted=true")
			}

			launch {
				delay(ANIMATION_START_FALLBACK_MS)
				if (!animationStarted) {
					animationStarted = true
					Log.d("Splash", "IntroSplashOverlay animationStarted forced by fallback")
				}
			}

			launch {
				delay(FORCE_FINISH_TIMEOUT_MS)
				if (!finishDispatched) {
					Log.d("Splash", "IntroSplashOverlay finish forced by timeout")
					finishOverlay("timeout")
				}
			}

			launch {
				var lastNanos = 0L
				var spawnAccumulator = 0f
				var lastProgress = 0f
				while (isActive) {
					withFrameNanos { frameTime ->
						if (lastNanos == 0L) lastNanos = frameTime
						val dt = ((frameTime - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
						lastNanos = frameTime

						if (revealStarted && wordmarkHeight > 0f && wordmarkWidth > 0f) {
							val progress = wordmarkProgress.value.coerceIn(0f, 1f)
							val progressSpeed = ((progress - lastProgress) / dt.coerceAtLeast(0.001f)).coerceAtLeast(0f)
							val bandFraction = (revealBandWidthPx / wordmarkWidth).coerceIn(0.015f, 0.08f)
							if (progress in 0.01f..0.998f) {
								val dynamicSpawn = 10f + (progressSpeed * 30f)
								spawnAccumulator += dt * dynamicSpawn.coerceIn(10f, 30f) * 60f
								val emitCount = spawnAccumulator.toInt().coerceIn(0, 30)
								if (emitCount > 0) {
									engine.emitDustBand(progress, wordmarkHeight, bandFraction, emitCount)
									spawnAccumulator -= emitCount
								}
								if (dt > 0.016f && progress in 0.08f..0.95f) {
									engine.emitDustBand(progress, wordmarkHeight, bandFraction, 8)
								}
							}
							lastProgress = progress
						}

						engine.step(dt)
						particleTick++
					}
				}
			}

			launch {
				iconScale.snapTo(0f)
				iconRotation.snapTo(0f)
				wordmarkProgress.snapTo(0f)
				wordmarkAlpha.snapTo(0f)
				overlayAlpha.snapTo(1f)

				while (!animationStarted) yield()

				coroutineScope {
					launch { iconScale.animateTo(0.7f, tween(ICON_PHASE_ONE_MS, easing = FastOutSlowInEasing)) }
					launch {
						iconRotation.animateTo(420f, tween(ICON_PHASE_ONE_MS, easing = FastOutSlowInEasing))
						iconRotation.animateTo(350f, tween(ICON_PHASE_TWO_MS, easing = FastOutSlowInEasing))
						iconRotation.animateTo(360f, tween(ICON_PHASE_THREE_MS, easing = FastOutSlowInEasing))
					}
				}

				delay(WORDMARK_DELAY_AFTER_ICON_MS)
				revealStarted = true
				coroutineScope {
					launch { wordmarkProgress.animateTo(1f, tween(WORDMARK_REVEAL_MS, easing = LinearEasing)) }
					launch { wordmarkAlpha.animateTo(1f, tween(WORDMARK_REVEAL_MS, easing = FastOutSlowInEasing)) }
				}

				// Keep the fully revealed end frame visible for a short moment before leaving splash.
				delay(SPLASH_HOLD_MS)
				overlayAlpha.animateTo(0f, tween(OVERLAY_FADE_OUT_MS, easing = FastOutSlowInEasing))
				finishOverlay("animation_complete")
			}
		}
	}

	BoxWithConstraints(
		modifier = Modifier
			.fillMaxSize()
			.background(SplashBackgroundColor)
			.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
			.graphicsLayer(alpha = overlayAlpha.value),
	) {
		if (!animationStarted) return@BoxWithConstraints

		val iconSize = (maxWidth * 0.58f).coerceIn(220.dp, 360.dp)
		val wordmarkDisplayWidth = (maxWidth * 0.9f).coerceIn(260.dp, 560.dp)
		val gap = (maxHeight * 0.03f).coerceIn(20.dp, 40.dp)

		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 28.dp, vertical = 36.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
		) {
			Box(contentAlignment = Alignment.Center, modifier = Modifier.width(iconSize).aspectRatio(1f).padding(12.dp)) {
				Image(
					painter = painterResource(R.drawable.monogatari_icon),
					contentDescription = null,
					contentScale = ContentScale.Fit,
					modifier = Modifier
						.fillMaxSize()
						.graphicsLayer(
							rotationZ = iconRotation.value,
							scaleX = iconScale.value,
							scaleY = iconScale.value,
						),
				)
			}

			Spacer(modifier = Modifier.height(gap))

			DustRevealWordmark(
				progress = if (revealStarted) wordmarkProgress.value else 0f,
				wordmarkAlpha = if (revealStarted) wordmarkAlpha.value else 0f,
				engine = engine,
				tick = particleTick,
				onSizeChanged = { width, height ->
					wordmarkWidth = width
					wordmarkHeight = height
				},
				revealBandWidthPx = revealBandWidthPx,
				modifier = Modifier.width(wordmarkDisplayWidth),
			)
		}
	}
}

@Composable
private fun DustRevealWordmark(
	progress: Float,
	wordmarkAlpha: Float,
	engine: ParticleEngine,
	tick: Int,
	onSizeChanged: (Float, Float) -> Unit,
	revealBandWidthPx: Float,
	modifier: Modifier = Modifier,
) {
	val bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.monogatari_wordmark)
	val ratio = remember(bitmap) {
		if (bitmap.width > 0 && bitmap.height > 0) bitmap.width.toFloat() / bitmap.height.toFloat() else 5.4f
	}

	Canvas(
		modifier = modifier
			.fillMaxWidth()
			.aspectRatio(ratio)
			.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
	) {
		tick
		onSizeChanged(size.width, size.height)
		val clampedProgress = progress.coerceIn(0f, 1f)
		val revealX = size.width * clampedProgress
		val alpha = wordmarkAlpha.coerceIn(0f, 1f)

		clipRect(right = revealX) {
			drawImage(bitmap, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()), alpha = alpha)
		}

		drawContext.canvas.withSaveLayer(Rect(0f, 0f, size.width, size.height), Paint()) {
			drawEdgeDustCloud(
				revealX = revealX,
				bandWidth = revealBandWidthPx.coerceIn(8f, size.width * 0.1f),
				height = size.height,
				progress = clampedProgress,
			)
			engine.drawParticles { xFraction, y, radius, particleAlpha, color ->
				val x = xFraction * size.width
				drawCircle(color = color.copy(alpha = particleAlpha * 0.34f), radius = radius * 2.9f, center = Offset(x, y))
				drawCircle(color = color.copy(alpha = particleAlpha), radius = radius, center = Offset(x, y))
			}
			drawImage(
				bitmap,
				dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
				alpha = alpha,
				blendMode = BlendMode.DstIn,
			)
		}
	}
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEdgeDustCloud(
	revealX: Float,
	bandWidth: Float,
	height: Float,
	progress: Float,
) {
	val seed = floor(progress.coerceIn(0f, 1f) * 90f).toInt()
	val sampleCount = (90 + (progress * 110f).toInt()).coerceIn(80, 200)
	repeat(sampleCount) { i ->
		val xRnd = stableNoise(seed, i, 31)
		val yRnd = stableNoise(seed, i, 73)
		val rRnd = stableNoise(seed, i, 113)
		val aRnd = stableNoise(seed, i, 157)
		val hueRnd = stableNoise(seed, i, 199)

		val x = revealX + ((xRnd - 0.5f) * bandWidth * 2f)
		val y = (0.13f + (yRnd * 0.76f)) * height
		val radius = 0.9f + (rRnd * 3.6f)
		val alpha = 0.08f + (aRnd * 0.32f)
		val color = when ((hueRnd * 3f).toInt().coerceIn(0, 2)) {
			0 -> Color(0xFFD4AF37)
			1 -> Color(0xFFFFD27D)
			else -> Color(0xFFFFB84D)
		}

		drawCircle(color = color.copy(alpha = alpha * 0.65f), radius = radius * 1.6f, center = Offset(x, y))
		drawCircle(color = color.copy(alpha = alpha), radius = radius, center = Offset(x, y))
	}
}

private fun stableNoise(seed: Int, index: Int, salt: Int): Float {
	var value = (seed * 374_761_393) xor (index * 668_265_263) xor (salt * 362_437)
	value = (value xor (value ushr 13)) * 1_274_126_177
	value = value xor (value ushr 16)
	return (value and 0x7fffffff) / Int.MAX_VALUE.toFloat()
}

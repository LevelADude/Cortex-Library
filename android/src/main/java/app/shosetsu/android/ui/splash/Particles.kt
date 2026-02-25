package app.shosetsu.android.ui.splash

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

private class MutableSplashParticle {
	var x: Float = 0f
	var y: Float = 0f
	var velocityX: Float = 0f
	var velocityY: Float = 0f
	var radius: Float = 0f
	var alpha: Float = 0f
	var lifeSeconds: Float = 0f
	var ageSeconds: Float = 0f
	var color: Color = Color.Unspecified
	var active: Boolean = false
}

internal class ParticleEngine(
	private val random: Random = Random.Default,
	maxParticles: Int = 960,
) {
	private val particles = Array(maxParticles) { MutableSplashParticle() }

	fun drawParticles(draw: (xFraction: Float, y: Float, radius: Float, alpha: Float, color: Color) -> Unit) {
		for (particle in particles) {
			if (!particle.active) continue
			draw(particle.x, particle.y, particle.radius, particle.alpha, particle.color)
		}
	}

	fun emitDustBand(
		revealProgress: Float,
		wordmarkHeight: Float,
		bandFraction: Float,
		amount: Int,
	) {
		if (wordmarkHeight <= 0f || amount <= 0) return
		val spread = bandFraction.coerceIn(0.015f, 0.08f)
		repeat(amount) {
			val particle = allocateParticle() ?: return@repeat
			particle.x = (revealProgress + random.nextFloat(-spread, spread)).coerceIn(0f, 1f)
			particle.y = random.nextFloat(wordmarkHeight * 0.16f, wordmarkHeight * 0.86f)
			particle.velocityX = random.nextFloat(-0.11f, 0.12f)
			particle.velocityY = random.nextFloat(-62f, -18f)
			particle.radius = random.nextFloat(2f, 9f)
			particle.lifeSeconds = random.nextFloat(0.60f, 1.20f)
			particle.ageSeconds = 0f
			particle.alpha = random.nextFloat(0.55f, 1f)
			particle.color = when (random.nextInt(3)) {
				0 -> Color(0xFFD4AF37)
				1 -> Color(0xFFFFD27D)
				else -> Color(0xFFFFB84D)
			}
			particle.active = true
		}
	}

	fun step(deltaSeconds: Float) {
		if (deltaSeconds <= 0f) return
		for (particle in particles) {
			if (!particle.active) continue
			particle.ageSeconds += deltaSeconds
			if (particle.ageSeconds >= particle.lifeSeconds) {
				particle.active = false
				continue
			}

			particle.x += particle.velocityX * deltaSeconds
			particle.y += particle.velocityY * deltaSeconds
			particle.velocityX *= 0.985f
			particle.velocityY = (particle.velocityY * 0.98f) + (9f * deltaSeconds)
			val lifeProgress = particle.ageSeconds / particle.lifeSeconds
			particle.radius *= 0.995f
			particle.alpha = (1f - (lifeProgress * lifeProgress)) * 0.95f
		}
	}

	private fun allocateParticle(): MutableSplashParticle? {
		for (particle in particles) {
			if (!particle.active) return particle
		}
		return null
	}
}

private fun Random.nextFloat(min: Float, max: Float): Float = min + (nextFloat() * (max - min))

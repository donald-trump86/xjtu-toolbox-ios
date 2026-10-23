package com.xjtu.toolbox.home

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import com.xjtu.toolbox.ui.components.LiquidPalettes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeHeroAppearanceTest {
    private val automatic = LiquidMood(LiquidPalettes.Midnight, seed = 4f, speed = 1.2f, crease = 0.8f)

    @Test
    fun defaultsPreserveAdaptiveAppearance() {
        assertEquals(automatic, HeroAppearance().applyTo(automatic))
    }

    @Test
    fun motionAndShapeCanChangeWithoutChangingColor() {
        val result = HeroAppearance(motion = "still", seed = 234).applyTo(automatic)
        assertFalse(result.animated)
        assertEquals(automatic.colors, result.colors)
        assertEquals(2.34f, result.seed)
        assertTrue(HeroAppearance(motion = "gentle").applyTo(automatic).speed < automatic.speed)
    }

    @Test
    fun evenWhiteCustomColorRemainsReadableBehindWhiteText() {
        val result = HeroAppearance(palette = "custom", colorHex = "#FFFFFF").applyTo(automatic)
        assertTrue(result.colors.dark.luminance() <= 0.06f)
        assertTrue(result.colors.mid.luminance() <= 0.2f)
        assertTrue(result.colors.light.luminance() <= 0.36f)
        assertEquals(automatic.speed, result.speed)
        assertEquals(automatic.seed, result.seed)
    }

    @Test
    fun solidColorIsPreservedAndTextAdaptsToLightBackgrounds() {
        val light = HeroAppearance(background = "solid", colorHex = "#FFFFFF")
        assertEquals(Color.White, light.solidColor)
        assertTrue(light.contentColor.luminance() < 0.1f)
        assertEquals(Color.White, light.copy(colorHex = "#000000").contentColor)
    }
}

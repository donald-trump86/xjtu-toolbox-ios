package com.xjtu.toolbox.agent.bot.cast

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 喵：一颗橘色虎斑猫头。粉耳朵、额头三道虎斑、白嘴套、粉鼻头、「ω」嘴，黄绿色竖瞳猫眼。
 * 待命偶尔抖耳朵、瞳孔跟着视线。动作都是整颗头在动：
 * - 微动：歪头慢眨眼（猫式「我爱你」）；
 * - 思考：毛线球绕着头转，整颗头跟着球歪来歪去、瞳孔放大成圆的，球转到前面时伸出肉垫爪子拍它；
 * - 提醒：耳朵唰地竖高、整颗头往上一弹；
 * - 被戳：「猫猫震惊」表情包：蹦起来、浑身炸毛成一圈尖刺、瞪圆眼、瞳孔缩成一点、小嘴「o」；
 * - 彩蛋：「猫推杯子」：眯眼盯着旁边的杯子，拨两下，一爪推下去，再眯成 ^^ 一脸无辜地看回来。
 */
object Cat : CastCharacter("cat", "喵") {
    override val microSec = 1.6
    override val pokeSec = 1.0
    override val comboSec = 3.0
    override val leavesCanvas = true

    private const val FUR = 0xFFF2A65A
    private const val STRIPE = 0xFFD97F2E
    private const val EAR_IN = 0xFFFFB3B8
    private const val MUZZLE = 0xFFFFF3E3
    private const val NOSE = 0xFFFF8A95
    private const val IRIS = 0xFFB9DA4E
    private const val MOUTH = 0xFF6B3324
    private const val YARN = 0xFFFF7FA8
    private const val BEAN = 0xFFFFA3AE
    private const val CUP = 0xFF7FB8E6

    private val twitchL = Beats(0xca71, 4.0, 9.0, 2.0)
    private val twitchR = Beats(0xca72, 5.0, 10.0, 4.5)

    override fun draw(s: Sketch, p: CastPose) {
        val now = p.now
        val think = p.amt(Act.THINK); val tt = p.t(Act.THINK)
        val alert = p.amt(Act.ALERT); val ta = p.t(Act.ALERT)
        val micro = p.amt(Act.MICRO); val tm = p.t(Act.MICRO)
        val poke = p.amt(Act.POKE); val tp = p.t(Act.POKE)
        val combo = p.amt(Act.COMBO); val tc = p.t(Act.COMBO)

        // 震惊：蹦起来、炸毛，定住，再慢慢松下来
        val shock = poke * window(tp, 0.0, 0.06, 0.6, 0.85)
        val poof = poke * window(tp, 0.02, 0.1, 0.55, 0.85)
        val hop = poke * 12 * bump(tp / 0.32)
        // 推杯子：头往左让出位置 → 盯杯子 → 拨两下 → 推下去 → ^^ 看回来
        val room = combo * window(tc, 0.0, 0.3, 2.5, 2.9)
        val stare = combo * window(tc, 0.2, 0.5, 1.9, 2.2)
        val tap = combo * (bump((tc - 0.75) / 0.3) + bump((tc - 1.2) / 0.3))
        val shove = combo * bump((tc - 1.6) / 0.45)
        val fall = combo * ramp(tc, 1.75, 2.35)
        val smug = combo * window(tc, 2.15, 2.35, 2.8, 3.0)
        // 提醒：往上一弹，耳朵竖高
        val spring = alert * wobble(ta, 15.0, 4.5)
        val perk = maxOf(alert, shock)
        val tilt = micro * bump(tm / 1.5)

        // 毛线球绕头转；转到前面时伸爪拍它
        val ang = tt * 2.4
        val ballX = cos(ang) * 94
        val ballY = 24 + 34 * sin(ang)
        val swipe = think * (if (sin(ang) > 0.55) bump((sin(ang) - 0.55) / 0.45) else 0.0)
        val lookX = mix(mix(p.lookX, cos(ang), think), 1.0, stare)
        val lookY = mix(mix(p.lookY, 0.5 * sin(ang), think), 0.6, stare)

        val rx = 100.0
        val ry = 76.0 * (1 + 0.012 * p.breath)
        val cy = 24.0

        if (think > 0.01 && sin(ang) < 0) yarn(s, ballX, ballY, ang, think)

        s.group {
            // 以下巴为支点
            translate(think * cos(ang) * 8 - 22 * room, cy + ry - hop)
            scale((1 - 0.12 * room) * (1 + 0.05 * shock - 0.06 * spring), (1 - 0.12 * room) * (1 + 0.04 * shock + 0.1 * spring))
            rotate(p.lookX * 4 * (1 - shock) + think * 11 * cos(ang) + 14 * tilt + 8 * stare)
            translate(0.0, -ry)

            val twL = 16 * wobble(twitchL.since(now), 30.0, 8.0)
            val twR = 16 * wobble(twitchR.since(now), 30.0, 8.0)
            ear(s, -1, rx, ry, twL, perk)
            ear(s, 1, rx, ry, twR, perk)
            // 炸毛：轮廓变成一圈尖刺
            s.blob(0.0, 0.0, 112) { th ->
                val saw = 1 - abs(((th / Sketch.TAU * 14) % 1.0) * 2 - 1)
                superR(th, rx, ry, 2.2) * (1 + 0.14 * poof * saw - 0.03 * poof)
            }
            s.fill(FUR)

            // 额头三道虎斑
            for (k in -1..1) {
                val x = k * 20.0
                s.line(x, -ry + 6, x * 0.8, -ry + (if (k == 0) 30.0 else 22.0))
                s.stroke(8.0, STRIPE)
            }

            // 白嘴套、粉鼻头、ω 嘴
            val my = ry * 0.42
            for (side in intArrayOf(-1, 1)) {
                s.ellipse(side * 17.0, my, 25.0, 19.0)
                s.fill(MUZZLE)
            }
            s.moveTo(-8.0, my - 14)
            s.lineTo(8.0, my - 14)
            s.lineTo(0.0, my - 5)
            s.close()
            s.fill(NOSE)
            s.stroke(5.0, NOSE)
            if (shock > 0.3) {
                s.ellipse(0.0, my + 10, 6.0, 9.0 * shock)
                s.fill(MOUTH)
            } else {
                s.moveTo(-15.0, my + 1)
                s.quadTo(-7.5, my + 10, 0.0, my - 1)
                s.quadTo(7.5, my + 10, 15.0, my + 1)
                s.stroke(4.5, MOUTH)
            }

            val ex = lookX * 4 * (1 - shock)
            val ey = -ry * 0.12
            when {
                smug > 0.3 -> s.joy(ex, ey, 1.0, 36.0, MOUTH)
                else -> {
                    // 慢眨眼：慢慢闭上、停一下、慢慢睁开
                    val slow = micro * window(tm, 0.0, 0.5, 0.9, 1.4)
                    // 瞳孔：平时竖条，兴奋（思考盯球、提醒、盯杯子）放大成圆，震惊缩成一点；盯杯子时坏笑着眯眼
                    val dilate = maxOf(think, alert, stare) * (1 - shock)
                    for (i in 0..1) {
                        val side = if (i == 0) -1 else 1
                        catEye(s, ex + side * 36.0, ey, p.eye[i] * (1 - slow) * (1 - 0.45 * stare), lookX, lookY, dilate, shock)
                    }
                }
            }
        }

        if (think > 0.01 && sin(ang) >= 0) yarn(s, ballX, ballY, ang, think)
        if (combo > 0.01 && fall < 1.0) {
            cup(s, 82 + 6 * tap + 70 * fall, 78 + 200 * fall * fall, 10 * tap + 160 * fall, ramp(tc, 0.0, 0.3))
        }
        // 肉垫爪子：思考时从下面伸上来拍球，彩蛋里伸过去拨杯子
        val reach = maxOf(tap.coerceAtMost(1.0) * 0.75, shove)
        when {
            swipe > 0.02 -> paw(
                s,
                mix(cos(ang).coerceIn(-0.6, 0.6) * 60, ballX * 0.9, swipe),
                mix(100.0, ballY + 16, swipe),
            )
            reach > 0.02 -> paw(s, mix(28.0, 52.0 + 20 * shove, reach), mix(128.0, 82.0, reach))
        }
        s.badge(alert, ta)
    }

    private fun paw(s: Sketch, x: Double, y: Double) {
        s.circle(x, y, 19.0)
        s.fill(FUR)
        s.ellipse(x, y + 3, 8.0, 6.5)
        s.fill(BEAN)
        for (k in -1..1) s.dot(x + k * 9.0, y - 9 - abs(k) * -2.0, 3.6, Tone.COLOR, 1.0, BEAN)
    }

    /** 马克杯：梯形杯身 + 把手 + 一道咖啡。绕杯底中心转，落下时整只翻出画面。 */
    private fun cup(s: Sketch, x: Double, y: Double, deg: Double, alpha: Double) {
        s.group {
            translate(x, y)
            rotate(deg)
            s.moveTo(16.0, -30.0)
            s.quadTo(36.0, -26.0, 34.0, -14.0)
            s.quadTo(32.0, -4.0, 14.0, -6.0)
            s.stroke(7.0, CUP, alpha)
            s.poly(-22.0, -44.0, 22.0, -44.0, 18.0, 0.0, -18.0, 0.0)
            s.fill(CUP, alpha)
            s.line(-18.0, -38.0, 18.0, -38.0)
            s.stroke(6.0, MOUTH, alpha)
        }
    }

    /** 猫眼：深色眼眶 + 黄绿虹膜 + 竖瞳 + 高光。[open] 太小就画成闭眼弧线。 */
    private fun catEye(s: Sketch, x: Double, y: Double, open: Double, lx: Double, ly: Double, dilate: Double, shock: Double) {
        val r = 16.0 * (1 + 0.3 * shock)
        if (open < 0.22) {
            s.moveTo(x - r, y)
            s.quadTo(x, y + r * 0.7, x + r, y)
            s.stroke(6.0, MOUTH)
            return
        }
        val ry = (r + 2) * open
        s.ellipse(x, y, r + 3.5, ry + 3.5)
        s.fill(EYE_DARK)
        s.ellipse(x, y, r, ry)
        s.fill(if (shock > 0.3) WHITE else IRIS)
        val px = x + lx * r * 0.3 * (1 - shock)
        val py = y + ly * ry * 0.2 * (1 - shock)
        val pw = mix(mix(4.5, 11.0, dilate), 4.0, shock)
        val ph = mix(mix(r * 0.85, r * 0.8, dilate), 4.0, shock) * open
        s.ellipse(px, py, pw, ph)
        s.fill(EYE_DARK)
        s.dot(px + pw * 0.4 + 2, py - ph * 0.45, 3.6, Tone.COLOR, 1.0, WHITE)
    }

    /** 耳朵：底边贴在头顶，内侧一块粉。[twitch] 抖动角度，[perk] 竖高。 */
    private fun ear(s: Sketch, side: Int, rx: Double, ry: Double, twitch: Double, perk: Double) {
        s.group {
            translate(side * rx * 0.54, -ry * 0.7)
            rotate(side * 16.0 + twitch * side)
            val h = 48 + 16 * perk
            s.poly(-30.0, 22.0, -4.0 * side, -h, 30.0, 22.0)
            s.fill(FUR)
            s.stroke(14.0, FUR)
            s.poly(-15.0, 16.0, -3.0 * side, -h * 0.6, 15.0, 16.0)
            s.fill(EAR_IN)
            s.stroke(6.0, EAR_IN)
        }
    }

    private fun yarn(s: Sketch, x: Double, y: Double, ang: Double, amount: Double) {
        // 球在头侧（sin≈0）换前后层，那里还压在脸的轮廓里：缩到没有再换，不然会凭空闪一下
        val r = (13.0 + 3 * sin(ang)) * min(1.0, abs(sin(ang)) / 0.3)
        s.circle(x, y, r * amount)
        s.fill(YARN)
    }
}

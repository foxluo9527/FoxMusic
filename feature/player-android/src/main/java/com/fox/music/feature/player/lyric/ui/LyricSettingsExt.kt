package com.fox.music.feature.player.lyric.ui

import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import com.fox.music.feature.player.databinding.LayoutLyricSettingsBinding
import com.fox.music.feature.player.lyric.data.LyricPalette
import com.fox.music.feature.player.lyric.data.LyricPalettes
import com.fox.music.feature.player.lyric.manager.LyricStyleManager

/**
 *    Author : 罗福林
 *    Date   : 2026/1/7
 *    Desc   :
 */
object LyricSettingsExt {
    /**
     * 悬浮窗里的 5 个色卡直接复用应用内歌词设置的前五套配色，
     * 点击写的是同一个 paletteId —— 悬浮窗和设置页从此是一套颜色。
     * 色卡顺序与 [LyricPalettes.all] 前五项一一对应：
     * style_classic = 纸上陶土、style_neon = 墨夜琥珀、style_summer = 黑胶奶咖、
     * style_fresh = 雾蓝清晨、style_dark = 雨后苔原。
     */
    fun LayoutLyricSettingsBinding.setListener(styleManager: LyricStyleManager){
        val binding = this
        val palettes = LyricPalettes.all.take(5)
        listOf(
            binding.styleClassic,
            binding.styleNeon,
            binding.styleSummer,
            binding.styleFresh,
            binding.styleDark,
        ).forEachIndexed { index, card ->
            val palette = palettes[index]
            // 颜色与设置页同源，避免 XML 里再抄一份色值后两边漂移。
            card.applySwatch(palette)
            card.setOnClickListener {
                styleManager.setPaletteId(palette.id)
            }
        }
        binding.textSizeSub.setOnClickListener {
            styleManager.setFontSize(styleManager.currentStyle.fontSize - 1)
        }
        binding.textSizeAdd.setOnClickListener {
            styleManager.setFontSize(styleManager.currentStyle.fontSize + 1)
        }
    }

    /** 色卡上半个圆用普通行色，下半个圆用高亮行色。 */
    private fun CardView.applySwatch(palette: LyricPalette) {
        val row = getChildAt(0) as? LinearLayout ?: return
        row.getChildAt(0)?.setBackgroundColor(palette.normalColor.toInt())
        row.getChildAt(1)?.setBackgroundColor(palette.highlightColor.toInt())
    }
}
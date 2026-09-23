package com.fox.music.feature.player.lyric.ui

import com.fox.music.feature.player.databinding.LayoutLyricSettingsBinding
import com.fox.music.feature.player.lyric.manager.LyricStyleManager

/**
 *    Author : 罗福林
 *    Date   : 2026/1/7
 *    Desc   :
 */
object LyricSettingsExt {
    fun LayoutLyricSettingsBinding.setListener(styleManager: LyricStyleManager){
        val binding = this
        binding.styleClassic.setOnClickListener {
            styleManager.updateStyle(LyricStyleManager.LyricPreset.CLASSIC.style,true)
        }
        binding.styleNeon.setOnClickListener {
            styleManager.updateStyle(LyricStyleManager.LyricPreset.NEON.style,true)
        }
        binding.styleSummer.setOnClickListener {
            styleManager.updateStyle(LyricStyleManager.LyricPreset.WARM.style,true)
        }
        binding.styleFresh.setOnClickListener {
            styleManager.updateStyle(LyricStyleManager.LyricPreset.COOL.style,true)
        }
        binding.styleDark.setOnClickListener {
            styleManager.updateStyle(LyricStyleManager.LyricPreset.DARK.style,true)
        }
        binding.textSizeSub.setOnClickListener {
            styleManager.setFontSize(styleManager.currentStyle.fontSize - 1)
        }
        binding.textSizeAdd.setOnClickListener {
            styleManager.setFontSize(styleManager.currentStyle.fontSize + 1)
        }
    }
}
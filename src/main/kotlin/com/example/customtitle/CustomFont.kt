package com.example.customtitle

import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier

/**
 * 自定义 TTF 字体接口 (MC 26.2 style-based font resolution)
 *
 * 本版本中 Font 对象不持有字体 id —— 字形由每个文本 Style 携带的 FontDescription
 * 动态解析 (FontManager 按 style.font 查找 FontSet)。因此做法是:
 *
 *   1. mod 资源提供字体定义 assets/customtitle/font/custom.json
 *      (ttf 优先, reference minecraft:default 兜底: TTF 未覆盖的字符回原版,
 *       TTF 文件缺失时整个字体集退化为原版, 不会出方块)
 *   2. 想让哪段文本用自定义字体, 就让它的 Style 指向 customtitle:custom
 *      本 mod 的做法: 两个 Screen 的 asPlainText() 统一加样式, 全部文本生效
 *
 * 更换字体: 用任意 .ttf 同名替换 assets/customtitle/font/custom.ttf 重新打包即可。
 */
object CustomFont {

    /** 对应 assets/customtitle/font/custom.json 定义的字体集 */
    val FONT_ID: Identifier = Identifier.fromNamespaceAndPath("customtitle", "custom")

    private val DESC: FontDescription = FontDescription.Resource(FONT_ID)

    /** 携带自定义字体样式的文本组件 */
    fun styled(s: String): Component =
        Component.literal(s).withStyle { it.withFont(DESC) }

    /** 按自定义字体实际度量测量文本宽度 (用于居中对齐) */
    fun width(font: Font, s: String): Int = font.width(styled(s))
}

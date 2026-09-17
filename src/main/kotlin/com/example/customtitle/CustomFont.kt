package com.example.customtitle

import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier

/**
 * 自定义 TTF 字体接口 (MC 26.2 style-based font resolution) - 全局注入版本
 *
 * 本版本中 Font 对象不持有字体 id —— 字形由每个文本 Style 携带的 FontDescription
 * 动态解析 (FontManager 按 style.font 查找 FontSet)。
 *
 * 全局注入原理 (已对照 26.2 反编译源码 FontManager.prepare() verify 过):
 * FontManager 用 ResourceManager.listMatchingResourceStacks() 按同一路径收集全部
 * 已激活资源包在该路径下的文件, 同一字体 id (如 minecraft:default) 的 providers
 * 会跨资源包 (含 mod 自带资源) 累加合并, 而不是"高优先级整份覆盖低优先级"。
 *
 * 所以只要把字体定义放在 assets/minecraft/font/default.json (而不是自己的命名空间
 * 下另起一个字体 id), 这份 TTF provider 就会被合并进 minecraft:default 本身的
 * provider 列表里、排在原版内建 providers 之前 —— 任何没有显式指定字体的文本
 * (游戏里绝大多数文本) 都会自动读取到这份新增的 TTF, TTF 未覆盖的字符仍会落回
 * 原版位图字体 (原版自己那份 default.json 依然在同一个合并列表里, 不是被替换掉)。
 *
 * 注意: 这份 provider 列表里不能再写 `{"type":"reference","id":"minecraft:default"}`
 * 兜底了 —— 现在这个文件本身就是 minecraft:default 的一部分, 那样写等于自我引用。
 *
 * 因为已经是全局生效, 本 mod 自己两个 Screen 的 asPlainText() 调用 [styled] 已经
 * 不是必需的 (不加也会用上这份字体), 但保留它们无害 —— 依然显式指向
 * minecraft:default, 即使某段文本已被其他代码显式指定了别的字体, 用 [styled]
 * 包一层也能把它拉回这份 (已增强的) 默认字体。
 *
 * 更换字体: 用任意 .ttf 同名替换 assets/customtitle/font/custom.ttf 重新打包即可
 * (ttf 文件本身仍放在 mod 自己的命名空间下, 只有 json 定义搬到了 minecraft 命名空间)。
 */
object CustomFont {

    /** 全局注入目标 —— 原版的默认字体 id, 不再是本 mod 自己的独立字体 id */
    val FONT_ID: Identifier = Identifier.fromNamespaceAndPath("minecraft", "default")

    private val DESC: FontDescription = FontDescription.Resource(FONT_ID)

    /** 携带自定义字体样式的文本组件 (全局注入后其实可省略, 保留作为显式兜底) */
    fun styled(s: String): Component =
        Component.literal(s).withStyle { it.withFont(DESC) }

    /** 按自定义字体实际度量测量文本宽度 (用于居中对齐) */
    fun width(font: Font, s: String): Int = font.width(styled(s))
}


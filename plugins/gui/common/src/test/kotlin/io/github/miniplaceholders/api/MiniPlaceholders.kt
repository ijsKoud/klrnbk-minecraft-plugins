package io.github.miniplaceholders.api

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

/** Test stand-in for MiniPlaceholders' real API class (same package and static method as the real one). */
object MiniPlaceholders {
    @JvmStatic
    fun audienceGlobalPlaceholders(): TagResolver = TagResolver.resolver("vip_prefix") { _, _ -> Tag.selfClosingInserting(Component.text("[VIP]")) }
}

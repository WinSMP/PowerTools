package org.winlogon.powertools

import org.bukkit.ChatColor

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object ChatFormatting {
    private val miniMessage = MiniMessage.miniMessage()
    private val tagsResolver: TagResolver = TagResolver
        .builder()
        .resolver(StandardTags.color())
        .resolver(StandardTags.reset())
        .resolver(StandardTags.decorations())
        .resolver(StandardTags.gradient())
        .resolver(StandardTags.rainbow())
        .resolver(StandardTags.clickEvent())
        .resolver(StandardTags.hoverEvent())
        .resolver(StandardTags.transition())
        .resolver(StandardTags.font())
        .build()

    /** Convert a legacy formatted string into a MiniMessage Component */
    def apply(msg: String): Component = {
        val legacyTranslated = ChatColor.translateAlternateColorCodes('&', msg)
        val legacyComponent =
            LegacyComponentSerializer.legacySection().deserialize(legacyTranslated)
        val miniMessageString = miniMessage
            .serialize(legacyComponent)
            .replaceAll("\\\\>", ">")
            .replaceAll("\\\\<", "<")
        MiniMessage
            .builder()
            .tags(tagsResolver)
            .build()
            .deserialize(miniMessageString, tagsResolver)
    }
}

package org.winlogon.powertools

import org.winlogon.retrohue.RetroHue

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags

object ChatFormatting {
    private val miniMessage = MiniMessage.miniMessage()
    private val colorConverter = RetroHue(miniMessage)
    private val tagsResolver: TagResolver = TagResolver
        .builder()
        .resolver(StandardTags.defaults())
        .build()

    /** Convert a legacy formatted string into a MiniMessage Component */
    def apply(msg: String): Component = {
        colorConverter.convertToComponent(msg, '&')
    }
}

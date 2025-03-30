package org.winlogon.powertools

import org.bukkit.ChatColor

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object ChatFormatting {
  private val miniMessage = MiniMessage.miniMessage()

  // TODO: add more tags
  private val tagsResolver = TagResolver.builder()
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

  /**
    * Format a text message, converting legacy colors to MiniMessage
    * allowing you to use MiniMessage.
    *
    * @param msg The formatted message
    */
  def apply(msg: String): Component = {
    val s = ChatColor.translateAlternateColorCodes('&', msg)
    val miniMessageString = miniMessage.serialize(
      LegacyComponentSerializer.legacySection().deserialize(s)
    )
    val escapedString = miniMessageString
      .replaceAll("\\\\>", ">")
      .replaceAll("\\\\<", "<")
    val mm = MiniMessage.builder().tags(tagsResolver).build()
    mm.deserialize(escapedString, tagsResolver)
  }
}

package org.winlogon.powertools

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.winlogon.retrohue.RetroHue

object ChatFormatting {
    private val colorPalette = TagResolver.builder()
        .resolver(Placeholder.styling("error", TextColor.color(0xF93822)))
        .build()

    val colorConverter = RetroHue()

    fun sendError(target: CommandSender, err: String) {
        val errorAsMiniMessage = colorConverter.convertToMiniMessage(err.sentenceCase(), '&')

        val formattedMessage =
            colorConverter.miniMessage.deserialize(
                "<error>Error</error><gray>: <error_msg></gray>",
                colorPalette,
                Placeholder.parsed("error_msg", errorAsMiniMessage)
            )

        target.sendMessage(formattedMessage)
    }

    /**
     * Converts a legacy/MiniMessage string into a Component.
     *
     * [placeholder] values are inserted verbatim, so player input (a pet name, say) is never
     * read as MiniMessage tags.
     */
    fun String.toComponent(placeholder: TagResolver = TagResolver.empty()): Component {
        val miniMessage = colorConverter.miniMessage
        return miniMessage.deserialize(colorConverter.convertToMiniMessage(this, '&'), placeholder)
    }

    fun String.sentenceCase(appendPeriod: Boolean = true): String {
        val trimmed = trim()
        // there is nothing to capitalise, and a period on its own would read as a whole message
        if (trimmed.isEmpty()) return trimmed

        val sentence = trimmed[0].uppercaseChar() + trimmed.substring(1)
        return if (appendPeriod && !sentence.endsWith(".")) "$sentence." else sentence
    }

    /**
     * Sends a player a message that has both legacy color codes and MiniMessage.
     *
     * Converts legacy codes to MiniMessage.
     */
    fun Player.sendLegacyMessage(message: String) {
        this.sendMessage(colorConverter.convertToComponent(message, '&'))
    }

    fun Player.sendError(message: String) {
        sendError(this, message)
    }
}

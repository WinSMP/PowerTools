package org.winlogon.powertools

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.winlogon.retrohue.RetroHue

object ChatFormatting {
    private val miniMessage = MiniMessage.miniMessage()
    val colorConverter = RetroHue(miniMessage)

    fun sendError(target: CommandSender, err: String) {
        // TODO: move <#F93822> to this styling placeholder
        val formattedMessage =
                colorConverter.convertToComponent("<#F93822>Error&7: ${err.sentenceCase()}", '&')

        target.sendMessage(formattedMessage)
    }

    fun String.sentenceCase(appendPeriod: Boolean = true): String {
        return this.trim()
                .let { if (it.isEmpty()) it else it[0].uppercaseChar() + it.substring(1) }
                .let { if (appendPeriod && !it.endsWith(".")) "$it." else it }
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

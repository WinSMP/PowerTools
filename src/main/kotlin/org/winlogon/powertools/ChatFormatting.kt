package org.winlogon.powertools

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.winlogon.retrohue.RetroHue
import org.bukkit.command.CommandSender

object ChatFormatting {
    private val miniMessage = MiniMessage.miniMessage()
    val colorConverter = RetroHue(miniMessage)

    fun sendError(target: CommandSender, err: String) {
        val formattedMessage = colorConverter.convertToComponent(
            "<#F93822>Error&7: ${sentenceCase(err)}", '&'
        )
        target.sendMessage(formattedMessage)
    }

    private fun sentenceCase(input: String): String {
        return input.trim().let {
            if (it.isEmpty()) it
            else it[0].uppercaseChar() + it.substring(1)
        }.let {
            if (it.endsWith(".")) it else "$it."
        }
    }
}

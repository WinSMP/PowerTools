package org.winlogon.powertools

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.winlogon.retrohue.RetroHue
import org.bukkit.command.CommandSender

object ChatFormatting {
    private val miniMessage = MiniMessage.miniMessage()
    private val colorConverter = RetroHue(miniMessage)

    fun translateLegacyCodes(msg: String): Component {
        return colorConverter.convertToComponent(msg, '&')
    }

    fun sendError(target: CommandSender, err: String) {
        target.sendMessage(translateLegacyCodes("<#F93822>Error&7: ${sentenceCase(err)}"))
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

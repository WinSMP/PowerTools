package org.winlogon.powertools.commands

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.{Command, CommandExecutor, CommandSender}
import org.bukkit.entity.Player

class BroadcastCommand(audiences: BukkitAudiences) extends CommandExecutor {
  private val miniMessage = MiniMessage.miniMessage()
  private val broadcastPrefix = "<red>[Broadcast]</red> "

  override def onCommand(sender: CommandSender, 
                         command: Command,
                         label: String,
                         args: Array[String]): Boolean = {
    
    // Permission check for players
    sender match {
      case player: Player if !player.hasPermission("emirver.broadcast") =>
        val noPerm = miniMessage.deserialize("<red>You do not have permissions to use that command!</red>")
        audiences.sender(player).sendMessage(noPerm)
        return true
      case _ => // Continue execution for console or permitted players
    }

    if (args.isEmpty) {
      val usage = miniMessage.deserialize("<red>Usage: /broadcast <message></red>")
      audiences.sender(sender).sendMessage(usage)
    } else {
      val message = args.mkString(" ")
      val formattedMessage = miniMessage.deserialize(s"$broadcastPrefix<reset>$message")
      audiences.all().sendMessage(formattedMessage)
    }
    
    true
  }
}

package org.winlogon.powertools.commands

import org.bukkit.Bukkit
import org.bukkit.command.{Command, CommandExecutor, CommandSender}
import org.bukkit.entity.Player

import org.winlogon.powertools.ChatFormatting

class BroadcastCommand extends CommandExecutor {
  private val broadcastPrefix = "<dark_gray>[<dark_aqua>Broadcast<dark_gray>]"

  override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean = {
    if (sender.isInstanceOf[Player] && !sender.hasPermission("powertools.broadcast")) {
      val noPerm = ChatFormatting.apply("<#F93822>Error: &7You do not have permissions to use that command!")
      sender.sendMessage(ChatFormatting.apply(s"Player &3${args(0)}&7 is not online."))
      sender.sendMessage(noPerm)
      return true
    }

    if (args.isEmpty) {
      val usage = ChatFormatting.apply("&7Usage: &3/broadcast &2<message>")
      sender.sendMessage(usage)
      return true
    }

    val message = args.mkString(" ")
    val formattedMessage = ChatFormatting.apply(s"$broadcastPrefix<reset> &7$message")
    Bukkit.getOnlinePlayers().forEach(_.sendMessage(formattedMessage))
    
    true
  }
}

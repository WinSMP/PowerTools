package org.winlogon.powertools.commands

import org.bukkit.Bukkit
import org.bukkit.command.{Command, CommandExecutor, CommandSender}
import org.bukkit.entity.Player
import org.winlogon.powertools.ChatFormatting

class InvseeCommand extends CommandExecutor {
  override def onCommand(
    sender: CommandSender, command: Command, label: String, args: Array[String]
  ): Boolean = {
    if (!sender.isInstanceOf[Player]) {
      sender.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Only players can use this command."))
      return true
    }

    if (args.length < 1) {
      sender.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Please specify a player's name."))
      return true
    }

    val target = Option(Bukkit.getPlayer(args(0)))

    target match {
      case Some(player) =>
        val senderPlayer = sender.asInstanceOf[Player]
        senderPlayer.openInventory(player.getInventory())
      case None =>
        sender.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Player not found or offline."))
    }
    true
  }
}

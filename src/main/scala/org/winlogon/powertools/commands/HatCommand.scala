package org.winlogon.powertools.commands

import org.bukkit.command.{Command, CommandExecutor, CommandSender}

class HatCommand extends CommandExecutor {
  override def onCommand(
    sender: CommandSender, command: Command, label: String, args: Array[String]
  ): Boolean = {
    sender.sendMessage("hat command")
    true
  }
}

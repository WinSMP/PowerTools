package org.winlogon.powertools.commands

import org.bukkit.{Bukkit, ChatColor, Location}
import org.bukkit.command.{Command, CommandExecutor, CommandSender}
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.winlogon.powertools.ChatFormatting

class SmiteCommand extends CommandExecutor {
  override def onCommand(
    sender: CommandSender, command: Command, label: String, args: Array[String]
  ): Boolean = {
    if (args.length != 1) {
      sender.sendMessage(s"&7Usage: &3/smite &2<player>")
      return true
    }

    val target = Bukkit.getPlayer(args(0))
    if (target == null || !target.isOnline) {
      sender.sendMessage(ChatFormatting.apply(s"<#F93822>Error: &7Player &3${args(0)}&7 is not online."))
      return true
    }

    val location: Location = target.getLocation
    target.getWorld.strikeLightning(location)
    sender.sendMessage(ChatFormatting.apply(s"&7You have smitten &3${target.getName}!"))
    target.sendMessage(ChatFormatting.apply(s"&7You have been smitten by <bold>&3a mighty force!"))
    true
  }
}

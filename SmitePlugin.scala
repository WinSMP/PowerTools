package com.example.smite

import org.bukkit.{Bukkit, ChatColor, Location}
import org.bukkit.command.{Command, CommandExecutor, CommandSender}
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.Listener

class SmitePlugin extends JavaPlugin with Listener {

  override def onEnable(): Unit = {
    getCommand("smite").setExecutor(new SmiteCommandExecutor)
    getLogger.info("SmitePlugin has been enabled!")
  }

  override def onDisable(): Unit = {
    getLogger.info("SmitePlugin has been disabled!")
  }
}

class SmiteCommandExecutor extends CommandExecutor {
  override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean = {
    if (!sender.isInstanceOf[Player]) {
      sender.sendMessage(ChatColor.RED + "Only players can use this command.")
      return true
    }

    if (args.length != 1) {
      sender.sendMessage(ChatColor.RED + "Usage: /smite <player>")
      return true
    }

    val target = Bukkit.getPlayer(args(0))
    if (target == null || !target.isOnline) {
      sender.sendMessage(ChatColor.RED + s"Player ${args(0)} is not online.")
      return true
    }

    val location: Location = target.getLocation
    target.getWorld.strikeLightning(location) // Ensures damage is applied
    sender.sendMessage(ChatColor.GREEN + s"You have smitten ${target.getName}!")
    target.sendMessage(ChatColor.RED + "You have been smitten by a mighty force!")
    true
  }
}

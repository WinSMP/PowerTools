package org.winlogon.powertools

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.command.CommandExecutor

import org.winlogon.powertools.commands.*

class PowerToolsPlugin extends JavaPlugin {
  val commands: Map[String, (CommandExecutor, String)] = Map(
    "invsee" -> (new InvseeCommand(), "powertools.invsee"),
    "smite" -> (new SmiteCommand(), "powertools.smite"),
    "hat" -> (new HatCommand(), "powertools.hat"),
  )

  override def onEnable(): Unit = {
    commands.foreach { case (command, (executor, permission)) =>
      getLogger.info(s"Registering command: $command, with permission: $permission")
      getCommand(command).setExecutor(executor)
      getCommand(command).setPermission(permission)
    }
  }
}

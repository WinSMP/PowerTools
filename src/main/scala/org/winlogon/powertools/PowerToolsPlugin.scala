package org.winlogon.powertools

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.{CommandArguments, CommandExecutor}
import org.bukkit.command.CommandSender
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class PowerToolsPlugin extends JavaPlugin {
  private val whitelistListener = new WhitelistListener()

  override def onEnable(): Unit = {
    registerCommands()
    getServer.getPluginManager.registerEvents(whitelistListener, this)
  }

  private def registerCommands(): Unit = {
    // Broadcast Command
    new CommandAPICommand("broadcast")
      .withPermission("powertools.broadcast")
      .withArguments(new StringArgument("message"))
      .executes((sender: CommandSender, args: CommandArguments) => {
        val message = args.get("message").asInstanceOf[String]
        executeBroadcast(sender, message)
        1
      })
      .register()

    // Hat Command
    new CommandAPICommand("hat")
      .withPermission("powertools.hat")
      .executesPlayer((player: Player, args: CommandArguments) => {
        executeHat(player)
        1
      })
      .register()

    // Invsee Command
    new CommandAPICommand("invsee")
      .withPermission("powertools.invsee")
      .withArguments(new StringArgument("target"))
      .executesPlayer((player: Player, args: CommandArguments) => {
        val target = args.get("target").asInstanceOf[String]
        executeInvsee(player, target)
        1
      })
      .register()

    // Smite Command
    new CommandAPICommand("smite")
      .withPermission("powertools.smite")
      .withArguments(new StringArgument("target"))
      .executes((sender: CommandSender, args: CommandArguments) => {
        val target = args.get("target").asInstanceOf[String]
        executeSmite(sender, target)
        1
      })
      .register()

    // Whitelist Request Command
    new CommandAPICommand("whitelistrequest")
      .withPermission("powertools.whitelist")
      .withAliases(Seq("wlreq", "wlrequest"): _*)
      .withSubcommand(
        new CommandAPICommand("request")
          .withArguments(new StringArgument("player"))
          .executesPlayer((player: Player, args: CommandArguments) => {
            val targetName = args.get("player").asInstanceOf[String]
            player.sendMessage(ChatFormatting.apply(whitelistListener.handleRequest(player, targetName)))
            1
          })
      )
      .withSubcommand(
        new CommandAPICommand("list")
          .executesPlayer((player: Player, args: CommandArguments) => {
            if (!player.hasPermission("whitelist.manage")) {
              player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You do not have permission to view whitelist requests."))
            } else {
              whitelistListener.listRequests(player).foreach(msg => player.sendMessage(ChatFormatting.apply(msg)))
            }
            1
          })
      )
      .withSubcommand(
        new CommandAPICommand("accept")
          .withArguments(new StringArgument("requester"))
          .executesPlayer((player: Player, args: CommandArguments) => {
            val requester = args.get("requester").asInstanceOf[String]
            if (!player.hasPermission("whitelist.manage")) {
              player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You do not have permission to manage whitelist requests."))
            } else {
              player.sendMessage(ChatFormatting.apply(whitelistListener.acceptRequest(player, requester)))
            }
            1
          })
      )
      .withSubcommand(
        new CommandAPICommand("refuse")
          .withArguments(new StringArgument("requester"))
          .executesPlayer((player: Player, args: CommandArguments) => {
            val requester = args.get("requester").asInstanceOf[String]
            if (!player.hasPermission("whitelist.manage")) {
              player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You do not have permission to manage whitelist requests."))
            } else {
              player.sendMessage(ChatFormatting.apply(whitelistListener.refuseRequest(player, requester)))
            }
            1
          })
      )
      .register()
  }

  private def executeBroadcast(sender: CommandSender, message: String): Unit = {
    val formattedMessage = ChatFormatting.apply(s"<dark_gray>[<dark_aqua>Broadcast<dark_gray>]<reset> &7${message}")
    Bukkit.getOnlinePlayers.forEach(_.sendMessage(formattedMessage))
  }

  private def executeHat(player: Player): Unit = {
    val playerInventory = player.getInventory
    val itemInHand = playerInventory.getItemInMainHand
    val helmetItem = playerInventory.getHelmet
    
    playerInventory.setHelmet(itemInHand)
    if (helmetItem == null) {
      playerInventory.setItemInMainHand(null)
    } else {
      playerInventory.setItemInMainHand(helmetItem)
      player.sendMessage(ChatFormatting.apply("&7Swapping items..."))
    }
    
    player.updateInventory()
    player.sendMessage(ChatFormatting.apply("&7Your held item is now &3on your head!"))
  }

  private def executeInvsee(player: Player, targetName: String): Unit = {
    val target = Bukkit.getPlayer(targetName)
    if (target == null || !target.isOnline) {
      player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Player not found or offline."))
    } else {
      player.openInventory(target.getInventory)
    }
  }

  private def executeSmite(sender: CommandSender, targetName: String): Unit = {
    val target = Bukkit.getPlayer(targetName)
    if (target == null || !target.isOnline) {
      sender.sendMessage(ChatFormatting.apply("<#F93822>Error: &7Player not found or offline."))
      return
    }

    target.getWorld.strikeLightning(target.getLocation)
    sender.sendMessage(ChatFormatting.apply(s"&7You have smitten &3${target.getName}!"))
    target.sendMessage(ChatFormatting.apply("&7You have been smitten by <bold>&3a mighty force!"))
  }
}

package org.winlogon.powertools.commands

import org.bukkit.Bukkit
import org.bukkit.command.{Command, CommandExecutor, CommandSender}
import org.bukkit.entity.Player
import org.winlogon.powertools.ChatFormatting

import scala.collection.mutable

// A simple case class to hold a whitelist request
case class WhitelistRequest(requester: String, target: String)

// A global mutable store to keep pending whitelist requests.
// The key here is the requester’s name (assuming one request per player)
object WhitelistManager {
  val pendingRequests: mutable.Map[String, WhitelistRequest] = mutable.Map.empty
}

class WhitelistCommand extends CommandExecutor {
  override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean = {
    if (!sender.isInstanceOf[Player]) {
      sender.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Only players can use this command."))
      return true
    }
    val player = sender.asInstanceOf[Player]

    if (args.isEmpty) {
      player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Usage: /whitelistrequest <request|list|accept|refuse> [player]"))
      return true
    }

    val redColor = "#CE1126"
    val greenColor = "#3AC867"
    args(0).toLowerCase match {

      case "request" =>
        // A player is asking for someone to be whitelisted.
        if (args.length < 2) {
          player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Please specify a player's name to request whitelisting."))
          return true
        }
        val targetName = args(1)
        // Save the request with the requester as the key.
        WhitelistManager.pendingRequests.put(player.getName, WhitelistRequest(player.getName, targetName))
        player.sendMessage(ChatFormatting.apply(s"<#00FF00>Success&7: Whitelist request for $targetName sent."))
        // Notify online admins (or players with whitelist.manage permission).
        Bukkit.getOnlinePlayers.forEach { p =>
          if (p.hasPermission("whitelist.manage")) {
            p.sendMessage(ChatFormatting.apply(
              s"<#00FF00>New whitelist request:&7 ${player.getName} is requesting $targetName to join. Use /whitelistrequest list to view."
            ))
          }
        }
        true

      case "list" =>
        if (!player.hasPermission("whitelist.manage")) {
          player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You do not have permission to view whitelist requests."))
          return true
        }
        if (WhitelistManager.pendingRequests.isEmpty) {
          player.sendMessage(ChatFormatting.apply("<#F93822>No pending whitelist requests."))
          return true
        }

        player.sendMessage(ChatFormatting.apply("<#00FF00>Whitelist requests:"))
        WhitelistManager.pendingRequests.values.foreach { req =>
          val acceptButton = s"<click:run_command:'/whitelistrequest accept ${req.requester}'><$greenColor>[Accept]</$greenColor></click>"
          val refuseButton = s"<click:run_command:'/whitelistrequest refuse ${req.requester}'><$redColor>[Refuse]</$redColor></click>"
          val msg = s"- ${req.requester} is asking for ${req.target} to join $acceptButton $refuseButton"
          player.sendMessage(ChatFormatting.apply(msg))
        }
        true

      case "accept" =>
        // Accepting a whitelist request; only admins may do this.
        if (!player.hasPermission("whitelist.manage")) {
          player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You do not have permission to manage whitelist requests."))
          return true
        }
        if (args.length < 2) {
          player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Please specify the requester whose request you want to accept."))
          return true
        }
        val requester = args(1)
        WhitelistManager.pendingRequests.get(requester) match {
          case Some(req) =>
            // Attempt to get the target player from the server.
            val targetPlayer = Bukkit.getPlayer(req.target)
            if (targetPlayer != null) {
              whitelistPlayer(targetPlayer)
              player.sendMessage(ChatFormatting.apply(s"<$greenColor>Accepted whitelist request:&7 ${req.requester} for ${req.target}."))
              // Optionally notify the requester if online.
              Option(Bukkit.getPlayer(req.requester)).foreach { reqPlayer =>
                reqPlayer.sendMessage(ChatFormatting.apply(s"<$greenColor>Your whitelist request for ${req.target} has been accepted."))
              }
              // Remove the request.
              WhitelistManager.pendingRequests.remove(requester)
            } else {
              player.sendMessage(ChatFormatting.apply(s"<#F93822>Error&7: Target player ${req.target} is not online."))
            }
          case None =>
            player.sendMessage(ChatFormatting.apply(s"<#F93822>Error&7: No whitelist request found for $requester."))
        }
        true

      case "refuse" =>
        
        true

      case _ =>
        player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Unknown subcommand. Usage: /whitelistrequest <request|list|accept|refuse>"))
        true
    }
  }

// Refusing a whitelist request.
  private def refusePlayer(): Unit = {
    if (!player.hasPermission("whitelist.manage")) {
      player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: You do not have permission to manage whitelist requests."))
      return true
    }
    if (args.length < 2) {
      player.sendMessage(ChatFormatting.apply("<#F93822>Error&7: Please specify the requester whose request you want to refuse."))
      return true
    }
    val requester = args(1)
    WhitelistManager.pendingRequests.get(requester) match {
      case Some(req) =>
        player.sendMessage(ChatFormatting.apply(s"<$redColor>Refused whitelist request:&7 &3${req.requester}<$redColor> for &2${req.target}."))
        // Optionally notify the requester if online.
        Option(Bukkit.getPlayer(req.requester)).foreach { reqPlayer =>
          reqPlayer.sendMessage(ChatFormatting.apply(s"<$redColor>Your whitelist request for &3${req.target}<$redColor> has been refused."))
        }
        WhitelistManager.pendingRequests.remove(requester)
      case None =>
        player.sendMessage(ChatFormatting.apply(s"<#F93822>Error&7: No whitelist request found for $requester."))
    }
  }

  // This helper whitelists a player using the Bukkit API.
  private def whitelistPlayer(player: Player): Unit = {
    player.setWhitelisted(true)
  }
}


package org.winlogon.powertools

import org.bukkit.Bukkit
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.entity.Player

import scala.collection.mutable

case class WhitelistRequest(requester: String, target: String)

object WhitelistManager {
  val pendingRequests: mutable.Map[String, WhitelistRequest] = mutable.Map.empty
}

class WhitelistListener extends Listener {
  val redColor = "#CE1126"
  val greenColor = "#3AC867"

  @EventHandler
  def onPlayerJoin(event: PlayerJoinEvent): Unit = {
    val player = event.getPlayer
    if (player.hasPermission("whitelist.manage") && WhitelistManager.pendingRequests.nonEmpty) {
      player.sendMessage(ChatFormatting.apply(
        s"<#FFBF3B>Hello! &7You have &3${WhitelistManager.pendingRequests.size}&7 pending whitelist request(s). <#F93822>Use /whitelistrequest list to review them."
      ))
    }
  }

  def handleRequest(player: Player, targetName: String): String = {
    WhitelistManager.pendingRequests.put(player.getName, WhitelistRequest(player.getName, targetName))
    Bukkit.getOnlinePlayers.forEach { p =>
      if (p.hasPermission("whitelist.manage")) {
        p.sendMessage(ChatFormatting.apply(
          s"<$redColor>New whitelist request:&7 ${player.getName} is requesting $targetName to join. Use /whitelistrequest list to view."
        ))
      }
    }
    s"<$greenColor>Success&7: Whitelist request for $targetName sent."
  }

  def listRequests(player: Player): List[String] = {
    if (WhitelistManager.pendingRequests.isEmpty) {
      List("<#F93822>No pending whitelist requests.")
    } else {
      s"<$greenColor>Whitelist requests:" :: WhitelistManager.pendingRequests.values.map { req =>
        s"- ${req.requester} is asking for ${req.target} " +
          s"<click:run_command:'/whitelistrequest accept ${req.requester}'><$greenColor>[Accept]</$greenColor></click> " +
          s"<click:run_command:'/whitelistrequest refuse ${req.requester}'><$redColor>[Refuse]</$redColor></click>"
      }.toList
    }
  }

  def acceptRequest(player: Player, requester: String): String = {
    WhitelistManager.pendingRequests.get(requester) match {
      case Some(req) =>
        val targetPlayer = Bukkit.getPlayer(req.target)
        if (targetPlayer != null) {
          whitelistPlayer(targetPlayer)
          WhitelistManager.pendingRequests.remove(requester)
          Option(Bukkit.getPlayer(req.requester)).foreach { reqPlayer =>
            reqPlayer.sendMessage(ChatFormatting.apply(s"<$greenColor>Your whitelist request for ${req.target} has been accepted."))
          }
          s"<$greenColor>Accepted whitelist request:&7 ${req.requester} for ${req.target}."
        } else {
          s"<#F93822>Error&7: Target player ${req.target} is not online."
        }
      case None =>
        s"<#F93822>Error&7: No whitelist request found for $requester."
    }
  }

  def refuseRequest(player: Player, requester: String): String = {
    WhitelistManager.pendingRequests.get(requester) match {
      case Some(req) =>
        WhitelistManager.pendingRequests.remove(requester)
        Option(Bukkit.getPlayer(req.requester)).foreach { reqPlayer =>
          reqPlayer.sendMessage(ChatFormatting.apply(s"<$redColor>Your whitelist request for &3${req.target}<$redColor> has been refused."))
        }
        s"<$redColor>Refused whitelist request:&7 ${req.requester} for ${req.target}."
      case None =>
        s"<#F93822>Error&7: No whitelist request found for $requester."
    }
  }

  private def whitelistPlayer(player: Player): Unit = {
    player.setWhitelisted(true)
  }
}

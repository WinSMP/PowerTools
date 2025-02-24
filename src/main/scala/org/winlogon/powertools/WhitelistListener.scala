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
  val greenColor = "#1DB989"
  val errorColor = "#F93822"

  @EventHandler
  def onPlayerJoin(event: PlayerJoinEvent): Unit = {
    val player = event.getPlayer
    if (player.hasPermission("whitelist.manage") && WhitelistManager.pendingRequests.nonEmpty) {
      player.sendMessage(ChatFormatting.apply(
        s"<$greenColor>Hello! &7You have &3${WhitelistManager.pendingRequests.size}&7 pending whitelist request(s). <$greenColor>Use /whitelistrequest list to review them."
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
      List(s"<$errorColor>No pending whitelist requests.")
    } else {
      s"<$greenColor>Whitelist requests:" :: WhitelistManager.pendingRequests.values.map { req =>
        s"- ${req.requester} is asking for ${req.target} " +
          s"<click:run_command:'/whitelistrequest accept ${req.target}'><$greenColor>[Accept]</$greenColor></click> " +
          s"<click:run_command:'/whitelistrequest refuse ${req.requester}'><$redColor>[Refuse]</$redColor></click>"
      }.toList
    }
  }

  def acceptRequest(player: Player, targetName: String): String = {
    // Find all requests for the target
    val requests = WhitelistManager.pendingRequests.values.filter(_.target.equalsIgnoreCase(targetName)).toList
    if (requests.isEmpty) {
      return s"<$errorColor>Error&7: No whitelist requests found for $targetName."
    }
  
    // Whitelist the target (even if offline)
    val offlinePlayer = Bukkit.getOfflinePlayer(targetName)
    offlinePlayer.setWhitelisted(true)
  
    // Remove all requests for this target
    WhitelistManager.pendingRequests --= requests.map(_.requester)
  
    // Notify all requesters
    requests.foreach { req =>
      Option(Bukkit.getPlayer(req.requester)).foreach { requesterPlayer =>
        requesterPlayer.sendMessage(ChatFormatting.apply(s"&7Your whitelist request for &3${req.target}&7 has been <$greenColor>accepted."))
      }
    }
  
    s"<$greenColor>Success&7: Whitelisted $targetName and removed ${requests.size} request(s)."
  }

  def refuseRequest(player: Player, requester: String): String = {
    WhitelistManager.pendingRequests.get(requester) match {
      case Some(req) =>
        WhitelistManager.pendingRequests.remove(requester)
        Option(Bukkit.getPlayer(req.requester)).foreach { reqPlayer =>
          reqPlayer.sendMessage(ChatFormatting.apply(s"&7Your whitelist request for &3${req.target}&7 has been <$redColor>refused."))
        }
        s"<$redColor>Refused&7 whitelist request: &3${req.requester} for &2${req.target}."
      case None =>
        s"<$errorColor>Error&7: No whitelist request found for $requester."
    }
  }

  private def whitelistPlayer(player: Player): Unit = {
    player.setWhitelisted(true)
  }
}

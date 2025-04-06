package org.winlogon.powertools

import org.bukkit.{Bukkit, OfflinePlayer}
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.entity.Player

import net.kyori.adventure.text.Component

import scala.collection.mutable

case class WhitelistRequest(requester: Player, target: OfflinePlayer)

object WhitelistManager {
  val pendingRequests: mutable.Map[String, WhitelistRequest] = mutable.Map.empty
}

class WhitelistListener extends Listener {
  private val redColor   = "#CE1126"
  private val greenColor = "#1DB989"
  private val errorColor = "#F93822"

  @EventHandler
  def onPlayerJoin(event: PlayerJoinEvent): Unit = {
    val player = event.getPlayer
    if (player.hasPermission("whitelist.manage") && WhitelistManager.pendingRequests.nonEmpty)
      sendSuccessMessage(
        player,
        s"Hello! You have ${WhitelistManager.pendingRequests.size} pending whitelist request(s). Use /whitelistrequest list to review them."
      )
  }

  def handleRequest(player: Player, targetName: String): Unit = {
    val target = Bukkit.getOfflinePlayer(targetName)
    WhitelistManager.pendingRequests.put(player.getName, WhitelistRequest(player, target))
    Bukkit.getOnlinePlayers.forEach { p =>
      if (p.hasPermission("whitelist.manage"))
        p.sendMessage(ChatFormatting.apply(
          s"<$redColor>New whitelist request:&7 ${player.getName} is requesting ${target.getName} to join. Use /whitelistrequest list to view."
        ))
    }
    sendSuccessMessage(player, s"Whitelist request for ${target.getName} sent.")
  }

  def listRequests(player: Player): List[Component] = {
    if (WhitelistManager.pendingRequests.isEmpty)
      List(ChatFormatting.apply(s"<$errorColor>No pending whitelist requests."))
    else {
      val header = ChatFormatting.apply(s"<$greenColor>Whitelist requests:")
      val requests = WhitelistManager.pendingRequests.values.map { req =>
        ChatFormatting.apply(
          s"- ${req.requester.getName} is asking for ${req.target.getName} " +
          s"<click:run_command:'/whitelistrequest accept ${req.target.getName}'><$greenColor>[Accept]</$greenColor></click> " +
          s"<click:run_command:'/whitelistrequest refuse ${req.requester.getName}'><$redColor>[Refuse]</$redColor></click>"
        )
      }.toList
      header :: requests
    }
  }


  def acceptRequest(player: Player, targetName: String): Unit = {
    val requests = WhitelistManager.pendingRequests.values.filter(_.target.getName.equalsIgnoreCase(targetName)).toList
    if (requests.isEmpty) {
      sendFailureMessage(player, s"No whitelist requests found for $targetName")
    } else {
      requests.foreach(req => req.target.setWhitelisted(true))
      WhitelistManager.pendingRequests --= requests.map(_.requester.getName)
      requests.foreach { req =>
        Option(Bukkit.getPlayer(req.requester.getName)).foreach(_.sendMessage(ChatFormatting.apply(
          s"&7Your whitelist request for &3${req.target.getName}&7 has been accepted."
        )))
      }
      sendSuccessMessage(player, s"Whitelisted $targetName and removed ${requests.size} request(s)")
    }
  }

  def refuseRequest(player: Player, requesterName: String): Unit = {
    WhitelistManager.pendingRequests.get(requesterName) match {
      case Some(req) =>
        WhitelistManager.pendingRequests.remove(requesterName)
        Option(Bukkit.getPlayer(req.requester.getName)).foreach(_.sendMessage(ChatFormatting.apply(
          s"&7Your whitelist request for &3${req.target.getName}&7 has been refused."
        )))
        sendSuccessMessage(player, s"Refused whitelist request for ${req.target.getName} from ${req.requester.getName}")
      case None =>
        sendFailureMessage(player, s"No whitelist request found for $requesterName")
    }
  }

  private def sendSuccessMessage(player: Player, msg: String): Unit =
    player.sendMessage(ChatFormatting.apply(s"<$greenColor>Success&7: $msg"))

  private def sendFailureMessage(player: Player, msg: String): Unit =
    player.sendMessage(ChatFormatting.apply(s"<$errorColor>Error&7: $msg"))
}

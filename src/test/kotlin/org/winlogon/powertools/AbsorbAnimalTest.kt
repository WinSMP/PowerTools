package org.winlogon.powertools

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.mockbukkit.mockbukkit.entity.WolfMock
import org.mockbukkit.mockbukkit.plugin.PluginMock
import java.util.UUID

/**
 * Covers the packing window: how long it stays open, which click closes it, and which pets it
 * refuses.
 *
 * Packing a pet is deliberately absent, because MockBukkit throws on `Entity#createSnapshot` and
 * the test would only be skipped. That path is covered against a real Paper server instead.
 */
class AbsorbAnimalTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: PluginMock
    private lateinit var absorb: AbsorbAnimal
    private lateinit var player: PlayerMock
    private lateinit var wolf: WolfMock

    private val plain = PlainTextComponentSerializer.plainText()

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        plugin = MockBukkit.createMockPlugin()
        absorb = AbsorbAnimal(plugin)
        player = server.addPlayer()
        wolf = WolfMock(server, UUID.randomUUID())
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    /// The listeners this plugin currently has registered, so that a window piling up is visible
    private fun registeredListeners(): Int = HandlerList.getRegisteredListeners(plugin).size

    /// What the player was just told, so that a test can tell which guard refused them
    private fun lastMessage(): String = player.nextComponentMessage()?.let(plain::serialize).orEmpty()

    @Test
    fun `a window registers one listener and gives it back on cancel`() {
        val before = registeredListeners()

        val id = absorb.awaitClick(player)
        assertEquals(before + 1, registeredListeners())

        assertTrue(absorb.cancel(player, id))
        assertEquals(before, registeredListeners())
    }

    @Test
    fun `opening a second window replaces the first instead of stacking listeners`() {
        val before = registeredListeners()

        absorb.awaitClick(player)
        absorb.awaitClick(player)

        assertEquals(before + 1, registeredListeners())
    }

    @Test
    fun `each window gets its own id`() {
        val first = absorb.awaitClick(player)
        val second = absorb.awaitClick(player)

        assertNotEquals(first, second)
    }

    @Test
    fun `an id from a lapsed window leaves the newer window open`() {
        val lapsed = absorb.awaitClick(player)
        val current = absorb.awaitClick(player)

        assertFalse(absorb.cancel(player, lapsed), "the first window was already replaced")
        assertTrue(absorb.cancel(player, current), "the second window is still open")
    }

    @Test
    fun `cancelling a window that is not open says so`() {
        assertFalse(absorb.cancel(player), "no window was ever opened")

        val id = absorb.awaitClick(player)
        assertTrue(absorb.cancel(player, id))
        assertFalse(absorb.cancel(player, id), "the window is already closed")
    }

    @Test
    fun `a click with the off hand leaves the window open`() {
        val id = absorb.awaitClick(player)

        click(player, wolf, EquipmentSlot.OFF_HAND)

        assertTrue(absorb.cancel(player, id), "only a main hand click counts")
    }

    @Test
    fun `another player's click leaves the window open`() {
        val id = absorb.awaitClick(player)
        val stranger = server.addPlayer()

        click(stranger, wolf, EquipmentSlot.HAND)

        assertTrue(absorb.cancel(player, id), "the click belonged to somebody else")
    }

    @Test
    fun `a click closes the window without cancelling when nothing was packed`() {
        val id = absorb.awaitClick(player)

        val event = click(player, wolf, EquipmentSlot.HAND)

        assertFalse(event.isCancelled, "the pet was refused, so the interaction should go ahead")
        assertFalse(absorb.cancel(player, id), "the click already closed the window")
    }

    @Test
    fun `refuses a target that is not a tameable pet`() {
        assertFalse(absorb.absorbPetOf(player, player))
        assertTrue(lastMessage().contains("not a tamed pet"), lastMessage())
    }

    @Test
    fun `refuses a pet that is not tamed`() {
        wolf.setTamed(false)

        assertFalse(absorb.absorbPetOf(player, wolf))
        assertTrue(lastMessage().contains("not a tamed pet"), lastMessage())
    }

    @Test
    fun `refuses a pet that somebody else owns`() {
        wolf.setTamed(true)
        wolf.setOwnerUUID(UUID.randomUUID())

        assertFalse(absorb.absorbPetOf(player, wolf))
        assertTrue(lastMessage().contains("not a tamed pet"), lastMessage())
    }

    @Test
    fun `refuses a pet that is out of reach`() {
        wolf.setTamed(true)
        wolf.setOwner(player)
        wolf.teleport(player.location.add(Vector(MAX_ENTITY_RANGE + 1.0, 0.0, 0.0)))

        assertFalse(absorb.absorbPetOf(player, wolf))
        assertTrue(lastMessage().contains("too far away"), lastMessage())
    }

    private fun click(who: PlayerMock, target: WolfMock, hand: EquipmentSlot): PlayerInteractAtEntityEvent {
        val event = PlayerInteractAtEntityEvent(who, target, who.eyeLocation.toVector(), hand)
        server.pluginManager.callEvent(event)
        return event
    }
}

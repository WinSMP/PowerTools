package org.winlogon.powertools

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Entity
import org.bukkit.entity.Villager
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.mockbukkit.mockbukkit.entity.VillagerMock
import org.mockbukkit.mockbukkit.entity.WanderingTraderMock
import org.mockbukkit.mockbukkit.entity.WolfMock
import org.mockbukkit.mockbukkit.entity.ZombieVillagerMock
import org.mockbukkit.mockbukkit.plugin.PluginMock
import java.util.UUID

/**
 * Covers the packing window: how long it stays open, which click closes it, which entities it accepts,
 * and the lore it writes on the egg.
 */
class AbsorbEntityTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: PluginMock
    private lateinit var absorb: AbsorbEntity
    private lateinit var player: PlayerMock
    private lateinit var wolf: WolfMock
    private lateinit var villager: VillagerMock

    private val plain = PlainTextComponentSerializer.plainText()

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        plugin = MockBukkit.createMockPlugin()
        absorb = AbsorbEntity(plugin)
        player = server.addPlayer()
        wolf = WolfMock(server, UUID.randomUUID())
        villager = VillagerMock(server, UUID.randomUUID())
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    /// The listeners this plugin currently has registered, so that a window piling up is visible
    private fun registeredListeners(): Int = HandlerList.getRegisteredListeners(plugin).size

    /// What the player was just told as a plain string
    private fun lastMessage(): String = player.nextComponentMessage()?.let(plain::serialize).orEmpty()

    /// The lore of a packed entity, as the player will read it down the side of the egg
    private fun loreOf(entity: Entity): List<String> = absorb.loreFor(entity).map(plain::serialize)

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
    fun `refuses a target that is neither a pet nor a villager`() {
        assertFalse(absorb.absorbEntityOf(player, player))
        assertTrue(lastMessage().contains("tamed pets or a villager"), lastMessage())
    }

    @Test
    fun `refuses a pet that is not tamed`() {
        wolf.setTamed(false)

        assertFalse(absorb.absorbEntityOf(player, wolf))
        assertTrue(lastMessage().contains("not a tamed pet"), lastMessage())
    }

    @Test
    fun `refuses a pet that somebody else owns`() {
        wolf.setTamed(true)
        wolf.setOwnerUUID(UUID.randomUUID())

        assertFalse(absorb.absorbEntityOf(player, wolf))
        assertTrue(lastMessage().contains("not a tamed pet"), lastMessage())
    }

    @Test
    fun `refuses a pet that is out of reach`() {
        wolf.setTamed(true)
        wolf.setOwner(player)
        wolf.teleport(player.location.add(Vector(MAX_ENTITY_RANGE + 1.0, 0.0, 0.0)))

        assertFalse(absorb.absorbEntityOf(player, wolf))
        assertTrue(lastMessage().contains("too far away"), lastMessage())
    }

    @Test
    fun `refuses a villager that is out of reach`() {
        player.addAttachment(plugin, PACK_VILLAGER_PERMISSION, true)
        villager.teleport(player.location.add(Vector(MAX_ENTITY_RANGE + 1.0, 0.0, 0.0)))

        assertFalse(absorb.absorbEntityOf(player, villager))
        assertTrue(lastMessage().contains("too far away"), lastMessage())
    }

    @Test
    fun `a villager needs its own permission`() {
        assertTrue(
            absorb.refusalFor(player, villager)?.contains("permission") == true,
            "a villager belongs to nobody, so packing one is a separate grant"
        )
    }

    @Test
    fun `a villager is packable once permitted`() {
        player.addAttachment(plugin, PACK_VILLAGER_PERMISSION, true)

        assertNull(absorb.refusalFor(player, villager))
    }

    @Test
    fun `a zombified villager is not a villager to pack`() {
        val zombie = ZombieVillagerMock(server, UUID.randomUUID())
        player.addAttachment(plugin, PACK_VILLAGER_PERMISSION, true)

        assertTrue(
            absorb.refusalFor(player, zombie)?.contains("tamed pets or a villager") == true,
            "a zombie villager is a zombie that is still being cured, not a villager"
        )
    }

    @Test
    fun `a pet stays packable without the villager permission`() {
        wolf.setTamed(true)
        wolf.setOwner(player)

        assertNull(absorb.refusalFor(player, wolf))
    }

    @Test
    fun `a wandering trader is not a villager to pack`() {
        val trader = WanderingTraderMock(server, UUID.randomUUID())
        player.addAttachment(plugin, PACK_VILLAGER_PERMISSION, true)

        assertTrue(
            absorb.refusalFor(player, trader)?.contains("tamed pets or a villager") == true,
            "carrying a wandering trader about would carry its heavily discounted trades about"
        )
    }

    @Test
    fun `a pet's lore says what the pet is`() {
        wolf.setTamed(true)
        wolf.setOwner(player)
        wolf.customName(Component.text("Rex"))
        wolf.health = 16.0

        assertEquals(
            listOf("Right click to spawn", "Name: Rex", "Health: 8.0 HP"),
            loreOf(wolf)
        )
    }

    @Test
    fun `a villager's lore describes the villager instead of the pet`() {
        villager.profession = Villager.Profession.FARMER
        villager.villagerType = Villager.Type.JUNGLE
        villager.villagerLevel = 3
        villager.customName(Component.text("Bess"))

        assertEquals(
            listOf(
                "Right click to spawn",
                "Villager name: Bess",
                "Health: 10.0 HP",
                "Profession: Farmer",
                "Biome: Jungle",
                "Trade level: 3",
                "Trades left: 0",
            ),
            loreOf(villager)
        )
    }

    @Test
    fun `a villager with no name or profession still reads as a sentence`() {
        villager.profession = Villager.Profession.NONE
        villager.villagerType = Villager.Type.PLAINS

        assertEquals(
            listOf(
                "Right click to spawn",
                "Villager name: Unnamed",
                "Health: 10.0 HP",
                "Profession: Unemployed",
                "Biome: Plains",
            ),
            loreOf(villager).take(5)
        )
    }

    @Test
    fun `a zombified villager never reaches the villager lore`() {
        val zombie = ZombieVillagerMock(server, UUID.randomUUID())
        zombie.villagerProfession = Villager.Profession.LIBRARIAN

        assertEquals(listOf("Right click to spawn"), loreOf(zombie))
    }

    @Test
    fun `a villager name is never read as markup`() {
        villager.profession = Villager.Profession.FARMER
        villager.customName(Component.text("<hover:show_entity:'1:2:3'>Bess</hover>"))

        assertEquals("Villager name: <hover:show_entity:'1:2:3'>Bess</hover>", loreOf(villager)[1])
    }

    private fun click(who: PlayerMock, target: Entity, hand: EquipmentSlot): PlayerInteractAtEntityEvent {
        val event = PlayerInteractAtEntityEvent(who, target, who.eyeLocation.toVector(), hand)
        server.pluginManager.callEvent(event)
        return event
    }
}

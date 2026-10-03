package org.winlogon.powertools

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.winlogon.powertools.ChatFormatting.sentenceCase
import org.winlogon.powertools.ChatFormatting.toComponent

class ChatFormattingTest {
    private val plain = PlainTextComponentSerializer.plainText()
    private val pink = TextColor.fromHexString("#b66c97")

    /// Every component of the tree, so that an assertion holds whatever shape MiniMessage picks
    private fun Component.flatten(): Sequence<Component> =
        sequenceOf(this) + children().asSequence().flatMap { it.flatten() }

    private fun Component.colors(): Set<TextColor> =
        flatten().mapNotNull { it.style().color() }.toSet()

    @Test
    fun `converts minimessage tags`() {
        val component = "<red>x</red>".toComponent()

        assertEquals("x", plain.serialize(component))
        assertEquals(setOf(NamedTextColor.RED), component.colors())
    }

    @Test
    fun `converts legacy colour codes`() {
        val component = "&cx".toComponent()

        assertEquals("x", plain.serialize(component))
        assertEquals(setOf(NamedTextColor.RED), component.colors())
    }

    @Test
    fun `converts hex colours`() {
        val component = "<color:#b66c97>x</color>".toComponent()

        assertEquals(setOf(pink), component.colors())
    }

    /// A pet name reaches us as player input, so it must be shown rather than interpreted: a value
    /// that went through the parser would lose its tags or bring its own styling along.
    @ParameterizedTest
    @ValueSource(
        strings = [
            "<red>x</red>",
            "</color>",
            "<hover:show_entity:'1:2:3'>x</hover>",
            "<gradient:red:blue>x</gradient>",
            "&cx",
            "`",
        ]
    )
    fun `keeps a placeholder value out of the parser`(payload: String) {
        val component = "<color:#b66c97><label></color>".toComponent(Placeholder.unparsed("label", payload))

        assertEquals(payload, plain.serialize(component))
        assertEquals(setOf(pink), component.colors(), "the value brought its own styling along")
        assertTrue(component.flatten().none { it.style().hoverEvent() != null }, "the value brought a hover along")
    }

    @Test
    fun `styles a placeholder value like any other text`() {
        val component = "<gray>Pet name: <dark_gray><name></dark_gray></gray>"
            .toComponent(Placeholder.unparsed("name", "Rex"))

        assertEquals("Pet name: Rex", plain.serialize(component))
        assertEquals(
            setOf(NamedTextColor.GRAY, NamedTextColor.DARK_GRAY),
            component.colors()
        )
    }

    /// The label is a gradient, so the legacy conversion has to pass the tag through rather than
    /// leave it as text or drop the colours
    @Test
    fun `keeps a gradient across a placeholder`() {
        val component = "<gradient:#94587b:#b66c97><label></gradient>"
            .toComponent(Placeholder.unparsed("label", "Packed Wolf"))

        assertEquals("Packed Wolf", plain.serialize(component))
        assertTrue(component.colors().size > 1, "the gradient did not survive the conversion")
        assertTrue(
            component.flatten().none { it.style().hoverEvent() != null },
            "the value brought a hover along"
        )
    }

    @Test
    fun `sentence case capitalises and terminates`() {
        assertEquals("Hello.", "hello".sentenceCase())
        assertEquals("Hello", "hello".sentenceCase(appendPeriod = false))
        assertEquals("Hello world.", "  hello world  ".sentenceCase())
        assertEquals("Already done.", "Already done.".sentenceCase())
    }

    @Test
    fun `sentence case leaves nothing to say as nothing`() {
        assertEquals("", "".sentenceCase())
        assertEquals("", "   ".sentenceCase())
        assertEquals("", "".sentenceCase(appendPeriod = false))
    }
}

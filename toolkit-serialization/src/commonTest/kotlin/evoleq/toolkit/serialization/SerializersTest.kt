package evoleq.toolkit.serialization

import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SerializersTest {
    @Test
    fun manuallyRegisteredValuesCanBeLookedUp() {
        val registry = Serializers {
            add<String>(String.serializer())
            add<Int>(Int.serializer())
        }
        assertEquals(2, registry.size)
        assertTrue(String::class in registry)
        assertEquals(String.serializer(), registry.require(String::class))
    }

    @Test
    fun independentlyDeclaredModulesComposeWithoutMutation() {
        val authentication = Serializers { add<String>(String.serializer()) }
        val billing = Serializers { add<Int>(Int.serializer()) }

        val app = Serializers { include(authentication); include(billing) }
        assertEquals(2, app.size)
        assertEquals(1, authentication.size)
        assertEquals(1, billing.size)
        assertEquals(setOf(String::class, Int::class), app.keys())
    }

    @Test
    fun accidentallyIncludingTheSameClassTwiceFails() {
        val module = Serializers { add<String>(String.serializer()) }
        assertFailsWith<IllegalArgumentException> {
            Serializers { include(module); include(module) }
        }
        assertFailsWith<IllegalArgumentException> {
            Serializers { add<String>(String.serializer()); add<String>(String.serializer()) }
        }
    }

    @Test
    fun anExplicitReplaceWinsButCannotInventANewRegistration() {
        val module = Serializers { add<String>(String.serializer()) }
        val app = Serializers {
            include(module)
            replace<String>(String.serializer())
        }
        assertEquals(String.serializer(), app.require(String::class))
        assertFailsWith<IllegalArgumentException> {
            Serializers { replace<String>(String.serializer()) }
        }
    }

    @Test
    fun incompleteRegistriesCannotBeSilentlyUsed() {
        val registry = Serializers { add<String>(String.serializer()) }
        assertFailsWith<IllegalArgumentException> { registry.require(Int::class) }
        assertFalse(Int::class in registry)
        // Snapshot is not a mutable Map exposed to consumers.
        assertEquals(setOf(String::class), registry.asMap().keys)
    }
}

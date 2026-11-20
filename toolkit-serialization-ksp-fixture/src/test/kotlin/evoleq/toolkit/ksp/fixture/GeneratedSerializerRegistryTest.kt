package evoleq.toolkit.ksp.fixture

import kotlinx.serialization.json.Json
import evoleq.toolkit.serialization.Serializers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedSerializerRegistryTest {
    @Test
    fun generatedRegistryContainsSerializableClassesFromMultipleFiles() {
        val generated = GeneratedFeatureSerializers
        assertEquals(3, generated.size)
        assertTrue(Login::class in generated)
        assertTrue(LoggedIn::class in generated)
        assertTrue(BillingEntry::class in generated)
        assertFalse(SecretInternalDto::class in generated)
        assertFalse(GenericBox::class in generated)
    }

    @Test
    fun generatedSerializerCanRoundTripARealDto() {
        val value = Login("alice", "secret")
        val codec = GeneratedFeatureSerializers.require(Login::class)
        val json = Json.encodeToString(codec, value)
        assertEquals(value, Json.decodeFromString(codec, json))
    }

    @Test
    fun generatedAndHandwrittenRegistriesUseTheSameRuntimeContract() {
        val manual = Serializers {
            add(SecretInternalDto::class, SecretInternalDto.serializer())
        }
        val application = Serializers {
            include(GeneratedFeatureSerializers)
            include(manual)
        }
        assertEquals(4, application.size)
        assertTrue(SecretInternalDto::class in application)
        assertFailsWith<IllegalArgumentException> {
            Serializers {
                include(GeneratedFeatureSerializers)
                add(Login::class, Login.serializer())
            }
        }
    }

    @Test
    fun manualRegistrationCanExplicitlyOverrideGeneratedValue() {
        val registry = Serializers {
            include(GeneratedFeatureSerializers)
            replace(Login::class, Login.serializer())
        }
        assertEquals(Login.serializer().descriptor.serialName,
            registry.require(Login::class).descriptor.serialName)
    }
}

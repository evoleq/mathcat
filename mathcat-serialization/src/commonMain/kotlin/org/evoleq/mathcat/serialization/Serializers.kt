package org.evoleq.mathcat.serialization

import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * A completed, immutable registry of ordinary (non-parameterized) class serializers.
 *
 * Both manual declarations and KSP-generated declarations produce this type.
 * There is no global mutable registry or dependency on KSP or Ktor at runtime.
 *
 * For List<T>, Map<K,V> and other generic types compose the appropriate
 * kotlinx.serialization KSerializer explicitly, not through List::class.
 */
class Serializers private constructor(
    private val entries: Map<KClass<*>, KSerializer<*>>
) {
    val size: Int get() = entries.size

    operator fun contains(type: KClass<*>): Boolean = entries.containsKey(type)

    fun keys(): Set<KClass<*>> = entries.keys.toSet()

    fun <T : Any> require(type: KClass<T>): KSerializer<T> {
        val serializer = requireNotNull(entries[type]) {
            "No serializer registered for " + type
        }
        @Suppress("UNCHECKED_CAST")
        return serializer as KSerializer<T>
    }

    /** Defensive copy, preserving a read-only view of the finished snapshot. */
    fun asMap(): Map<KClass<*>, KSerializer<*>> = entries.toMap()

    class Builder {
        private val entries = mutableMapOf<KClass<*>, KSerializer<*>>()

        inline fun <reified T : Any> add(serializer: KSerializer<T>) =
            add(T::class, serializer)

        /** Fail on accidental duplicate registrations instead of overwriting. */
        fun <T : Any> add(type: KClass<T>, serializer: KSerializer<T>) {
            require(type !in entries) {
                "Serializer already registered for " + type + "; use replace() explicitly"
            }
            entries[type] = serializer
        }

        /** Compose a handwritten or generated feature registry. */
        fun include(other: Serializers) {
            for ((key, serializer) in other.entries) {
                require(key !in entries) {
                    "Serializer already registered for " + key + " while including registry"
                }
                entries[key] = serializer
            }
        }

        inline fun <reified T : Any> replace(serializer: KSerializer<T>) =
            replace(T::class, serializer)

        /** Replace only an existing registration; a typo must not silently add one. */
        fun <T : Any> replace(type: KClass<T>, serializer: KSerializer<T>) {
            require(type in entries) {
                "Cannot replace an unregistered serializer for " + type
            }
            entries[type] = serializer
        }

        internal fun build(): Serializers = Serializers(entries.toMap())
    }

    companion object {
        operator fun invoke(configure: Builder.() -> Unit): Serializers =
            Builder().apply(configure).build()
    }
}

/**
 * Exclude a serializable class from the generated registration in its module.
 * The class remains serializable and may still be registered manually.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class SkipAutoSerializer

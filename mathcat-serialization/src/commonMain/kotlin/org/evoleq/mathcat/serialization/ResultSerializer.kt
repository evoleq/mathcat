package org.evoleq.mathcat.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.evoleq.mathcat.result.Result
import kotlin.reflect.KClass

/** Preserve the old Result envelope while using an explicit payload codec. */
class ResultSerializer<T : Any>(
    private val payload: KSerializer<T>,
    private val classType: (T) -> String = {
        it::class.simpleName ?: payload.descriptor.serialName.substringAfterLast('.')
    }
) : KSerializer<Result<T>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Result") {
        element<String>("type")
        element<String>("classType", isOptional = true)
        element("data", payload.descriptor, isOptional = true)
        element<String>("message", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: Result<T>) {
        val output = encoder.beginStructure(descriptor)
        when (value) {
            is Result.Success -> {
                output.encodeStringElement(descriptor, 0, "Success")
                output.encodeStringElement(descriptor, 1, classType(value.data))
                output.encodeSerializableElement(descriptor, 2, payload, value.data)
            }
            is Result.Failure.Message -> {
                output.encodeStringElement(descriptor, 0, "Failure")
                output.encodeStringElement(descriptor, 3, value.value)
            }
            is Result.Failure.Exception -> {
                output.encodeStringElement(descriptor, 0, "Failure")
                output.encodeStringElement(
                    descriptor, 3, value.value.message ?: "No message provided"
                )
            }
            is Result.Failure.HttpStatusMessage -> {
                output.encodeStringElement(descriptor, 0, "Failure")
                output.encodeStringElement(descriptor, 3, value.value)
            }
        }
        output.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Result<T> {
        val input = decoder.beginStructure(descriptor)
        var type: String? = null
        var data: T? = null
        var message: String? = null
        while (true) {
            when (val index = input.decodeElementIndex(descriptor)) {
                0 -> type = input.decodeStringElement(descriptor, 0)
                1 -> input.decodeStringElement(descriptor, 1) // historical hint only
                2 -> data = input.decodeSerializableElement(descriptor, 2, payload)
                3 -> message = input.decodeStringElement(descriptor, 3)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected Result field index " + index)
            }
        }
        input.endStructure(descriptor)
        return when (type) {
            "Success" -> Result.Success(
                data ?: throw SerializationException("Success missing data")
            )
            "Failure" -> Result.Failure.Message(message ?: "Unknown message")
            else -> throw SerializationException("Unexpected Result type " + type)
        }
    }
}

fun <T : Any> Serializers.resultSerializer(type: KClass<T>): KSerializer<Result<T>> =
    ResultSerializer(require(type))

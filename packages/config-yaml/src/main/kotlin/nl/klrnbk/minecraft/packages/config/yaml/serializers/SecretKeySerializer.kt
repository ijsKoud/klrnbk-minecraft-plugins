package nl.klrnbk.minecraft.packages.config.yaml.serializers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import nl.klrnbk.minecraft.packages.common.cryptography.secretKeyToString
import javax.crypto.SecretKey

class SecretKeySerializer : JsonSerializer<SecretKey>() {
    override fun serialize(
        value: SecretKey,
        gen: JsonGenerator,
        serializers: SerializerProvider,
    ) {
        gen.writeString(secretKeyToString(value))
    }
}

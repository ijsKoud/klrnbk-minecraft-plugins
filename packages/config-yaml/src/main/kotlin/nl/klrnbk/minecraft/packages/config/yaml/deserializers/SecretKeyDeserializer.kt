package nl.klrnbk.minecraft.packages.config.yaml.deserializers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import nl.klrnbk.minecraft.packages.common.cryptography.stringToSecretKey
import javax.crypto.SecretKey

class SecretKeyDeserializer : JsonDeserializer<SecretKey>() {
    override fun deserialize(
        parser: JsonParser,
        ctxt: DeserializationContext,
    ): SecretKey = stringToSecretKey(parser.text)
}

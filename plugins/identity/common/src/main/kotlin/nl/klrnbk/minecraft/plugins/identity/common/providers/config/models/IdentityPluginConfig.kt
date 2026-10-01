package nl.klrnbk.minecraft.plugins.identity.common.providers.config.models

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import nl.klrnbk.minecraft.packages.config.yaml.deserializers.SecretKeyDeserializer
import nl.klrnbk.minecraft.packages.config.yaml.serializers.SecretKeySerializer
import nl.klrnbk.minecraft.packages.database.DatasourceConfig
import javax.crypto.SecretKey

data class IdentityPluginConfig(
    val useProxy: Boolean = false,
    @field:JsonSerialize(using = SecretKeySerializer::class)
    @field:JsonDeserialize(using = SecretKeyDeserializer::class)
    val encryptionKey: SecretKey? = null,
    val logs: IdentityPluginLogsConfig = IdentityPluginLogsConfig(),
    val database: DatasourceConfig = DatasourceConfig(),
)

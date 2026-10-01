package nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.PlayerWhitelistEntity
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.database.models.PlayerWhitelistEntityTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Singleton
class PlayerWhitelistRepository
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseRepository(context) {
        fun findByPlayerIdentityId(playerIdentityId: Uuid): PlayerWhitelistEntity? =
            execute {
                PlayerWhitelistEntity.find { PlayerWhitelistEntityTable.identityId eq playerIdentityId }.firstOrNull()
            }

        fun upsert(
            identityId: Uuid,
            actorIdentityId: Uuid,
            isWhitelisted: Boolean,
        ) = execute {
            PlayerWhitelistEntityTable.upsert(PlayerWhitelistEntityTable.identityId) {
                it[PlayerWhitelistEntityTable.identityId] = identityId
                it[PlayerWhitelistEntityTable.isWhitelisted] = isWhitelisted
                it[PlayerWhitelistEntityTable.actorIdentityId] = actorIdentityId
                it[PlayerWhitelistEntityTable.lastUpdatedAt] = Clock.System.now()
            }
        }
    }

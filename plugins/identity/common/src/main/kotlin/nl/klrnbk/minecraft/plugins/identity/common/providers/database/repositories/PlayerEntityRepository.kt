package nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerEntity
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerEntityTable
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Singleton
class PlayerEntityRepository
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseRepository(context) {
        fun count(): Long =
            execute {
                PlayerEntity
                    .all()
                    .count()
            }

        fun findById(uuid: Uuid): PlayerEntity? =
            execute {
                PlayerEntity
                    .findById(uuid)
            }

        fun findByPlayerId(playerId: Uuid): PlayerEntity? =
            execute {
                PlayerEntity
                    .find { PlayerEntityTable.playerId eq playerId }
                    .firstOrNull()
            }

        /**
         * Names are case-insensitive in Minecraft. A name can have belonged to several players over time
         * (when its previous owner never rejoined after renaming), so the player that joined last wins.
         */
        fun findByName(name: String): PlayerEntity? =
            execute {
                PlayerEntity
                    .find { PlayerEntityTable.name.lowerCase() eq name.lowercase() }
                    .orderBy(PlayerEntityTable.firstJoined to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
            }

        fun findAllByIds(ids: Collection<Uuid>): List<PlayerEntity> =
            execute {
                if (ids.isEmpty()) emptyList() else PlayerEntity.forIds(ids.toList()).toList()
            }

        fun findNamesByPrefix(
            prefix: String,
            limit: Int,
        ): List<String> =
            execute {
                val escaped = prefix.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

                PlayerEntityTable
                    .select(PlayerEntityTable.name)
                    .where { PlayerEntityTable.name.lowerCase() like LikePattern("$escaped%", '\\') }
                    .orderBy(PlayerEntityTable.name.lowerCase() to SortOrder.ASC)
                    .limit(limit)
                    .map { it[PlayerEntityTable.name] }
            }

        fun findAll(pagination: QueryPagination): List<PlayerEntity> =
            execute {
                PlayerEntity
                    .all()
                    .orderBy(PlayerEntityTable.name.lowerCase() to SortOrder.ASC)
                    .limit(pagination.itemsPerPage)
                    .offset(pagination.page.toLong() * pagination.itemsPerPage)
                    .toList()
            }

        fun updateNameByPlayerId(
            playerId: Uuid,
            newName: String,
        ): Boolean =
            execute {
                val playerEntity =
                    findByPlayerId(playerId)
                        ?: throw IllegalArgumentException("Player with ID $playerId not found")
                if (playerEntity.name == newName) return@execute false

                PlayerEntity.findByIdAndUpdate(playerEntity.id.value) {
                    it.name = newName
                }

                true
            }

        fun create(
            playerId: Uuid,
            name: String,
        ): PlayerEntity =
            execute {
                val playerEntity = findByPlayerId(playerId)
                if (playerEntity != null) {
                    throw IllegalArgumentException("Player with ID $playerId already exists")
                }

                PlayerEntity.new {
                    this.name = name
                    this.playerId = playerId
                    this.firstJoined = Clock.System.now()
                }
            }
    }

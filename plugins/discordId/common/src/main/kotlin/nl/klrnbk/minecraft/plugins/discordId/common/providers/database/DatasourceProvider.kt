package nl.klrnbk.minecraft.plugins.discordId.common.providers.database

import com.google.inject.Inject
import nl.klrnbk.minecraft.packages.database.BaseDatasource
import nl.klrnbk.minecraft.packages.database.DatabaseContext

class DatasourceProvider
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseDatasource(context)

package nl.klrnbk.minecraft.plugins.whitelist.common.providers.database

import nl.klrnbk.minecraft.packages.database.BaseDatasource
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import com.google.inject.Inject

class DatasourceProvider
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseDatasource(context)

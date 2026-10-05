package nl.klrnbk.minecraft.packages.database.transfer

/**
 * An export or import that can't continue because of its input (a bad file, a mismatching schema, a target
 * that isn't empty), as opposed to a database failure. The message is safe to show to an administrator.
 */
class DatabaseTransferException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

package nl.klrnbk.minecraft.plugins.gui.api

/**
 * Marks declarations that are public only because the KLRNBK GUI implementation modules
 * (`gui:common`, `gui:velocity`) live in different Gradle modules than the API.
 *
 * Consumers must never use them: they are not covered by the semantic-versioning
 * guarantees of the public API and can change or disappear in any release.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is internal to the KLRNBK GUI framework and not part of its stable API.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
public annotation class InternalGuiApi

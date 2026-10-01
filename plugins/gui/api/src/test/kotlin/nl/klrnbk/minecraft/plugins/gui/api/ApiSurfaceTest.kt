package nl.klrnbk.minecraft.plugins.gui.api

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

/**
 * The API's promise: no protocol-library, Geyser or netty type can appear in a public signature, so a protocol swap can
 * never be a source or binary break for consumers. This scans every class of the API module.
 */
class ApiSurfaceTest {
    private val forbiddenPrefixes =
        listOf(
            "com.github.retrooper",
            "io.github.retrooper",
            "org.geysermc",
            "io.netty",
            "nl.klrnbk.minecraft.plugins.gui.common",
            "nl.klrnbk.minecraft.plugins.gui.velocity",
        )

    private fun apiClasses(): List<Class<*>> {
        val root = Path.of(Gui::class.java.protectionDomain.codeSource.location.toURI())
        Files.walk(root).use { paths ->
            return paths
                .filter { it.extension == "class" }
                .map { root.relativize(it).toString().removeSuffix(".class").replace('/', '.') }
                .filter { !it.endsWith("module-info") }
                .map { Class.forName(it, false, Gui::class.java.classLoader) }
                .toList()
        }
    }

    private fun typesIn(type: Type): List<Class<*>> =
        when (type) {
            is Class<*> -> listOf(if (type.isArray) type.componentType else type)
            is ParameterizedType -> typesIn(type.rawType) + type.actualTypeArguments.flatMap(::typesIn)
            is WildcardType -> (type.upperBounds + type.lowerBounds).flatMap(::typesIn)
            else -> emptyList()
        }

    private fun isExposed(member: Member) = Modifier.isPublic(member.modifiers) || Modifier.isProtected(member.modifiers)

    @Test
    fun `no public signature mentions a protocol geyser netty or implementation type`() {
        val offenders = mutableListOf<String>()
        fun check(
            where: String,
            type: Type,
        ) {
            typesIn(type).filter { c -> forbiddenPrefixes.any { c.name.startsWith(it) } }.forEach { offenders += "$where uses ${it.name}" }
        }
        val classes = apiClasses()
        assertTrue(classes.size > 20, "expected to find the API classes, found ${classes.size}")
        for (cls in classes) {
            (cls.genericInterfaces.toList() + listOfNotNull(cls.genericSuperclass)).forEach { check("${cls.name} supertype", it) }
            cls.declaredMethods.filter(::isExposed).forEach { m ->
                check("${cls.name}.${m.name} return", m.genericReturnType)
                m.genericParameterTypes.forEach { check("${cls.name}.${m.name} parameter", it) }
            }
            cls.declaredConstructors.filter(::isExposed).forEach { c -> c.genericParameterTypes.forEach { check("${cls.name}.<init> parameter", it) } }
            cls.declaredFields.filter(::isExposed).forEach { check("${cls.name}.${it.name}", it.genericType) }
        }
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun `the API is written against velocity and adventure only`() {
        val allowed = listOf("java.", "kotlin.", "com.velocitypowered.api.", "net.kyori.adventure.", "nl.klrnbk.minecraft.plugins.gui.api.", "org.jetbrains.annotations.")
        val offenders = mutableListOf<String>()
        for (cls in apiClasses()) {
            (cls.declaredMethods.filter(::isExposed).flatMap { m -> listOf(m.genericReturnType) + m.genericParameterTypes.toList() })
                .flatMap(::typesIn)
                .filter { c -> !c.isPrimitive && allowed.none { c.name.startsWith(it) } }
                .forEach { offenders += "${cls.name} exposes ${it.name}" }
        }
        assertTrue(offenders.isEmpty(), offenders.distinct().joinToString("\n"))
    }
}

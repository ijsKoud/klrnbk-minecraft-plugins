package nl.klrnbk.minecraft.plugins.gui.common.menu

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.velocitypowered.api.proxy.Player
import nl.klrnbk.minecraft.plugins.gui.api.GuiClickType
import nl.klrnbk.minecraft.plugins.gui.api.GuiLayout
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.api.action.ClientClick
import nl.klrnbk.minecraft.plugins.gui.api.action.GuiAction
import nl.klrnbk.minecraft.plugins.gui.api.action.ProxyCommandExecutor
import nl.klrnbk.minecraft.plugins.gui.api.item.GuiData

class ParseResult(
    val menu: MenuDef?,
    val issues: List<MenuIssue>,
)

/**
 * Reads one menu file. Never throws for bad content: a bad *item* is reported and skipped, a bad *menu-level* setting
 * (title, size) drops the menu, and every problem names the file and the YAML path so an admin can find it.
 */
class MenuParser(
    /** What `open_menu` does when clicked; supplied by the service that owns the menus. */
    private val openMenu: (id: String, player: Player) -> Unit,
    private val mapper: YAMLMapper = YAMLMapper(),
) {
    fun parse(
        id: String,
        fileName: String,
        text: String,
    ): ParseResult {
        val issues = mutableListOf<MenuIssue>()

        fun issue(
            path: String,
            message: String,
        ) {
            issues += MenuIssue(fileName, path, message)
        }

        val root =
            try {
                mapper.readTree(text)
            } catch (e: Exception) {
                issue("", "not valid YAML: ${e.message?.lineSequence()?.firstOrNull()}")
                return ParseResult(null, issues)
            }
        if (root == null || !root.isObject) {
            issue("", "the file must contain a mapping (title, rows, items, ...)")
            return ParseResult(null, issues)
        }

        val title = root.text("title")
        if (title.isNullOrBlank()) issue("title", "required (MiniMessage text)")

        val layout = parseLayout(root, ::issue)
        if (title.isNullOrBlank() || layout == null) return ParseResult(null, issues)

        val commands = root.stringList("commands").map { it.removePrefix("/").trim().lowercase() }
        commands.filter { !COMMAND_NAME.matches(it) }.forEach { issue("commands", "'$it' is not a valid command name (letters, digits, - and _)") }
        val permission = root.text("permission")?.takeIf { it.isNotBlank() }
        val refresh = root.get("refresh_seconds")?.let { if (it.isInt && it.asInt() >= 1) it.asInt() else null.also { issue("refresh_seconds", "must be a whole number >= 1") } }

        val references = linkedSetOf<String>()
        val filler = root.get("filler")?.let { parseItem("filler", it, references, requireMaterial = true, ::issue) }

        val items = mutableListOf<ItemDef>()
        val itemsNode = root.get("items")
        if (itemsNode != null && !itemsNode.isObject) {
            issue("items", "must be a mapping of item id -> item")
        } else {
            itemsNode?.fields()?.forEach { (itemId, node) ->
                val path = "items.$itemId"
                val slots = parseSlots(node, path, layout.size, ::issue)
                val spec = parseItem(path, node, references, requireMaterial = true, ::issue)
                if (spec != null && slots.isNotEmpty()) items += ItemDef(itemId, slots, spec)
            }
        }
        return ParseResult(MenuDef(id, title, layout, commands.filter { COMMAND_NAME.matches(it) }, permission, refresh, filler, items, references), issues)
    }

    private fun parseLayout(
        root: JsonNode,
        issue: (String, String) -> Unit,
    ): GuiLayout? {
        val type = root.text("type")?.lowercase() ?: "chest"
        return when (type) {
            "chest" -> {
                val rows = root.get("rows")
                if (rows == null || !rows.isInt || rows.asInt() !in 1..6) {
                    issue("rows", "required: a whole number from 1 to 6")
                    null
                } else {
                    GuiLayout.chest(rows.asInt())
                }
            }
            "hopper" -> GuiLayout.Hopper
            "dispenser" -> GuiLayout.Dispenser
            else -> {
                issue("type", "must be chest, hopper or dispenser (got '$type')")
                null
            }
        }
    }

    /** `slot: 4`, `slots: [0, 1, "3-5"]` or `slot: "0-8"`. */
    private fun parseSlots(
        node: JsonNode,
        path: String,
        size: Int,
        issue: (String, String) -> Unit,
    ): List<Int> {
        val entries = (node.get("slots")?.let { if (it.isArray) it.toList() else listOf(it) } ?: emptyList()) + listOfNotNull(node.get("slot"))
        if (entries.isEmpty()) {
            issue(path, "needs a 'slot' or 'slots'")
            return emptyList()
        }
        val slots = linkedSetOf<Int>()
        for (entry in entries) {
            val raw = entry.asText()
            val range = RANGE.matchEntire(raw.trim())
            val values =
                when {
                    entry.isInt -> listOf(entry.asInt())
                    range != null -> (range.groupValues[1].toInt()..range.groupValues[2].toInt()).toList()
                    else -> {
                        issue("$path.slot", "'$raw' is not a slot number or range like 0-8")
                        continue
                    }
                }
            values.forEach { if (it in 0 until size) slots += it else issue("$path.slot", "slot $it is outside 0..${size - 1}") }
        }
        return slots.toList()
    }

    private fun parseItem(
        path: String,
        node: JsonNode,
        references: MutableSet<String>,
        requireMaterial: Boolean,
        issue: (String, String) -> Unit,
    ): ItemSpec? {
        if (!node.isObject) {
            issue(path, "must be a mapping")
            return null
        }
        val material = node.text("material")?.lowercase()
        if (requireMaterial && material == null) {
            issue("$path.material", "required (e.g. diamond or minecraft:diamond)")
            return null
        }
        if (material != null && !MATERIAL.matches(material)) {
            issue("$path.material", "'$material' is not a valid item id")
            return null
        }
        val amount = node.get("amount")?.let { if (it.isInt && it.asInt() in 1..99) it.asInt() else null.also { issue("$path.amount", "must be 1..99") } }

        val enchantments = mutableMapOf<String, Int>()
        node.get("enchantments")?.fields()?.forEach { (name, level) ->
            if (level.isInt && level.asInt() in 1..255) enchantments[name] = level.asInt() else issue("$path.enchantments.$name", "level must be 1..255")
        }
        val components = mutableMapOf<String, GuiData>()
        node.get("components")?.fields()?.forEach { (name, value) -> toData(value)?.let { components[name] = it } ?: issue("$path.components.$name", "unsupported value") }

        val overrides = mutableMapOf<GuiPlatform, ItemSpec>()
        node.get("platform")?.fields()?.forEach { (name, override) ->
            val platform = GuiPlatform.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (platform == null) {
                issue("$path.platform.$name", "unknown platform (java or bedrock)")
            } else {
                parseItem("$path.platform.$name", override, references, requireMaterial = false, issue)?.let { overrides[platform] = it }
            }
        }

        val clicks = mutableListOf<Pair<Set<GuiClickType>, List<ActionTemplate>>>()
        node.get("actions")?.let { parseActions("$path.actions", it, references, issue) }?.takeIf { it.isNotEmpty() }?.let { clicks += GuiClickType.PRIMARY to it }
        node.get("on")?.fields()?.forEach { (name, list) ->
            val type = CLICK_NAMES[name.lowercase()]
            if (type == null) {
                issue("$path.on.$name", "unknown click type; use one of ${CLICK_NAMES.keys.joinToString()}")
            } else {
                parseActions("$path.on.$name", list, references, issue).takeIf { it.isNotEmpty() }?.let { clicks += setOf(type) to it }
            }
        }

        return ItemSpec(
            material = material,
            amount = amount,
            name = node.text("name"),
            lore = node.get("lore")?.let { node.stringList("lore") },
            glow = node.get("glow")?.takeIf { it.isBoolean }?.asBoolean(),
            enchantments = enchantments.takeIf { node.has("enchantments") },
            customModelData = node.get("custom_model_data")?.takeIf { it.isInt }?.asInt(),
            customModelStrings = node.get("custom_model_strings")?.let { node.stringList("custom_model_strings") },
            itemModel = node.text("item_model"),
            hideTooltip = node.get("hide_tooltip")?.takeIf { it.isBoolean }?.asBoolean(),
            hideTooltipComponents = node.get("hide_tooltip_components")?.let { node.stringList("hide_tooltip_components") },
            components = components.takeIf { node.has("components") },
            platformOverrides = overrides,
            clicks = clicks,
        )
    }

    private fun parseActions(
        path: String,
        node: JsonNode,
        references: MutableSet<String>,
        issue: (String, String) -> Unit,
    ): List<ActionTemplate> {
        val entries = if (node.isArray) node.toList() else listOf(node)
        return entries.mapIndexedNotNull { index, entry ->
            val where = "$path[$index]"
            try {
                val template = parseAction(entry, references) { issue(where, it) } ?: return@mapIndexedNotNull null
                template.build(Placeholders.validation) // fail now, not when a player clicks
                template
            } catch (e: IllegalArgumentException) {
                issue(where, e.message ?: "invalid action")
                null
            }
        }
    }

    private fun parseAction(
        entry: JsonNode,
        references: MutableSet<String>,
        problem: (String) -> Unit,
    ): ActionTemplate? {
        if (entry.isTextual) {
            return when (entry.asText().lowercase()) {
                "close" -> ActionTemplate { GuiAction.Close }
                "refresh" -> ActionTemplate { GuiAction.Refresh }
                else -> {
                    problem("unknown action '${entry.asText()}'; write it as a mapping like server_command: spawn")
                    null
                }
            }
        }
        if (!entry.isObject || entry.size() != 1) {
            problem("an action is a mapping with exactly one key, like server_command: spawn")
            return null
        }
        val (key, value) = entry.fields().next()
        fun str(field: String): String? = if (value.isTextual) (if (field == "command") value.asText() else null) else value.text(field)
        return when (key.lowercase()) {
            "close" -> ActionTemplate { GuiAction.Close }
            "refresh" -> ActionTemplate { GuiAction.Refresh }
            "server_command" -> {
                val command = str("command") ?: return null.also { problem("server_command needs a command") }
                val server = value.takeIf { it.isObject }?.text("server")
                ActionTemplate { s -> GuiAction.ExecuteServerCommand(s(command), server) }
            }
            "player_command" -> {
                val command = str("command") ?: return null.also { problem("player_command needs a command") }
                ActionTemplate { s -> GuiAction.ExecutePlayerCommand(s(command)) }
            }
            "proxy_command" -> {
                val command = str("command") ?: return null.also { problem("proxy_command needs a command") }
                val executor =
                    when (value.takeIf { it.isObject }?.text("as")?.lowercase()) {
                        null, "player" -> ProxyCommandExecutor.PLAYER
                        "console" -> ProxyCommandExecutor.CONSOLE
                        else -> return null.also { problem("'as' must be player or console") }
                    }
                ActionTemplate { s -> GuiAction.ExecuteProxyCommand(s(command), executor) }
            }
            "connect" -> {
                val server = if (value.isTextual) value.asText() else value.text("server")
                if (server.isNullOrBlank()) return null.also { problem("connect needs a server name") }
                ActionTemplate { s -> GuiAction.ConnectToServer(s(server)) }
            }
            "message" -> {
                val text = if (value.isTextual) value.asText() else value.text("text")
                if (text.isNullOrBlank()) return null.also { problem("message needs text") }
                ActionTemplate { s -> GuiAction.SendMessage(s(text)) }
            }
            "open_menu" -> {
                val menu = if (value.isTextual) value.asText() else value.text("menu")
                if (menu.isNullOrBlank()) return null.also { problem("open_menu needs a menu id") }
                references += menu
                ActionTemplate { GuiAction.Run { event -> openMenu(menu, event.player) } }
            }
            "plugin_message" -> {
                val channel = value.text("channel")
                val data = value.text("text") ?: ""
                if (channel == null) return null.also { problem("plugin_message needs a channel") }
                ActionTemplate { s -> GuiAction.SendPluginMessage(channel, s(data).toByteArray(Charsets.UTF_8)) }
            }
            "clickable_message" -> {
                val text = value.text("text") ?: return null.also { problem("clickable_message needs text") }
                val click: ClientClick =
                    value.text("suggest_command")?.let { ClientClick.SuggestCommand(it) }
                        ?: value.text("run_command")?.let { ClientClick.RunCommand(it) }
                        ?: value.text("open_url")?.let { ClientClick.OpenUrl(it) }
                        ?: value.text("copy_to_clipboard")?.let { ClientClick.CopyToClipboard(it) }
                        ?: return null.also { problem("clickable_message needs one of suggest_command, run_command, open_url, copy_to_clipboard") }
                ActionTemplate { s -> GuiAction.SendClickableMessage(s(text), click) }
            }
            else -> {
                problem("unknown action '$key'")
                null
            }
        }
    }

    private fun toData(node: JsonNode): GuiData? =
        when {
            node.isTextual -> GuiData.text(node.asText())
            node.isBoolean -> GuiData.bool(node.asBoolean())
            node.isInt -> GuiData.int(node.asInt())
            node.isNumber -> GuiData.double(node.asDouble())
            node.isArray -> node.map { toData(it) ?: return null }.let { GuiData.ListOf(it) }
            node.isObject -> GuiData.Compound(node.fields().asSequence().associate { (k, v) -> k to (toData(v) ?: return null) })
            else -> null
        }

    private fun JsonNode.text(field: String): String? = get(field)?.takeIf { it.isValueNode && !it.isNull }?.asText()

    private fun JsonNode.stringList(field: String): List<String> =
        get(field)?.let { if (it.isArray) it.map { e -> e.asText() } else listOf(it.asText()) } ?: emptyList()

    companion object {
        private val COMMAND_NAME = Regex("[a-z0-9_-]+")
        private val MATERIAL = Regex("([a-z0-9_.-]+:)?[a-z0-9_./-]+")
        private val RANGE = Regex("(\\d+)\\s*-\\s*(\\d+)")

        /** YAML names for the click types an item may react to. */
        val CLICK_NAMES: Map<String, GuiClickType> =
            mapOf(
                "left" to GuiClickType.LEFT,
                "right" to GuiClickType.RIGHT,
                "shift_left" to GuiClickType.SHIFT_LEFT,
                "shift_right" to GuiClickType.SHIFT_RIGHT,
                "middle" to GuiClickType.MIDDLE,
                "number_key" to GuiClickType.NUMBER_KEY,
                "offhand" to GuiClickType.OFFHAND_SWAP,
                "drop" to GuiClickType.DROP,
                "control_drop" to GuiClickType.CONTROL_DROP,
                "double_click" to GuiClickType.DOUBLE_CLICK,
            )
    }
}

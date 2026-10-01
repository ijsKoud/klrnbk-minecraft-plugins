// package nl.klrnbk.minecraft.plugins.pkgs.i18n
//
// import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
// import org.bukkit.inventory.ItemStack
// import org.bukkit.inventory.meta.ItemMeta
// import java.util.Locale
//
// /**
// * Item display text does NOT support Adventure's automatic
// * [net.kyori.adventure.translation.GlobalTranslator] resolution — see the
// * doc comment on [TranslationService] for why. These always bake in a
// * concrete, already-rendered translation for one specific [locale] —
// * there's no way to make a single `ItemStack` show different text to
// * different viewers; give each viewer their own `ItemStack` built with
// * their own locale if that's what you need.
// */
// fun ItemMeta.applyTranslatedName(
//    translations: TranslationService,
//    key: String,
//    locale: Locale,
//    vararg resolvers: TagResolver,
// ) {
//    displayName(translations.component(key, locale, *resolvers))
// }
//
// fun ItemMeta.applyTranslatedLore(
//    translations: TranslationService,
//    key: String,
//    locale: Locale,
//    vararg resolvers: TagResolver,
// ) {
//    lore(translations.lore(key, locale, *resolvers))
// }
//
// /**
// * Convenience wrapper applying both name and lore to an [ItemStack] in one
// * call. Pass `null` for either key to skip it (e.g. an item with lore but
// * no custom name, or vice versa).
// */
// fun ItemStack.applyTranslations(
//    translations: TranslationService,
//    nameKey: String?,
//    loreKey: String?,
//    locale: Locale,
//    vararg resolvers: TagResolver,
// ) {
//    val meta = itemMeta ?: return
//    if (nameKey != null) meta.applyTranslatedName(translations, nameKey, locale, *resolvers)
//    if (loreKey != null) meta.applyTranslatedLore(translations, loreKey, locale, *resolvers)
//    itemMeta = meta
// }

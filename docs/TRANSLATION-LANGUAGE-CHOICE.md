# Translation language, script and region choices

Through alpha.17, the translation picker keyed its entries by
`Locale.getDisplayLanguage()` and sent only `Locale.getLanguage()`. Installed
locales that shared a language display name overwrote one another, and the
selected request could not distinguish script or region. For example, Chinese
locales could collapse to the same `zh` request. This is a source finding, not
an observed phone translation or a claim about model quality.

Alpha.18 source keeps distinct language tags from the platform's installed
locales and displays the localized locale name with its tag. It deduplicates
equivalent tags, excludes missing or undetermined languages and offers the exact
chosen tag to the existing Sol translation request. Existing input, explicit
effort choice, editor-anchor and privacy gates remain. The list may be longer
than the old collapsed list and is not yet searchable.

Public synthetic checks cover Simplified and Traditional Chinese, Brazilian
and European Portuguese, English regions, duplicate tags, missing locales and
deterministic ordering. The APK checker requires the selected clone's minimum
Android API to support `Locale.toLanguageTag()` and checks source references;
these are not Android UI or model-quality tests. Hosted verification is pending.
The set of installed locales is not a claim that Sol supports every locale or
will preserve the requested dialect. Actual selection, output quality and
editor behavior require the existing authorized device and model acceptance.

Android documents [`Locale.getAvailableLocales()` and `toLanguageTag()`](https://developer.android.com/reference/java/util/Locale)
and notes that a well-formed tag is not necessarily a registered one. No model
or phone operation was performed for this change.

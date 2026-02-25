# Extension Builder Runtime Audit

## Pipeline observed in app
- Install source of truth is `installed_extension` table plus script files in `/files/src/scripts/<fileName>.lua` written via `FileExtensionDataSource`.
- Runtime loading goes through `ExtensionEntitiesRepository.get()` -> `FileExtensionDataSource.loadExtension()` -> `GenericExtensionEntity.asIEntity()` -> `LuaExtension(script, fileName)`.
- Browse list composition comes from `ExtensionsRepository.loadBrowseExtensions()`, now combining repository entries with installed-only entries.

## Lua contract markers used by runtime
Derived from runtime invocation sites (`GetCatalogueListingDataUseCase`, `GetRemoteNovelUseCase`, install path):
- Metadata fields consumed: `id`, `name`, `imageURL`, `lang`, `version`, `hasSearch`, `chapterType`, `listings`, `startIndex`.
- Functions consumed: `parseNovel(novelURL, loadChapters)`, `getPassage(chapterURL)`, and optional `search(data)` when `hasSearch=true`.
- Listing contract: `listings = { { name, isIncrementing, getListing = function(data) ... } }`.
- Helper globals expected by generated scaffold: `GET`, `Document`, `NovelInfo`, `NovelStatus`, `ChapterType`, `EncodeUrl`, `StartsWith`, `PAGE_INDEX`, `QUERY_INDEX`.

## Builder alignment
- Generator now emits numeric runtime `id` (`runtimeId`) for compatibility with integer `formatterID` used throughout Kotlin storage and browse/runtime APIs.
- Install flow writes using same repositories/filesystem path as normal installs and updates `installed_extension` rows.

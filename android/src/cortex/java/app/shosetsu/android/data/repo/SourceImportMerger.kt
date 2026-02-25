package app.shosetsu.android.data.repo

import app.shosetsu.android.domain.model.Source
import java.util.UUID

data class SourceMergeResult(
    val merged: List<Source>,
    val replaced: Int,
    val added: Int
)

object SourceImportMerger {
    fun merge(existing: List<Source>, imported: List<Source>): SourceMergeResult {
        val existingByComposite = existing.associateBy { compositeKey(it) }
        val existingById = existing.associateBy { it.id }
        val merged = existing.toMutableList()
        var replaced = 0
        var added = 0

        imported.forEach { incoming ->
            val idxById = merged.indexOfFirst { it.id == incoming.id }
            val idxByComposite = merged.indexOfFirst { compositeKey(it) == compositeKey(incoming) }
            val replacementIndex = when {
                incoming.id in existingById -> idxById
                compositeKey(incoming) in existingByComposite -> idxByComposite
                else -> -1
            }
            if (replacementIndex >= 0) {
                merged[replacementIndex] = incoming
                replaced++
            } else {
                merged += incoming.copy(id = incoming.id.ifBlank { UUID.randomUUID().toString() })
                added++
            }
        }
        return SourceMergeResult(merged, replaced, added)
    }

    private fun compositeKey(source: Source): String = "${source.name.lowercase()}|${source.baseUrl.lowercase()}|${source.type.name}"
}

package com.sopa.viva_automotive.feature.voice.data.embedding

import android.util.Log
import com.sopa.viva_automotive.core.common.coroutines.IoDispatcher
import com.sopa.viva_automotive.feature.voice.domain.embedding.CosineSimilarity
import com.sopa.viva_automotive.feature.voice.domain.embedding.SemanticIntentMatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class OnnxEmbeddingIntentMatcher @Inject constructor(
    private val encoder: OnnxEmbeddingEncoder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SemanticIntentMatcher {

    private val mutex = Mutex()

    @Volatile
    private var index: List<IndexedExemplar>? = null

    override suspend fun warmUp() {
        ensureIndex()
    }

    override suspend fun bestIntent(utterance: String): String? {
        // No lowercasing: the model is `-cased`, and folding case throws away a
        // signal it was trained to use. The tokenizer still normalises whitespace.
        val query = utterance.trim()
        if (query.isBlank()) return null

        val queryVec = encoder.embed(query) ?: return null
        val prototypes = ensureIndex() ?: return null

        var bestIntent: String? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (item in prototypes) {
            val score = CosineSimilarity.score(queryVec, item.vector)
            if (score > bestScore) {
                bestScore = score
                bestIntent = item.intentType
            }
        }

        Log.d(TAG, "Semantic match \"$query\" → $bestIntent (cos=$bestScore)")
        if (bestScore < MIN_COSINE) return null
        return bestIntent
    }

    private suspend fun ensureIndex(): List<IndexedExemplar>? = mutex.withLock {
        index?.let { return it }
        if (!encoder.ensureReady()) return null
        withContext(ioDispatcher) {
            val built = ArrayList<IndexedExemplar>(IntentExemplarCatalog.exemplars.size)
            for (ex in IntentExemplarCatalog.exemplars) {
                val vec = encoder.embed(ex.phrase)
                if (vec != null) {
                    built.add(IndexedExemplar(ex.intentType, ex.phrase, vec))
                }
            }
            if (built.isEmpty()) {
                Log.e(TAG, "Failed to build intent embedding index")
                null
            } else {
                Log.i(TAG, "Built intent embedding index with ${built.size} exemplars")
                index = built
                built
            }
        }
    }

    private data class IndexedExemplar(
        val intentType: String,
        val phrase: String,
        val vector: FloatArray,
    )

    private companion object {
        const val TAG = "EmbedIntent"
        // Recalibrated for distiluse-base-multilingual-cased-v2 on 2026-08-06
        // with the production tokenizer, ONNX graph and pooling: supported
        // semantic corpus pairs were >= 0.849, while negative/unsupported
        // controls peaked at 0.797. A false accept becomes a vehicle command;
        // a false reject only becomes a clarifying question.
        const val MIN_COSINE = 0.82f
    }
}

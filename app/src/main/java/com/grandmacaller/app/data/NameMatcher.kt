package com.grandmacaller.app.data

/**
 * Matches a transcribed phrase against each relative's spokenNames.
 *
 * Previous version returned the FIRST relative it found any match for,
 * scanning relatives in list order -- so it never actually compared
 * candidates against each other. Combined with speech-recognition noise,
 * that meant a garbled transcript could latch onto the wrong person's
 * short alias via a lenient edit-distance check and dial them with no
 * one ever comparing that guess against anything better.
 *
 * This version:
 *  - Scores EVERY relative/alias pair, across ALL relatives, before
 *    deciding anything.
 *  - Requires a real minimum confidence to accept a match at all -- no
 *    "best of a bad bunch" guessing.
 *  - Refuses to pick when two different relatives score similarly close
 *    (ambiguous), rather than arbitrarily preferring whichever came first.
 */
object NameMatcher {

    sealed class MatchResult {
        data class Found(val relative: Relative) : MatchResult()
        object NoConfidentMatch : MatchResult()
    }

    private const val MIN_ALIAS_LENGTH_FOR_FUZZY = 2
    private const val AMBIGUITY_MARGIN = 1 // if top two scores differ by less than this, treat as ambiguous

    fun findBestMatch(transcript: String, relatives: List<Relative>): MatchResult {
        val cleaned = transcript.trim().lowercase()
        if (cleaned.isEmpty() || relatives.isEmpty()) return MatchResult.NoConfidentMatch

        data class Candidate(val relative: Relative, val score: Int) // lower is better; 0 = exact/contains

        val candidates = mutableListOf<Candidate>()

        for (relative in relatives) {
            for (spoken in relative.spokenNames) {
                val target = spoken.trim().lowercase()
                if (target.isEmpty()) continue

                // Direct containment is the strongest signal -- score it, and
                // penalize very short aliases since they're more likely to
                // appear by coincidence inside an unrelated transcript.
                if (cleaned.contains(target)) {
                    val score = if (target.length <= 1) 3 else 0
                    candidates.add(Candidate(relative, score))
                    continue
                }

                // Fuzzy fallback: only for aliases long enough that a small
                // edit distance is actually meaningful.
                if (target.length < MIN_ALIAS_LENGTH_FOR_FUZZY) continue

                for (word in cleaned.split(" ")) {
                    if (word.isEmpty()) continue
                    val dist = levenshtein(word, target)
                    val threshold = (target.length / 3).coerceAtLeast(1)
                    if (dist <= threshold) {
                        candidates.add(Candidate(relative, score = dist + 1))
                    }
                }
            }
        }

        if (candidates.isEmpty()) return MatchResult.NoConfidentMatch

        // Best (lowest) score per distinct relative, so one relative with
        // multiple weak alias hits doesn't drown out a genuinely close call.
        val bestPerRelative = candidates
            .groupBy { it.relative.id }
            .map { (_, group) -> group.minByOrNull { it.score }!! }
            .sortedBy { it.score }

        val top = bestPerRelative[0]
        val runnerUp = bestPerRelative.getOrNull(1)

        // Two different relatives scored within the ambiguity margin of each
        // other -- don't guess, ask again instead of possibly dialing the
        // wrong person.
        if (runnerUp != null && (runnerUp.score - top.score) < AMBIGUITY_MARGIN) {
            return MatchResult.NoConfidentMatch
        }

        return MatchResult.Found(top.relative)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}

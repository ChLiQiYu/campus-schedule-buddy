package com.fjnu.schedule.util

import com.fjnu.schedule.data.KnowledgePointEntity
import com.fjnu.schedule.model.KnowledgeProgress
import com.fjnu.schedule.model.KnowledgeStatus
import kotlin.math.roundToInt

object KnowledgeProgressCalculator {
    private const val KEY_POINT_WEIGHT = 1.5

    fun calculate(points: List<KnowledgePointEntity>): KnowledgeProgress {
        if (points.isEmpty()) {
            return KnowledgeProgress(
                totalCount = 0,
                masteredCount = 0,
                learningCount = 0,
                notStartedCount = 0,
                stuckCount = 0,
                percent = 0
            )
        }
        var mastered = 0
        var learning = 0
        var notStarted = 0
        var stuck = 0
        var weightedScore = 0.0
        var weightSum = 0.0
        points.forEach { point ->
            val status = KnowledgeStatus.fromLevel(point.masteryLevel)
            when (status) {
                KnowledgeStatus.MASTERED -> mastered += 1
                KnowledgeStatus.LEARNING -> learning += 1
                KnowledgeStatus.NOT_STARTED -> notStarted += 1
                KnowledgeStatus.STUCK -> stuck += 1
            }
            val weight = if (point.isKeyPoint) KEY_POINT_WEIGHT else 1.0
            weightedScore += status.level * weight
            weightSum += weight
        }
        val percent = if (weightSum <= 0.0) {
            0
        } else {
            ((weightedScore / (100.0 * weightSum)) * 100.0).roundToInt().coerceIn(0, 100)
        }
        return KnowledgeProgress(
            totalCount = points.size,
            masteredCount = mastered,
            learningCount = learning,
            notStartedCount = notStarted,
            stuckCount = stuck,
            percent = percent
        )
    }
}

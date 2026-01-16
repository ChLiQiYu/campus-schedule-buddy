package com.fjnu.schedule

import com.fjnu.schedule.data.KnowledgePointEntity
import com.fjnu.schedule.util.KnowledgeProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeProgressCalculatorTest {
    @Test
    fun calculate_returnsWeightedPercentAndCounts() {
        val points = listOf(
            KnowledgePointEntity(
                id = 1,
                courseId = 1,
                title = "A",
                masteryLevel = 0,
                isKeyPoint = false,
                lastReviewedAt = null
            ),
            KnowledgePointEntity(
                id = 2,
                courseId = 1,
                title = "B",
                masteryLevel = 50,
                isKeyPoint = true,
                lastReviewedAt = null
            ),
            KnowledgePointEntity(
                id = 3,
                courseId = 1,
                title = "C",
                masteryLevel = 100,
                isKeyPoint = false,
                lastReviewedAt = null
            )
        )

        val progress = KnowledgeProgressCalculator.calculate(points)

        assertEquals(3, progress.totalCount)
        assertEquals(1, progress.notStartedCount)
        assertEquals(1, progress.learningCount)
        assertEquals(1, progress.masteredCount)
        assertEquals(0, progress.stuckCount)
        assertEquals(50, progress.percent)
    }
}

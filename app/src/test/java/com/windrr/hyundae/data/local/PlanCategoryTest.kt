package com.windrr.hyundae.data.local

import com.windrr.hyundae.data.local.entity.PlanCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanCategoryTest {

    @Test
    fun fromCode_knownCodes() {
        assertEquals(PlanCategory.TRAVEL, PlanCategory.fromCode("TRAVEL"))
        assertEquals(PlanCategory.LEISURE, PlanCategory.fromCode("LEISURE"))
        assertEquals(PlanCategory.MOVING, PlanCategory.fromCode("MOVING"))
    }

    @Test
    fun fromCode_nullOrUnknown_isTravel() {
        assertEquals(PlanCategory.TRAVEL, PlanCategory.fromCode(null))
        assertEquals(PlanCategory.TRAVEL, PlanCategory.fromCode(""))
        assertEquals(PlanCategory.TRAVEL, PlanCategory.fromCode("OUTING"))
    }
}

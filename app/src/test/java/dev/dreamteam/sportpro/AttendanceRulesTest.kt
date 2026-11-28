package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.AttendanceRecord
import dev.dreamteam.sportpro.data.model.AttendanceStatus
import dev.dreamteam.sportpro.data.model.canMarkAttendance
import dev.dreamteam.sportpro.data.model.recordsOfFormerMembers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class AttendanceRulesTest {

    private val now = Date(1_800_000_000_000L)

    @Test
    fun attendance_onlyOnceTheSessionStarted() {
        assertTrue(canMarkAttendance(Date(now.time - 60_000), now))   // empezó hace 1 min
        assertTrue(canMarkAttendance(now, now))                       // empieza justo ahora
        assertFalse(canMarkAttendance(Date(now.time + 60_000), now))  // empieza en 1 min
        assertFalse(canMarkAttendance(null, now))
    }

    @Test
    fun formerMembers_areRecordsWhosePlayerLeftTheTeam() {
        val stay = AttendanceRecord(id = "s_a", playerId = "a", playerName = "Ana")
        val leftB = AttendanceRecord(id = "s_b", playerId = "b", playerName = "Bruno")
        val leftC = AttendanceRecord(id = "s_c", playerId = "c", playerName = "carla")
        assertEquals(listOf(leftB, leftC), recordsOfFormerMembers(listOf(leftC, stay, leftB), setOf("a")))
        assertEquals(emptyList<AttendanceRecord>(), recordsOfFormerMembers(listOf(stay), setOf("a")))
    }

    @Test
    fun corrected_onlyWhenThereIsALastChange() {
        assertFalse(AttendanceRecord(status = AttendanceStatus.PRESENTE).wasCorrected)
        assertTrue(AttendanceRecord(status = AttendanceStatus.TARDANZA, lastChangeId = "c1").wasCorrected)
    }
}

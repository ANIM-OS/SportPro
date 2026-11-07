package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.TRYOUT_CANCELLED
import dev.dreamteam.sportpro.data.model.TryoutNotice
import dev.dreamteam.sportpro.domain.tryouts.TryoutFilter
import dev.dreamteam.sportpro.domain.tryouts.TryoutState
import dev.dreamteam.sportpro.domain.tryouts.datePickerMillisToDate
import dev.dreamteam.sportpro.domain.tryouts.endOfDayMillis
import dev.dreamteam.sportpro.domain.tryouts.startOfDayMillis
import dev.dreamteam.sportpro.domain.tryouts.state
import dev.dreamteam.sportpro.domain.tryouts.toDatePickerMillis
import dev.dreamteam.sportpro.domain.tryouts.validateTryout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TryoutRulesTest {
    private val lima = ZoneId.of("America/Lima")
    private val day = LocalDate.of(2026, 11, 20)

    @Test
    fun deadline_isEndOfThatDay() {
        val notice = TryoutNotice(deadlineMillis = day.endOfDayMillis(lima))
        assertEquals(TryoutState.VIGENTE, notice.state(day.atTime(23, 59).atZone(lima).toInstant().toEpochMilli()))
        assertEquals(TryoutState.VENCIDO, notice.state(day.plusDays(1).startOfDayMillis(lima)))
    }

    @Test
    fun noDeadline_staysCurrent() {
        assertEquals(TryoutState.VIGENTE, TryoutNotice().state(Long.MAX_VALUE))
    }

    @Test
    fun cancelled_isNeverCurrent() {
        val notice = TryoutNotice(status = TRYOUT_CANCELLED, deadlineMillis = day.endOfDayMillis(lima))
        assertEquals(TryoutState.CANCELADO, notice.state(day.startOfDayMillis(lima)))
    }

    @Test
    fun categoryAndPositionFilters_includeNoticesOpenToAll() {
        val filter = TryoutFilter(category = "SUB-15", position = "Portero")
        assertTrue(filter.matches(TryoutNotice(category = "SUB-15", position = "Portero"), lima))
        assertTrue(filter.matches(TryoutNotice(), lima))
        assertFalse(filter.matches(TryoutNotice(category = "SUB-17"), lima))
        assertFalse(filter.matches(TryoutNotice(position = "Delantero"), lima))
    }

    @Test
    fun dateFilter_usesTryoutDayAndExcludesNoticesWithoutDate() {
        val filter = TryoutFilter(fromDate = day, toDate = day.plusDays(2))
        assertTrue(filter.matches(TryoutNotice(tryoutDateMillis = day.startOfDayMillis(lima)), lima))
        assertTrue(filter.matches(TryoutNotice(tryoutDateMillis = day.plusDays(2).startOfDayMillis(lima)), lima))
        assertFalse(filter.matches(TryoutNotice(tryoutDateMillis = day.plusDays(3).startOfDayMillis(lima)), lima))
        assertFalse(filter.matches(TryoutNotice(), lima))
    }

    @Test
    fun locationFilter_ignoresCaseAndAccents() {
        val notice = TryoutNotice(location = "Estadio Municipal de Chorrillos, Lima")
        assertTrue(TryoutFilter(location = "chorríllos").matches(notice, lima))
        assertTrue(TryoutFilter(location = "  ESTADIO ").matches(notice, lima))
        assertFalse(TryoutFilter(location = "Callao").matches(notice, lima))
        assertFalse(TryoutFilter(location = "Lima").matches(TryoutNotice(), lima))
    }

    @Test
    fun validation_requiresTitleAndDescriptionOnly() {
        val ok = validateTryout("Prueba Sub-15", "Traer chimpunes", "", null, null, day)
        assertFalse(ok.hasErrors)
        val empty = validateTryout(" ", "", "", null, null, day)
        assertNotNull(empty.title)
        assertNotNull(empty.description)
        assertNull(empty.location)
    }

    @Test
    fun validation_checksDates() {
        assertNotNull(validateTryout("t", "d", "", null, day.minusDays(1), day).deadline)
        assertNotNull(validateTryout("t", "d", "", day.minusDays(1), null, day).tryoutDate)
        assertNotNull(validateTryout("t", "d", "", day.plusDays(1), day.plusDays(2), day).deadline)
        assertFalse(validateTryout("t", "d", "", day.plusDays(2), day.plusDays(2), day).hasErrors)
        assertFalse(validateTryout("t", "d", "", null, day, day).hasErrors)
    }

    @Test
    fun validation_limitsLengths() {
        val errors = validateTryout("x".repeat(101), "y".repeat(2001), "z".repeat(121), null, null, day)
        assertNotNull(errors.title)
        assertNotNull(errors.description)
        assertNotNull(errors.location)
    }

    @Test
    fun datePickerConversion_roundTrips() {
        assertEquals(day, datePickerMillisToDate(day.toDatePickerMillis()))
    }
}

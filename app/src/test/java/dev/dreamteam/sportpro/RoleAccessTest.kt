package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.ui.auth.AppRole
import dev.dreamteam.sportpro.ui.auth.RoleAccess
import dev.dreamteam.sportpro.ui.auth.RoleCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleAccessTest {
    private val coachCapabilities = listOf(
        RoleCapability.CREATE_TEAM,
        RoleCapability.MANAGE_EVENT_CATALOG,
        RoleCapability.REGISTER_EVENTS,
        RoleCapability.EDIT_EVENTS,
        RoleCapability.PUBLISH_TRYOUT
    )

    @Test
    fun unapprovedCoach_hasNoCoachCapabilities() {
        (coachCapabilities + RoleCapability.CREATE_POST).forEach {
            assertFalse(it.name, RoleAccess.can(setOf(AppRole.COACH), it, coachApproved = false))
        }
    }

    @Test
    fun approvedCoach_hasCoachCapabilities() {
        (coachCapabilities + RoleCapability.CREATE_POST).forEach {
            assertTrue(it.name, RoleAccess.can(setOf(AppRole.COACH), it, coachApproved = true))
        }
    }

    @Test
    fun approvalWithoutCoachRole_grantsNothing() {
        coachCapabilities.forEach {
            assertFalse(it.name, RoleAccess.can(setOf(AppRole.PLAYER), it, coachApproved = true))
        }
    }

    @Test
    fun unapprovedCoachWhoIsPlayer_keepsPlayerCapabilities() {
        val roles = setOf(AppRole.PLAYER, AppRole.COACH)
        assertTrue(RoleAccess.can(roles, RoleCapability.CREATE_POST, coachApproved = false))
        assertTrue(RoleAccess.can(roles, RoleCapability.JOIN_WITH_CODE, coachApproved = false))
        assertFalse(RoleAccess.can(roles, RoleCapability.CREATE_TEAM, coachApproved = false))
    }

    @Test
    fun unapprovedCoach_canStillComment() {
        assertTrue(RoleAccess.can(setOf(AppRole.COACH), RoleCapability.COMMENT_POST, coachApproved = false))
    }
}

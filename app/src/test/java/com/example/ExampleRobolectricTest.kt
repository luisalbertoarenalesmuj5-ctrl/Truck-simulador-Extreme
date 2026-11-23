package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.CameraMode
import com.example.model.DriverProfile
import com.example.model.LiveryStyle
import com.example.model.RouteCatalog
import com.example.model.TruckCatalog
import com.example.sim.ControlInputs
import com.example.sim.TruckPhysicsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Truck Simulator Extreme", appName)
    }

    @Test
    fun `catalog has 8 trucks and 7 routes including free roam`() {
        assertEquals(8, TruckCatalog.trucks.size)
        assertEquals(7, RouteCatalog.routes.size)
        assertTrue(RouteCatalog.byId("free_roam_world").isFreeRoam)
        assertEquals(4, CameraMode.entries.size)
    }

    @Test
    fun `profile visual config resolves paints and accessories`() {
        val truck = TruckCatalog.byId("colossus_w900")
        val profile = DriverProfile(
            selectedTruckId = truck.id,
            paintColorHex = "#D97706",
            liveryStyle = LiveryStyle.RACING.name,
            bullbarInstalled = true,
            roofLightbarInstalled = true,
            aeroSpoilerInstalled = false,
            chromeExhaustInstalled = true
        )
        val visual = profile.toVisualConfig(truck)
        assertEquals("#D97706", visual.paintHex)
        assertEquals(LiveryStyle.RACING, visual.liveryStyle)
        assertTrue(visual.bullbarInstalled)
        assertTrue(visual.roofLightbarInstalled)
        assertTrue(visual.chromeExhaustInstalled)
    }

    @Test
    fun `physics steps driving wipers and on-foot mode cleanly`() {
        val truck = TruckCatalog.byId("nordic_fh540")
        val route = RouteCatalog.byId("free_roam_world")
        val engine = TruckPhysicsEngine(truck, route, initialDamagePercent = 12)

        assertEquals(12, engine.readTelemetry().damagePercent)
        assertEquals(1, engine.cycleWiperMode())
        assertTrue(engine.exitTruckToFoot())
        assertTrue(engine.readTelemetry().driverOnFoot)
        assertTrue(engine.boardTruckFromFoot())

        engine.inputs = ControlInputs(throttle = 0.8f, headlightsOn = true)
        repeat(40) { engine.step(0.016f, paused = false) }
        assertTrue(engine.readTelemetry().speedKmh > 0)
    }

    @Test
    fun `driver profile level and xp progression scales accurately`() {
        assertEquals(1L, DriverProfile.levelForXp(0L))
        assertEquals(1L, DriverProfile.levelForXp(499L))
        assertEquals(2L, DriverProfile.levelForXp(500L))
        assertEquals(4L, DriverProfile.levelForXp(1750L))

        val profile = DriverProfile(
            driverName = "Conductor Test",
            credits = 30000L,
            xp = 1250L,
            level = 3L,
            deliveriesCompleted = 2L,
            totalDistanceKm = 80.0
        )
        assertEquals(3L, profile.effectiveLevel())
        assertEquals(250L, profile.xpIntoCurrentLevel())
        assertEquals(250L, profile.xpRemainingToNextLevel())
        assertEquals(0.5f, profile.levelProgressFraction(), 0.001f)
        assertEquals("Transportista Experto", profile.driverRankTitle())

        assertTrue(com.example.model.CareerCatalog.milestones.first { it.id == "starter_license" }.isUnlocked(profile))
        assertTrue(com.example.model.CareerCatalog.milestones.first { it.id == "first_haul" }.isUnlocked(profile))
        assertTrue(com.example.model.CareerCatalog.milestones.first { it.id == "road_50km" }.isUnlocked(profile))
        assertTrue(!com.example.model.CareerCatalog.milestones.first { it.id == "master_250km" }.isUnlocked(profile))
    }

    @Test
    fun `contract generator produces all difficulty tiers and valid route specs`() {
        val contracts = com.example.model.ContractGenerator.generateBatch(
            userId = "uid_1",
            driverLevel = 3L,
            count = 5,
            random = kotlin.random.Random(42)
        )
        assertEquals(5, contracts.size)
        val difficulties = contracts.map { it.resolvedDifficulty() }.toSet()
        assertEquals(com.example.model.ContractDifficulty.entries.toSet(), difficulties)

        contracts.forEach { contract ->
            assertEquals("uid_1", contract.userId)
            assertEquals(com.example.model.ContractStatus.AVAILABLE, contract.resolvedStatus())
            assertTrue(contract.rewardCredits in 5000L..250_000L)
            assertTrue(contract.rewardXp in 140L..12_000L)
            assertTrue(contract.bonusPerfectCredits in 500L..80_000L)
            assertTrue(contract.cargoTons in 8L..52L)
            assertTrue(contract.distanceKm in 28.0..280.0)

            val routeSpec = contract.toRouteSpec()
            assertEquals(contract.resolvedTrailerType(), routeSpec.trailerType)
            assertEquals(contract.cargoTons.toInt(), routeSpec.cargoTons)
            assertEquals(contract.rewardCredits, routeSpec.baseRewardCredits)
            assertTrue(!routeSpec.isFreeRoam)
        }
    }
}

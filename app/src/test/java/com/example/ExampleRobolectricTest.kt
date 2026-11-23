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
}

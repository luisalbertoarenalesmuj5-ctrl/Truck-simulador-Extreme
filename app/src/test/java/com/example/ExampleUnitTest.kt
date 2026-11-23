package com.example

import com.example.model.CameraMode
import com.example.model.DriverProfile
import com.example.model.RouteCatalog
import com.example.model.TruckCatalog
import com.example.model.WeatherCondition
import com.example.sim.ControlInputs
import com.example.sim.TruckPhysicsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `legacy camera mode strings map cleanly to 2D perspectives`() {
        assertEquals(CameraMode.SIDE_2D, DriverProfile(cameraMode = "CHASE").resolvedCameraMode())
        assertEquals(CameraMode.TOPDOWN_2D, DriverProfile(cameraMode = "HOOD").resolvedCameraMode())
        assertEquals(CameraMode.CAB_2D, DriverProfile(cameraMode = "CAB").resolvedCameraMode())
        assertEquals(CameraMode.SIDE_2D, DriverProfile().resolvedCameraMode())
    }

    @Test
    fun `truck accelerates consumes fuel and supports manual gear shifting`() {
        val truck = TruckCatalog.byId("nordic_fh540")
        val route = RouteCatalog.byId("alpine_pass")
        val engine = TruckPhysicsEngine(truck, route)

        // Manual gear mode up/down
        assertFalse(engine.manualTransmission)
        assertTrue(engine.toggleTransmissionMode())
        engine.shiftGearUp()
        assertEquals(1, engine.currentGear)
        engine.shiftGearUp()
        assertEquals(2, engine.currentGear)

        // Accelerate in gear 2
        val initialFuel = engine.fuelLiters
        engine.inputs = ControlInputs(throttle = 1.0f)
        repeat(40) { engine.step(0.05f, paused = false) }
        val snap = engine.readTelemetry()
        assertTrue("Expected speed > 10 km/h, got ${snap.speedKmh}", snap.speedKmh > 10)
        assertTrue("Expected fuel to be consumed", engine.fuelLiters < initialFuel)
        assertTrue("Expected manual gear label M2", snap.gearLabel.startsWith("M"))
    }

    @Test
    fun `driver can exit truck to walk on foot and board back in`() {
        val truck = TruckCatalog.byId("valkyrie_s730")
        val route = RouteCatalog.byId("pacific_coast")
        val engine = TruckPhysicsEngine(truck, route)

        assertTrue(engine.exitTruckToFoot())
        assertTrue(engine.driverOnFoot)

        // Walk briefly beside cab door
        engine.inputs = ControlInputs(walkMoveY = 0.4f)
        repeat(5) { engine.step(0.05f, paused = false) }
        assertTrue(engine.boardTruckFromFoot())
        assertFalse(engine.driverOnFoot)
    }

    @Test
    fun `day night cycle and weather condition cycle work cleanly`() {
        val engine = TruckPhysicsEngine(TruckCatalog.trucks.first(), RouteCatalog.routes.first())
        val initialTod = engine.timeOfDay
        engine.cycleTimeOfDayPreset()
        assertTrue(engine.timeOfDay != initialTod)

        assertEquals(WeatherCondition.CLEAR, engine.weatherCondition)
        engine.cycleWeatherCondition()
        assertEquals(WeatherCondition.RAIN, engine.weatherCondition)
    }
}

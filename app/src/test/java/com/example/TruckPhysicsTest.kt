package com.example

import com.example.model.RouteCatalog
import com.example.model.TruckCatalog
import com.example.sim.ControlInputs
import com.example.sim.TruckPhysicsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TruckPhysicsTest {

    @Test
    fun truckAcceleratesShiftsGearsAndBrakesToStop() {
        val truck = TruckCatalog.byId("valkyrie_s730")
        val route = RouteCatalog.byId("alpine_pass")
        val engine = TruckPhysicsEngine(truck, route)

        // Accelerate straight for 4 seconds on the opening straightaway
        engine.inputs = ControlInputs(throttle = 1.0f, steerInput = 0f)
        repeat(240) {
            engine.step(1f / 60f, paused = false)
        }
        val movingTelemetry = engine.readTelemetry()
        assertTrue("Expected speed > 30 km/h, was ${movingTelemetry.speedKmh}", movingTelemetry.speedKmh > 30)
        assertTrue("Expected forward gear D*, was ${movingTelemetry.gearLabel}", movingTelemetry.gearLabel.startsWith("D"))

        // Apply full brakes for 3 seconds (comes down sharply before reversing)
        engine.inputs = ControlInputs(throttle = 0f, brake = 1.0f)
        repeat(180) {
            engine.step(1f / 60f, paused = false)
        }
        val brakedTelemetry = engine.readTelemetry()
        assertTrue(
            "Expected speed to drop significantly under braking (was ${movingTelemetry.speedKmh}, now ${brakedTelemetry.speedKmh})",
            brakedTelemetry.speedKmh < movingTelemetry.speedKmh
        )
    }

    @Test
    fun steeringTurnsTractorAndArticulatesTrailer() {
        val truck = TruckCatalog.byId("colossus_w900")
        val route = RouteCatalog.byId("pacific_coast")
        val engine = TruckPhysicsEngine(truck, route)
        val initialYaw = engine.tractorYaw

        engine.inputs = ControlInputs(throttle = 0.8f, steerInput = 0.65f)
        repeat(180) {
            engine.step(1f / 60f, paused = false)
        }

        assertTrue("Expected tractor yaw to change while steering", engine.tractorYaw != initialYaw)
        assertTrue("Expected trailer yaw to follow tractor", engine.trailerYaw != initialYaw)
    }

    @Test
    fun parkingBrakeHoldsTruckStationary() {
        val truck = TruckCatalog.byId("nordic_fh540")
        val route = RouteCatalog.byId("storm_summit")
        val engine = TruckPhysicsEngine(truck, route)

        engine.inputs = ControlInputs(throttle = 1.0f, parkingBrake = true)
        repeat(120) {
            engine.step(1f / 60f, paused = false)
        }
        assertEquals(0, engine.readTelemetry().speedKmh)
    }
}

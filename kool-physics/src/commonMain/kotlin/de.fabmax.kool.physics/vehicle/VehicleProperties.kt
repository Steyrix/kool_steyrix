package de.fabmax.kool.physics.vehicle

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.physics.Material
import de.fabmax.kool.physics.PhysicsFilterData
import de.fabmax.kool.physics.shapes.CollisionShape

class VehicleProperties {
    var chassisMass = 1500f
    var chassisDims = Vec3f(2f, 1f, 5f)
    var chassisCMOffset = Vec3f(0.0f, -chassisDims.y * 0.5f + 0.15f, 0.25f)

    var numWheels = 4       // for now this is kind of fixed
    var wheelMass = 20f
    var wheelWidth = 0.4f
    var wheelRadius = 0.5f
    var maxSteerAngle = 40f
    var wheelFrontZ = 1.75f
    var wheelRearZ = -1.75f
    var trackWidth = 1.8f
    var wheelCenterHeightOffset = -0.5f     // relative to center of chassis dims

    var maxBrakeTorque = 5000f
    var maxHandBrakeTorque = 5000f

    var maxCompression = 0.3f
    var maxDroop = 0.1f
    var springStrength = 35000f
    var springDamperRate = 4500f
    var camberAngleAtRest = 0.0f
    var camberAngleAtMaxDroop = 0.05f
    var camberAngleAtMaxCompression = -0.05f

    var peakEngineTorque = 1000f
    var peakEngineRpm = 6000f
    var gearSwitchTime = 0.5f
    var clutchStrength = 10f

    var frontAntiRollBarStiffness = 10000f
    var rearAntiRollBarStiffness = 10000f

    var chassisMOI = Vec3f(0f)
    var wheelMOI = 0f

    var groundMaterialFrictions = mapOf<Material, Float>()

    var chassisShapes = emptyList<CollisionShape>()
    var chassisMaterial = Material(0.5f,0.5f, 0.6f)
    var chassisSimFilterData = PhysicsFilterData(   // word0 = collide type, word1 = collide against types, word2 = PxPairFlags
        VehicleUtils.COLLISION_FLAG_CHASSIS,
        VehicleUtils.COLLISION_FLAG_CHASSIS_AGAINST
    )
    var wheelMaterial = Material(0.5f,0.5f, 0.6f)
    var wheelSimFilterData = PhysicsFilterData(   // word0 = collide type, word1 = collide against types, word2 = PxPairFlags
        VehicleUtils.COLLISION_FLAG_WHEEL,
        VehicleUtils.COLLISION_FLAG_WHEEL_AGAINST
    )

    init {
        updateChassisMoiFromDimensionsAndMass()
        updateWheelMoiFromRadiusAndMass()
    }

    fun setSymmetricWheelBase(wheelBase: Float) {
        wheelFrontZ = wheelBase * 0.5f
        wheelRearZ = wheelBase * -0.5f
    }

    fun updateChassisMoiFromDimensionsAndMass() {
        chassisMOI = Vec3f(
            (chassisDims.y * chassisDims.y + chassisDims.z * chassisDims.z) * chassisMass / 12.0f,
            (chassisDims.x * chassisDims.x + chassisDims.z * chassisDims.z) * chassisMass / 12.0f * 0.8f,
            (chassisDims.x * chassisDims.x + chassisDims.y * chassisDims.y) * chassisMass / 12.0f)
    }

    fun updateWheelMoiFromRadiusAndMass() {
        wheelMOI = 0.5f * wheelMass * wheelRadius * wheelRadius
    }
}
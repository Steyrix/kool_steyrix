package de.fabmax.kool.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


open class Vec4f(x: Float, y: Float, z: Float, w: Float) {

    protected val fields = FloatArray(4)

    open val x get() = this[0]
    open val y get() = this[1]
    open val z get() = this[2]
    open val w get() = this[3]

    constructor(f: Float) : this(f, f, f, f)
    constructor(xyz: Vec3f, w: Float) : this(xyz.x, xyz.y, xyz.z, w)
    constructor(v: Vec4f) : this(v.x, v.y, v.z, v.w)

    init {
        fields[0] = x
        fields[1] = y
        fields[2] = z
        fields[3] = w
    }

    fun add(other: Vec4f, result: MutableVec4f): MutableVec4f = result.set(this).add(other)

    fun distance(other: Vec4f): Float = sqrt(sqrDistance(other))

    fun dot(other: Vec4f): Float = x * other.x + y * other.y + z * other.z + w * other.w

    /**
     * Checks vector components for equality using [de.fabmax.kool.math.isFuzzyEqual], that is all components must
     * have a difference less or equal [eps].
     */
    fun isFuzzyEqual(other: Vec4f, eps: Float = FUZZY_EQ_F): Boolean =
            isFuzzyEqual(x, other.x, eps) && isFuzzyEqual(y, other.y, eps) && isFuzzyEqual(z, other.z, eps) && isFuzzyEqual(w, other.w, eps)

    fun length(): Float = sqrt(sqrLength())

    fun mix(other: Vec4f, weight: Float, result: MutableVec4f): MutableVec4f {
        result.x = other.x * weight + x * (1f - weight)
        result.y = other.y * weight + y * (1f - weight)
        result.z = other.z * weight + z * (1f - weight)
        result.w = other.w * weight + w * (1f - weight)
        return result
    }

    fun mul(other: Vec4f, result: MutableVec4f): MutableVec4f = result.set(this).mul(other)

    fun norm(result: MutableVec4f): MutableVec4f = result.set(this).norm()

    fun quatProduct(otherQuat: Vec4f, result: MutableVec4f): MutableVec4f {
        result.x = w * otherQuat.x + x * otherQuat.w + y * otherQuat.z - z * otherQuat.y
        result.y = w * otherQuat.y + y * otherQuat.w + z * otherQuat.x - x * otherQuat.z
        result.z = w * otherQuat.z + z * otherQuat.w + x * otherQuat.y - y * otherQuat.x
        result.w = w * otherQuat.w - x * otherQuat.x - y * otherQuat.y - z * otherQuat.z
        return result
    }

    fun scale(factor: Float, result: MutableVec4f): MutableVec4f = result.set(this).scale(factor)

    fun sqrDistance(other: Vec4f): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        val dw = z - other.w
        return dx*dx + dy*dy + dz*dz + dw*dw
    }

    fun sqrLength(): Float = x*x + y*y + z*z + w*w

    fun subtract(other: Vec4f, result: MutableVec4f): MutableVec4f = result.set(this).subtract(other)

    fun getXyz(result: MutableVec3f): MutableVec3f {
        result.x = x
        result.y = y
        result.z = z
        return result
    }

    open operator fun get(i: Int): Float = fields[i]

    operator fun times(other: Vec4f): Float = dot(other)

    fun multiplyQuaternion(quat: Vec4f, result: MutableVec4f): MutableVec4f {
        val ax = x
        val ay = y
        val az = z
        val aw = w

        val qx = quat.x
        val qy = quat.y
        val qz = quat.z
        val qw = quat.w

        result.x = aw * qx + ax * qw + ay * qz - az * qy
        result.y = aw * qy - ax * qz + ay * qw + az * qx
        result.z = aw * qz + ax * qy - ay * qx + az * qw
        result.w = aw * qw - ax * qx - ay * qy - az * qz
        return result
    }

    override fun toString(): String = "($x, $y, $z, $w)"

    fun toVec4d(): Vec4d = Vec4d(x.toDouble(), y.toDouble(), z.toDouble(), w.toDouble())

    fun toMutableVec4d(): MutableVec4d = toMutableVec4d(MutableVec4d())

    fun toMutableVec4d(result: MutableVec4d): MutableVec4d = result.set(x.toDouble(), y.toDouble(), z.toDouble(), w.toDouble())

    /**
     * Checks vector components for equality (using '==' operator). For better numeric stability consider using
     * [isFuzzyEqual].
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vec4f) return false

        if (x != other.x) return false
        if (y != other.y) return false
        if (z != other.z) return false
        if (w != other.w) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        result = 31 * result + w.hashCode()
        return result
    }

    companion object {
        val ZERO = Vec4f(0f)
        val X_AXIS = Vec4f(1f, 0f, 0f, 0f)
        val Y_AXIS = Vec4f(0f, 1f, 0f, 0f)
        val Z_AXIS = Vec4f(0f, 0f, 1f, 0f)
        val W_AXIS = Vec4f(0f, 0f, 0f, 1f)
        val NEG_X_AXIS = Vec4f(-1f, 0f, 0f, 0f)
        val NEG_Y_AXIS = Vec4f(0f, -1f, 0f, 0f)
        val NEG_Z_AXIS = Vec4f(0f, 0f, -1f, 0f)
        val NEG_W_AXIS = Vec4f(0f, 0f, 0f, -1f)
    }
}

open class MutableVec4f(x: Float, y: Float, z: Float, w: Float) : Vec4f(x, y, z, w) {

    override var x
        get() = this[0]
        set(value) { this[0] = value }
    override var y
        get() = this[1]
        set(value) { this[1] = value }
    override var z
        get() = this[2]
        set(value) { this[2] = value }
    override var w
        get() = this[3]
        set(value) { this[3] = value }

    val array: FloatArray
        get() = fields

    constructor() : this(0f, 0f, 0f, 0f)
    constructor(f: Float) : this(f, f, f, f)
    constructor(xyz: Vec3f, w: Float) : this(xyz.x, xyz.y, xyz.z, w)
    constructor(other: Vec4f) : this(other.x, other.y, other.z, other.w)

    fun add(other: Vec4f): MutableVec4f {
        x += other.x
        y += other.y
        z += other.z
        w += other.w
        return this
    }

    fun mul(other: Vec4f): MutableVec4f {
        x *= other.x
        y *= other.y
        z *= other.z
        w *= other.w
        return this
    }

    fun norm(): MutableVec4f = scale(1f / length())

    fun quatProduct(otherQuat: Vec4f): MutableVec4f {
        val px = w * otherQuat.x + x * otherQuat.w + y * otherQuat.z - z * otherQuat.y
        val py = w * otherQuat.y + y * otherQuat.w + z * otherQuat.x - x * otherQuat.z
        val pz = w * otherQuat.z + z * otherQuat.w + x * otherQuat.y - y * otherQuat.x
        val pw = w * otherQuat.w - x * otherQuat.x - y * otherQuat.y - z * otherQuat.z
        set(px, py, pz, pw)
        return this
    }

    fun scale(factor : Float): MutableVec4f {
        x *= factor
        y *= factor
        z *= factor
        w *= factor
        return this
    }

    fun set(x: Float, y: Float, z: Float, w: Float): MutableVec4f {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
        return this
    }

    fun set(other: Vec4f): MutableVec4f {
        x = other.x
        y = other.y
        z = other.z
        w = other.w
        return this
    }

    fun set(other: Vec4d): MutableVec4f {
        x = other.x.toFloat()
        y = other.y.toFloat()
        z = other.z.toFloat()
        w = other.w.toFloat()
        return this
    }

    fun set(xyz: Vec3f, w: Float = 0f): MutableVec4f {
        x = xyz.x
        y = xyz.y
        z = xyz.z
        this.w = w
        return this
    }

    fun subtract(other: Vec4f): MutableVec4f {
        x -= other.x
        y -= other.y
        z -= other.z
        w -= other.w
        return this
    }

    operator fun plusAssign(other: Vec4f) { add(other) }

    operator fun minusAssign(other: Vec4f) { subtract(other) }

    open operator fun set(i: Int, v: Float) { fields[i] = v }

    /**
     * Sets this vector to the quaternion representation of the given rotation. The axis components must represent a
     * unit vector (i.e. the length of the vector ([axX], [axY], [axZ]) must be 1.0).
     *
     * @param angleDeg rotation angle in degrees
     * @param axX      rotation axis x-component
     * @param axY      rotation axis y-component
     * @param axZ      rotation axis z-component
     */
    fun setRotation(angleDeg: Float, axX: Float, axY: Float, axZ: Float): MutableVec4f {
        val rad2 = angleDeg.toRad() * 0.5f
        val factor = sin(rad2)
        x = axX * factor
        y = axY * factor
        z = axZ * factor
        w = cos(rad2)
        return this
    }

    fun setRotation(angleDeg: Float, axis: Vec3f): MutableVec4f = setRotation(angleDeg, axis.x, axis.y, axis.z)

    /**
     * Rotates this vector by the given angle aorund the given axis, assuming that this vector already represents a
     * valid rotation quaternion. The axis components must represent a unit vector (i.e. the length of the vector
     * ([axX], [axY], [axZ]) must be 1.0).
     *
     * @param angleDeg rotation angle in degrees
     * @param axX      rotation axis x-component
     * @param axY      rotation axis y-component
     * @param axZ      rotation axis z-component
     */
    fun rotateQuaternion(angleDeg: Float, axX: Float, axY: Float, axZ: Float): MutableVec4f {
        val ax = x
        val ay = y
        val az = z
        val aw = w

        val rad2 = angleDeg.toRad() * 0.5f
        val factor = sin(rad2)
        val qx = axX * factor
        val qy = axY * factor
        val qz = axZ * factor
        val qw = cos(rad2)

        x = aw * qx + ax * qw + ay * qz - az * qy
        y = aw * qy - ax * qz + ay * qw + az * qx
        z = aw * qz + ax * qy - ay * qx + az * qw
        w = aw * qw - ax * qx - ay * qy - az * qz
        return this
    }

    fun rotateQuaternion(angleDeg: Float, axis: Vec3f): MutableVec4f = rotateQuaternion(angleDeg, axis.x, axis.y, axis.z)

    fun multiplyQuaternion(quat: Vec4f) = multiplyQuaternion(quat, this)
}

open class Vec4d(x: Double, y: Double, z: Double, w: Double) {

    protected val fields = DoubleArray(4)

    open val x get() = this[0]
    open val y get() = this[1]
    open val z get() = this[2]
    open val w get() = this[3]

    constructor(f: Double) : this(f, f, f, f)
    constructor(xyz: Vec3d, w: Double) : this(xyz.x, xyz.y, xyz.z, w)
    constructor(v: Vec4d) : this(v.x, v.y, v.z, v.w)

    init {
        fields[0] = x
        fields[1] = y
        fields[2] = z
        fields[3] = w
    }

    fun add(other: Vec4d, result: MutableVec4d): MutableVec4d = result.set(this).add(other)

    fun distance(other: Vec4d): Double = sqrt(sqrDistance(other))

    fun dot(other: Vec4d): Double = x * other.x + y * other.y + z * other.z + w * other.w

    /**
     * Checks vector components for equality using [de.fabmax.kool.math.isFuzzyEqual], that is all components must
     * have a difference less or equal [eps].
     */
    fun isFuzzyEqual(other: Vec4d, eps: Double = FUZZY_EQ_D): Boolean =
            isFuzzyEqual(x, other.x, eps) && isFuzzyEqual(y, other.y, eps) && isFuzzyEqual(z, other.z, eps) && isFuzzyEqual(w, other.w, eps)

    fun length(): Double = sqrt(sqrLength())

    fun mix(other: Vec4d, weight: Double, result: MutableVec4d): MutableVec4d {
        result.x = other.x * weight + x * (1.0 - weight)
        result.y = other.y * weight + y * (1.0 - weight)
        result.z = other.z * weight + z * (1.0 - weight)
        result.w = other.w * weight + w * (1.0 - weight)
        return result
    }

    fun mul(other: Vec4d, result: MutableVec4d): MutableVec4d = result.set(this).mul(other)

    fun norm(result: MutableVec4d): MutableVec4d = result.set(this).norm()

    fun quatProduct(otherQuat: Vec4d, result: MutableVec4d): MutableVec4d {
        result.x = w * otherQuat.x + x * otherQuat.w + y * otherQuat.z - z * otherQuat.y
        result.y = w * otherQuat.y + y * otherQuat.w + z * otherQuat.x - x * otherQuat.z
        result.z = w * otherQuat.z + z * otherQuat.w + x * otherQuat.y - y * otherQuat.x
        result.w = w * otherQuat.w - x * otherQuat.x - y * otherQuat.y - z * otherQuat.z
        return result
    }

    fun scale(factor: Double, result: MutableVec4d): MutableVec4d = result.set(this).scale(factor)

    fun sqrDistance(other: Vec4d): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        val dw = z - other.w
        return dx*dx + dy*dy + dz*dz + dw*dw
    }

    fun sqrLength(): Double = x*x + y*y + z*z + w*w

    fun subtract(other: Vec4d, result: MutableVec4d): MutableVec4d = result.set(this).subtract(other)

    fun getXyz(result: MutableVec3d): MutableVec3d {
        result.x = x
        result.y = y
        result.z = z
        return result
    }

    open operator fun get(i: Int): Double = fields[i]

    operator fun times(other: Vec4d): Double = dot(other)

    fun multiplyQuaternion(quat: Vec4d, result: MutableVec4d): MutableVec4d {
        val ax = x
        val ay = y
        val az = z
        val aw = w

        val qx = quat.x
        val qy = quat.y
        val qz = quat.z
        val qw = quat.w

        result.x = aw * qx + ax * qw + ay * qz - az * qy
        result.y = aw * qy - ax * qz + ay * qw + az * qx
        result.z = aw * qz + ax * qy - ay * qx + az * qw
        result.w = aw * qw - ax * qx - ay * qy - az * qz
        return result
    }

    override fun toString(): String = "($x, $y, $z, $w)"

    fun toVec4f(): Vec4f = Vec4f(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())

    fun toMutableVec4f(): MutableVec4f = toMutableVec4f(MutableVec4f())

    fun toMutableVec4f(result: MutableVec4f): MutableVec4f = result.set(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())

    /**
     * Checks vector components for equality (using '==' operator). For better numeric stability consider using
     * [isFuzzyEqual].
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vec4d) return false

        if (x != other.x) return false
        if (y != other.y) return false
        if (z != other.z) return false
        if (w != other.w) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        result = 31 * result + w.hashCode()
        return result
    }

    companion object {
        val ZERO = Vec4d(0.0)
        val X_AXIS = Vec4d(1.0, 0.0, 0.0, 0.0)
        val Y_AXIS = Vec4d(0.0, 1.0, 0.0, 0.0)
        val Z_AXIS = Vec4d(0.0, 0.0, 1.0, 0.0)
        val W_AXIS = Vec4d(0.0, 0.0, 0.0, 1.0)
        val NEG_X_AXIS = Vec4d(-1.0, 0.0, 0.0, 0.0)
        val NEG_Y_AXIS = Vec4d(0.0, -1.0, 0.0, 0.0)
        val NEG_Z_AXIS = Vec4d(0.0, 0.0, -1.0, 0.0)
        val NEG_W_AXIS = Vec4d(0.0, 0.0, 0.0, -1.0)
    }
}

open class MutableVec4d(x: Double, y: Double, z: Double, w: Double) : Vec4d(x, y, z, w) {

    override var x
        get() = this[0]
        set(value) { this[0] = value }
    override var y
        get() = this[1]
        set(value) { this[1] = value }
    override var z
        get() = this[2]
        set(value) { this[2] = value }
    override var w
        get() = this[3]
        set(value) { this[3] = value }

    constructor() : this(0.0, 0.0, 0.0, 0.0)
    constructor(f: Double) : this(f, f, f, f)
    constructor(xyz: Vec3d, w: Double) : this(xyz.x, xyz.y, xyz.z, w)
    constructor(other: Vec4d) : this(other.x, other.y, other.z, other.w)

    fun add(other: Vec4d): MutableVec4d {
        x += other.x
        y += other.y
        z += other.z
        w += other.w
        return this
    }

    fun mul(other: Vec4d): MutableVec4d {
        x *= other.x
        y *= other.y
        z *= other.z
        w *= other.w
        return this
    }

    fun norm(): MutableVec4d = scale(1.0 / length())

    fun quatProduct(otherQuat: Vec4d): MutableVec4d {
        val px = w * otherQuat.x + x * otherQuat.w + y * otherQuat.z - z * otherQuat.y
        val py = w * otherQuat.y + y * otherQuat.w + z * otherQuat.x - x * otherQuat.z
        val pz = w * otherQuat.z + z * otherQuat.w + x * otherQuat.y - y * otherQuat.x
        val pw = w * otherQuat.w - x * otherQuat.x - y * otherQuat.y - z * otherQuat.z
        set(px, py, pz, pw)
        return this
    }

    fun scale(factor : Double): MutableVec4d {
        x *= factor
        y *= factor
        z *= factor
        w *= factor
        return this
    }

    fun set(x: Double, y: Double, z: Double, w: Double): MutableVec4d {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
        return this
    }

    fun set(other: Vec4d): MutableVec4d {
        x = other.x
        y = other.y
        z = other.z
        w = other.w
        return this
    }

    fun set(other: Vec4f): MutableVec4d {
        x = other.x.toDouble()
        y = other.y.toDouble()
        z = other.z.toDouble()
        w = other.w.toDouble()
        return this
    }

    fun set(xyz: Vec3d, w: Double = 0.0): MutableVec4d {
        x = xyz.x
        y = xyz.y
        z = xyz.z
        this.w = w
        return this
    }

    fun subtract(other: Vec4d): MutableVec4d {
        x -= other.x
        y -= other.y
        z -= other.z
        w -= other.w
        return this
    }

    operator fun plusAssign(other: Vec4d) { add(other) }

    operator fun minusAssign(other: Vec4d) { subtract(other) }

    open operator fun set(i: Int, v: Double) { fields[i] = v }

    /**
     * Sets this vector to the quaternion representation of the given rotation. The axis components must represent a
     * unit vector (i.e. the length of the vector ([axX], [axY], [axZ]) must be 1.0).
     *
     * @param angleDeg rotation angle in degrees
     * @param axX      rotation axis x-component
     * @param axY      rotation axis y-component
     * @param axZ      rotation axis z-component
     */
    fun setRotation(angleDeg: Double, axX: Double, axY: Double, axZ: Double): MutableVec4d {
        val rad2 = angleDeg.toRad() * 0.5
        val factor = sin(rad2)
        x = axX * factor
        y = axY * factor
        z = axZ * factor
        w = cos(rad2)
        return this
    }

    fun setRotation(angleDeg: Double, axis: Vec3d): MutableVec4d = setRotation(angleDeg, axis.x, axis.y, axis.z)

    /**
     * Rotates this vector by the given angle aorund the given axis, assuming that this vector already represents a
     * valid rotation quaternion. The axis components must represent a unit vector (i.e. the length of the vector
     * ([axX], [axY], [axZ]) must be 1.0).
     *
     * @param angleDeg rotation angle in degrees
     * @param axX      rotation axis x-component
     * @param axY      rotation axis y-component
     * @param axZ      rotation axis z-component
     */
    fun rotateQuaternion(angleDeg: Double, axX: Double, axY: Double, axZ: Double): MutableVec4d {
        val ax = x
        val ay = y
        val az = z
        val aw = w

        val rad2 = angleDeg.toRad() * 0.5
        val factor = sin(rad2)
        val qx = axX * factor
        val qy = axY * factor
        val qz = axZ * factor
        val qw = cos(rad2)

        x = aw * qx + ax * qw + ay * qz - az * qy
        y = aw * qy - ax * qz + ay * qw + az * qx
        z = aw * qz + ax * qy - ay * qx + az * qw
        w = aw * qw - ax * qx - ay * qy - az * qz
        return this
    }

    fun rotateQuaternion(angleDeg: Double, axis: Vec3d): MutableVec4d = rotateQuaternion(angleDeg, axis.x, axis.y, axis.z)

    fun multiplyQuaternion(quat: Vec4d) = multiplyQuaternion(quat, this)
}

open class Vec4i(x: Int, y: Int, z: Int, w: Int) {

    protected val fields = IntArray(4)

    open val x get() = this[0]
    open val y get() = this[1]
    open val z get() = this[2]
    open val w get() = this[3]

    constructor(f: Int) : this(f, f, f, f)
    constructor(v: Vec4i) : this(v.x, v.y, v.z, v.w)

    init {
        fields[0] = x
        fields[1] = y
        fields[2] = z
        fields[3] = w
    }

    open operator fun get(i: Int): Int = fields[i]

    override fun toString(): String = "($x, $y, $z, $w)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vec4i) return false

        if (x != other.x) return false
        if (y != other.y) return false
        if (z != other.z) return false
        if (w != other.w) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        result = 31 * result + w.hashCode()
        return result
    }

    companion object {
        val ZERO = Vec4i(0)
        val X_AXIS = Vec4i(1, 0, 0, 0)
        val Y_AXIS = Vec4i(0, 1, 0, 0)
        val Z_AXIS = Vec4i(0, 0, 1, 0)
        val W_AXIS = Vec4i(0, 0, 0, 1)
        val NEG_X_AXIS = Vec4i(-1, 0, 0, 0)
        val NEG_Y_AXIS = Vec4i(0, -1, 0, 0)
        val NEG_Z_AXIS = Vec4i(0, 0, -1, 0)
        val NEG_W_AXIS = Vec4i(0, 0, 0, -1)
    }
}

open class MutableVec4i(x: Int, y: Int, z: Int, w: Int) : Vec4i(x, y, z, w) {

    override var x
        get() = this[0]
        set(value) { this[0] = value }
    override var y
        get() = this[1]
        set(value) { this[1] = value }
    override var z
        get() = this[2]
        set(value) { this[2] = value }
    override var w
        get() = this[3]
        set(value) { this[3] = value }

    val array: IntArray
        get() = fields

    constructor() : this(0, 0, 0, 0)
    constructor(f: Int) : this(f, f, f, f)
    constructor(other: Vec4i) : this(other.x, other.y, other.z, other.w)

    init {
        fields[0] = x
        fields[1] = y
        fields[2] = z
        fields[3] = w
    }

    fun set(x: Int, y: Int, z: Int, w: Int): MutableVec4i {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
        return this
    }

    fun set(other: Vec4i): MutableVec4i {
        x = other.x
        y = other.y
        z = other.z
        w = other.w
        return this
    }

    open operator fun set(i: Int, v: Int) { fields[i] = v }

    fun add(other: Vec4i): MutableVec4i {
        x += other.x
        y += other.y
        z += other.z
        w += other.w
        return this
    }

    fun subtract(other: Vec4i): MutableVec4i {
        x -= other.x
        y -= other.y
        z -= other.z
        w -= other.w
        return this
    }
}
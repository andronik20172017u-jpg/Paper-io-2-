package com.example.game.math

import android.opengl.Matrix
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {
  operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)

  operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)

  operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)

  fun lengthSq(): Float = x * x + y * y

  fun length(): Float = sqrt(lengthSq())

  fun normalized(): Vec2 {
    val len = length()
    return if (len > 1e-6f) Vec2(x / len, y / len) else Vec2(0f, 1f)
  }

  fun dot(other: Vec2): Float = x * other.x + y * other.y
}

data class Vec3(val x: Float, val y: Float, val z: Float) {
  operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)

  operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)

  operator fun times(scalar: Float) = Vec3(x * scalar, y * scalar, z * scalar)

  fun length(): Float = sqrt(x * x + y * y + z * z)

  fun normalized(): Vec3 {
    val len = length()
    return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
  }

  fun cross(other: Vec3): Vec3 =
    Vec3(
      y * other.z - z * other.y,
      z * other.x - x * other.z,
      x * other.y - y * other.x,
    )
}

object Math3D {
  const val TWO_PI = (PI * 2.0).toFloat()

  fun wrapAngle(angle: Float): Float {
    var a = angle % TWO_PI
    if (a > PI) a -= TWO_PI
    if (a < -PI) a += TWO_PI
    return a
  }

  fun angleDiff(from: Float, to: Float): Float = wrapAngle(to - from)

  fun lerpAngle(from: Float, to: Float, t: Float): Float {
    val diff = angleDiff(from, to)
    return wrapAngle(from + diff * t.coerceIn(0f, 1f))
  }

  fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

  fun clamp(v: Float, min: Float, max: Float): Float = v.coerceIn(min, max)

  fun distSqPointToSegment(p: Vec2, a: Vec2, b: Vec2): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lenSq = abx * abx + aby * aby
    if (lenSq < 1e-8f) {
      val dx = p.x - a.x
      val dy = p.y - a.y
      return dx * dx + dy * dy
    }
    val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq).coerceIn(0f, 1f)
    val projX = a.x + abx * t
    val projY = a.y + aby * t
    val dx = p.x - projX
    val dy = p.y - projY
    return dx * dx + dy * dy
  }

  /**
   * Builds a Model matrix with Translation, Y-yaw, X-pitch, Z-roll, and uniform scale.
   * Matches +Z forward convention (yaw = atan2(dx, dz)).
   */
  fun buildUnitModelMatrix(
    outMatrix: FloatArray,
    x: Float,
    y: Float,
    z: Float,
    yawRad: Float,
    pitchRad: Float,
    rollRad: Float,
    scale: Float = 1f,
  ) {
    Matrix.setIdentityM(outMatrix, 0)
    Matrix.translateM(outMatrix, 0, x, y, z)
    Matrix.rotateM(outMatrix, 0, Math.toDegrees(yawRad.toDouble()).toFloat(), 0f, 1f, 0f)
    Matrix.rotateM(outMatrix, 0, Math.toDegrees(pitchRad.toDouble()).toFloat(), 1f, 0f, 0f)
    Matrix.rotateM(outMatrix, 0, Math.toDegrees(rollRad.toDouble()).toFloat(), 0f, 0f, 1f)
    if (scale != 1f) {
      Matrix.scaleM(outMatrix, 0, scale, scale, scale)
    }
  }

  /**
   * Computes upper-left 3x3 inverse-transpose normal matrix from a 4x4 model matrix.
   */
  fun computeNormalMatrix3x3(model4x4: FloatArray, outMat3: FloatArray, tempInv: FloatArray) {
    if (Matrix.invertM(tempInv, 0, model4x4, 0)) {
      // Transpose of inverse upper 3x3 (column-major)
      outMat3[0] = tempInv[0]
      outMat3[1] = tempInv[4]
      outMat3[2] = tempInv[8]

      outMat3[3] = tempInv[1]
      outMat3[4] = tempInv[5]
      outMat3[5] = tempInv[9]

      outMat3[6] = tempInv[2]
      outMat3[7] = tempInv[6]
      outMat3[8] = tempInv[10]
    } else {
      outMat3[0] = 1f; outMat3[1] = 0f; outMat3[2] = 0f
      outMat3[3] = 0f; outMat3[4] = 1f; outMat3[5] = 0f
      outMat3[6] = 0f; outMat3[7] = 0f; outMat3[8] = 1f
    }
  }

  fun yawFromDirection(dx: Float, dz: Float): Float = atan2(dx, dz)

  fun forwardFromYaw(yaw: Float): Vec2 = Vec2(sin(yaw), cos(yaw))
}

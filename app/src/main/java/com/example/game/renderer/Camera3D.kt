package com.example.game.renderer

import android.opengl.Matrix
import com.example.game.math.Math3D
import com.example.game.math.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Smooth third-person angled follow camera with look-ahead damping
 * and cinematic menu orbit mode.
 */
class Camera3D {
  val viewMatrix = FloatArray(16)
  val projectionMatrix = FloatArray(16)
  val vpMatrix = FloatArray(16)

  var position = Vec3(0f, 22f, 18f)
    private set

  private var targetPos = Vec3(0f, 0f, 0f)
  private var aspectRatio = 1f
  private var orbitAngle = 0f

  fun setViewport(width: Int, height: Int) {
    aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1f
    val fov = if (aspectRatio > 1.2f) 44f else 50f
    Matrix.perspectiveM(projectionMatrix, 0, fov, aspectRatio, 1.0f, 180f)
  }

  fun updateFollow(
    dt: Float,
    focusX: Float,
    focusZ: Float,
    headingYaw: Float,
    isMenuOrbit: Boolean,
  ) {
    val damp = (dt * 5.2f).coerceIn(0f, 1f)

    if (isMenuOrbit) {
      orbitAngle += dt * 0.24f
      val orbitR = 34f
      val desiredEye = Vec3(cos(orbitAngle) * orbitR, 24f, sin(orbitAngle) * orbitR)
      position = Vec3(
        Math3D.lerp(position.x, desiredEye.x, damp),
        Math3D.lerp(position.y, desiredEye.y, damp),
        Math3D.lerp(position.z, desiredEye.z, damp),
      )
      targetPos = Vec3(
        Math3D.lerp(targetPos.x, 0f, damp),
        0f,
        Math3D.lerp(targetPos.z, 0f, damp),
      )
    } else {
      val forward = Math3D.forwardFromYaw(headingYaw)
      val lookAheadX = focusX + forward.x * 2.4f
      val lookAheadZ = focusZ + forward.y * 2.4f

      targetPos = Vec3(
        Math3D.lerp(targetPos.x, lookAheadX, damp * 1.2f),
        0f,
        Math3D.lerp(targetPos.z, lookAheadZ, damp * 1.2f),
      )

      val height = if (aspectRatio > 1.2f) 21.5f else 24.5f
      val zBack = if (aspectRatio > 1.2f) 16.0f else 17.5f
      val desiredEye = Vec3(targetPos.x, height, targetPos.z + zBack)
      position = Vec3(
        Math3D.lerp(position.x, desiredEye.x, damp),
        Math3D.lerp(position.y, desiredEye.y, damp),
        Math3D.lerp(position.z, desiredEye.z, damp),
      )
    }

    Matrix.setLookAtM(
      viewMatrix,
      0,
      position.x,
      position.y,
      position.z,
      targetPos.x,
      targetPos.y,
      targetPos.z,
      0f,
      1f,
      0f,
    )
    Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
  }
}

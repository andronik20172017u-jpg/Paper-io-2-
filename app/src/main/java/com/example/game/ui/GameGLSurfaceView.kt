package com.example.game.ui

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.example.game.engine.GameWorld3D
import com.example.game.engine.MatchPhase
import com.example.game.renderer.GameRenderer3D
import kotlin.math.sqrt

data class JoystickOverlayState(
  val isActive: Boolean = false,
  val anchorX: Float = 0f,
  val anchorY: Float = 0f,
  val knobX: Float = 0f,
  val knobY: Float = 0f,
  val radiusPx: Float = 110f,
)

@SuppressLint("ViewConstructor")
class GameGLSurfaceView(
  context: Context,
  private val world: GameWorld3D,
  private val onJoystickChanged: (JoystickOverlayState) -> Unit,
) : GLSurfaceView(context) {

  private val renderer: GameRenderer3D
  private var activePointerId = MotionEvent.INVALID_POINTER_ID
  private var anchorX = 0f
  private var anchorY = 0f
  private val maxRadiusPx = 110f * resources.displayMetrics.density / 2.2f

  init {
    setEGLContextClientVersion(3)
    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
    renderer = GameRenderer3D(context, world)
    setRenderer(renderer)
    renderMode = RENDERMODE_CONTINUOUSLY
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (world.phase != MatchPhase.PLAYING) {
      resetJoystick()
      return false
    }

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        activePointerId = event.getPointerId(0)
        anchorX = event.x
        anchorY = event.y
        onJoystickChanged(
          JoystickOverlayState(
            isActive = true,
            anchorX = anchorX,
            anchorY = anchorY,
            knobX = anchorX,
            knobY = anchorY,
            radiusPx = maxRadiusPx,
          )
        )
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        val ptrIdx = event.findPointerIndex(activePointerId)
        if (ptrIdx < 0) return false
        val dx = event.getX(ptrIdx) - anchorX
        val dy = event.getY(ptrIdx) - anchorY
        val dist = sqrt(dx * dx + dy * dy)

        val clampedDist = dist.coerceAtMost(maxRadiusPx)
        val nx = if (dist > 1f) dx / dist else 0f
        val ny = if (dist > 1f) dy / dist else 0f

        if (dist > 10f) {
          world.setJoystickInput(active = true, dx = nx, dyScreen = ny)
        }

        onJoystickChanged(
          JoystickOverlayState(
            isActive = true,
            anchorX = anchorX,
            anchorY = anchorY,
            knobX = anchorX + nx * clampedDist,
            knobY = anchorY + ny * clampedDist,
            radiusPx = maxRadiusPx,
          )
        )
        return true
      }

      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        resetJoystick()
        return true
      }

      MotionEvent.ACTION_POINTER_UP -> {
        val idx = event.actionIndex
        if (event.getPointerId(idx) == activePointerId) {
          resetJoystick()
        }
        return true
      }
    }
    return super.onTouchEvent(event)
  }

  private fun resetJoystick() {
    activePointerId = MotionEvent.INVALID_POINTER_ID
    world.setJoystickInput(active = false, dx = 0f, dyScreen = 0f)
    onJoystickChanged(JoystickOverlayState(isActive = false))
  }
}

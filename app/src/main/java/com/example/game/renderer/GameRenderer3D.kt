package com.example.game.renderer

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.engine.BotPersonality
import com.example.game.engine.GameWorld3D
import com.example.game.engine.MatchPhase
import com.example.game.engine.TrailMeshBuilder
import com.example.game.engine.UnitModelType
import com.example.game.graphics.GpuMesh
import com.example.game.graphics.ObjLoader
import com.example.game.graphics.ShaderProgram
import com.example.game.math.Math3D
import com.example.game.math.Vec2
import com.example.game.math.Vec3
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * OpenGL ES 3.0 Renderer orchestrating custom GLSL shaders, OBJ 3D models (including Thief & Bomb
 * & Watermelon Wedge), dynamic 3D territory slabs with skin relief, glowing trails, and confetti.
 */
class GameRenderer3D(
  private val context: Context,
  private val world: GameWorld3D,
) : GLSurfaceView.Renderer {

  private val camera = Camera3D()

  private lateinit var unitShader: ShaderProgram
  private lateinit var arenaShader: ShaderProgram
  private lateinit var territoryShader: ShaderProgram
  private lateinit var trailShader: ShaderProgram
  private lateinit var particleShader: ShaderProgram

  private val modelMeshes = HashMap<UnitModelType, GpuMesh>()
  private val bombMesh = GpuMesh(dynamic = false)
  private val pylonMesh = GpuMesh(dynamic = false)
  private val crownMesh = GpuMesh(dynamic = false)
  private val arenaMesh = GpuMesh(dynamic = false)
  private val shardMesh = GpuMesh(dynamic = false)

  private val territoryMeshes = Array(world.units.size) { GpuMesh(dynamic = true) }
  private val trailMeshes = Array(world.units.size) { GpuMesh(dynamic = true) }

  private val modelMatrix = FloatArray(16)
  private val mvpMatrix = FloatArray(16)
  private val normalMatrix = FloatArray(9)
  private val tempInvMatrix = FloatArray(16)
  private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

  private val lightDir = Vec3(0.42f, 0.85f, 0.32f).normalized()
  private var lastFrameNs = 0L

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES30.glClearColor(0.028f, 0.042f, 0.082f, 1.0f)
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    GLES30.glDepthFunc(GLES30.GL_LEQUAL)
    GLES30.glEnable(GLES30.GL_BLEND)
    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

    val assets = context.assets
    unitShader = ShaderProgram.fromAssets(assets, "shaders/unit_model.vert", "shaders/unit_model.frag")
    arenaShader = ShaderProgram.fromAssets(assets, "shaders/arena_platform.vert", "shaders/arena_platform.frag")
    territoryShader = ShaderProgram.fromAssets(assets, "shaders/territory_slab.vert", "shaders/territory_slab.frag")
    trailShader = ShaderProgram.fromAssets(assets, "shaders/trail_ribbon.vert", "shaders/trail_ribbon.frag")
    particleShader = ShaderProgram.fromAssets(assets, "shaders/particle_burst.vert", "shaders/particle_burst.frag")

    UnitModelType.entries.forEach { type ->
      val gpu = GpuMesh(dynamic = false)
      gpu.upload(ObjLoader.loadFromAssets(assets, type.assetPath, scale = 1.05f))
      modelMeshes[type] = gpu
    }
    bombMesh.upload(ObjLoader.loadFromAssets(assets, "models/bomb_sphere.obj", scale = 1.15f))
    pylonMesh.upload(ObjLoader.loadFromAssets(assets, "models/arena_pylon.obj", scale = 1.15f))
    crownMesh.upload(ObjLoader.loadFromAssets(assets, "models/crown_beacon.obj", scale = 0.95f))

    arenaMesh.upload(ArenaMeshBuilder.buildArenaPlatform(world.territoryManager.arenaRadius))
    shardMesh.upload(ArenaMeshBuilder.buildShardOctahedron())

    world.withLock {
      world.units.forEach { world.territoryManager.markDirty(it.id) }
    }
    lastFrameNs = System.nanoTime()
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES30.glViewport(0, 0, width, height)
    camera.setViewport(width, height)
  }

  override fun onDrawFrame(gl: GL10?) {
    val nowNs = System.nanoTime()
    val dt = if (lastFrameNs == 0L) 0.016f else ((nowNs - lastFrameNs) / 1_000_000_000.0).toFloat()
    lastFrameNs = nowNs

    val elapsedTime = world.update(dt)

    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

    world.withLock {
      val player = world.player
      val isOrbit = world.phase == MatchPhase.MENU || world.phase == MatchPhase.VICTORY
      camera.updateFollow(dt, player.x, player.z, player.yaw, isOrbit)

      // 1. Render 3D Arena Platform with textured knit relief & bomb danger rings
      arenaShader.use()
      Matrix.multiplyMM(mvpMatrix, 0, camera.vpMatrix, 0, identityMatrix, 0)
      arenaShader.setMat4("u_MvpMatrix", mvpMatrix)
      arenaShader.setMat4("u_ModelMatrix", identityMatrix)
      arenaShader.setFloat("u_Time", elapsedTime)
      arenaShader.setFloat("u_ArenaRadius", world.territoryManager.arenaRadius)
      arenaShader.setVec3("u_PlayerPos", player.x, 0f, player.z)
      arenaShader.setVec3("u_CameraPos", camera.position)

      val b0 = world.bombs.getOrNull(0)
      val b1 = world.bombs.getOrNull(1)
      arenaShader.setVec4("u_Bomb0", b0?.x ?: 0f, b0?.z ?: 0f, b0?.blastRadius ?: 0f, if (b0 != null) 1f else 0f)
      arenaShader.setVec4("u_Bomb1", b1?.x ?: 0f, b1?.z ?: 0f, b1?.blastRadius ?: 0f, if (b1 != null) 1f else 0f)
      arenaMesh.draw()

      // 2. Render 3D Extruded Territories with skin patterns & capture flash
      territoryShader.use()
      territoryShader.setMat4("u_MvpMatrix", mvpMatrix)
      territoryShader.setMat4("u_ModelMatrix", identityMatrix)
      territoryShader.setFloat("u_Time", elapsedTime)
      territoryShader.setVec3("u_CameraPos", camera.position)
      territoryShader.setVec3("u_LightDir", lightDir)

      for (unit in world.units) {
        if (unit.personality == BotPersonality.THIEF) continue
        if (world.territoryManager.consumeDirtyFlag(unit.id)) {
          val meshData = world.territoryManager.buildOwnerMesh(unit.id)
          territoryMeshes[unit.id].upload(meshData)
        }
        if (world.territoryManager.hasTerritory(unit.id)) {
          territoryShader.setVec3("u_TeamColor", unit.effectiveBaseColor())
          territoryShader.setFloat("u_SkinStyle", unit.effectiveSkinStyle())
          territoryShader.setFloat("u_FlashAmount", (unit.flashTimer / 0.65f).coerceIn(0f, 1f))
          territoryMeshes[unit.id].draw()
        }
      }

      // 3. Render 12 Perimeter 3D Pylon Beacons
      unitShader.use()
      unitShader.setVec3("u_CameraPos", camera.position)
      unitShader.setVec3("u_LightDir", lightDir)
      unitShader.setFloat("u_DissolveAmount", 0f)
      unitShader.setFloat("u_SkinStyle", 0f)
      unitShader.setFloat("u_AlertPulse", 0f)
      unitShader.setFloat("u_Time", elapsedTime)
      unitShader.setVec3("u_BaseColor", 0.16f, 0.28f, 0.44f)
      unitShader.setVec3("u_RimColor", 0.15f, 0.85f, 1.0f)

      val pylonCount = 12
      val pylonR = world.territoryManager.arenaRadius + 0.55f
      for (i in 0 until pylonCount) {
        val a = (i.toFloat() / pylonCount.toFloat()) * Math3D.TWO_PI
        val px = cos(a) * pylonR
        val pz = sin(a) * pylonR
        Math3D.buildUnitModelMatrix(modelMatrix, px, 0f, pz, -a, 0f, 0f, 1.0f)
        drawModelWithUnitShader(pylonMesh)
      }

      // 4. Render 3D Armed Bombs (pulsating scale + red/white warning blink!)
      for (bomb in world.bombs) {
        val urgency = (1f - bomb.fuseTimer / bomb.maxFuse).coerceIn(0f, 1f)
        val blink = 0.5f + 0.5f * sin(elapsedTime * (8f + urgency * 24f))
        val pulseScale = 0.88f + urgency * 0.28f + blink * 0.10f
        Math3D.buildUnitModelMatrix(modelMatrix, bomb.x, 0.25f, bomb.z, elapsedTime * 2.4f, 0f, 0f, pulseScale)
        unitShader.setVec3("u_BaseColor", 0.18f, 0.18f, 0.22f)
        unitShader.setVec3("u_RimColor", 1.0f, 0.22f, 0.10f)
        unitShader.setFloat("u_SkinStyle", 0f)
        unitShader.setFloat("u_DissolveAmount", 0f)
        unitShader.setFloat("u_AlertPulse", blink * (0.4f + 0.6f * urgency))
        drawModelWithUnitShader(bombMesh)
      }
      unitShader.setFloat("u_AlertPulse", 0f)

      // 5. Render 3D Player, Bots & Thief Raider
      for (unit in world.units) {
        if (!unit.isAlive && !unit.isDissolving) continue
        val hoverY = 0.52f + sin(unit.hoverPhase) * 0.08f
        Math3D.buildUnitModelMatrix(
          outMatrix = modelMatrix,
          x = unit.x,
          y = hoverY,
          z = unit.z,
          yawRad = unit.yaw,
          pitchRad = unit.pitchAngle,
          rollRad = unit.bankAngle,
          scale = 1.0f,
        )
        unitShader.setVec3("u_BaseColor", unit.effectiveBaseColor())
        unitShader.setVec3("u_RimColor", unit.effectiveRimColor())
        val modelSkinStyle =
          if (unit.modelType == UnitModelType.WATERMELON_SLICE) 1.0f else unit.effectiveSkinStyle()
        unitShader.setFloat("u_SkinStyle", modelSkinStyle)
        unitShader.setFloat("u_DissolveAmount", if (unit.isDissolving) unit.dissolveProgress else 0f)

        val mesh = modelMeshes[unit.modelType] ?: modelMeshes[UnitModelType.CYBER_DRONE]
        if (mesh != null) {
          drawModelWithUnitShader(mesh)
        }
      }

      // 6. Render Spinning 3D Leader Crown
      val leader = world.units.getOrNull(world.leaderUnitId)
      if (leader != null && leader.isAlive && leader.territoryPercent > 0.1f) {
        val crownY = 1.68f + sin(elapsedTime * 3.5f) * 0.12f
        Math3D.buildUnitModelMatrix(
          outMatrix = modelMatrix,
          x = leader.x,
          y = crownY,
          z = leader.z,
          yawRad = elapsedTime * 1.8f,
          pitchRad = 0f,
          rollRad = 0f,
          scale = 0.92f,
        )
        unitShader.setVec3("u_BaseColor", 1.0f, 0.80f, 0.16f)
        unitShader.setVec3("u_RimColor", 1.0f, 0.96f, 0.65f)
        unitShader.setFloat("u_SkinStyle", 0f)
        unitShader.setFloat("u_DissolveAmount", 0f)
        drawModelWithUnitShader(crownMesh)
      }

      // 7. Render Glowing Semi-Transparent 3D Trail Ribbons
      GLES30.glDepthMask(false)
      trailShader.use()
      Matrix.multiplyMM(mvpMatrix, 0, camera.vpMatrix, 0, identityMatrix, 0)
      trailShader.setMat4("u_MvpMatrix", mvpMatrix)
      trailShader.setMat4("u_ModelMatrix", identityMatrix)
      trailShader.setFloat("u_Time", elapsedTime)

      for (unit in world.units) {
        if (!unit.isAlive || unit.trail.isEmpty()) continue
        val ribbonData = TrailMeshBuilder.build(unit.trail, Vec2(unit.x, unit.z))
        trailMeshes[unit.id].upload(ribbonData)
        trailShader.setVec3("u_TeamColor", unit.effectiveRimColor())
        trailShader.setFloat("u_SkinStyle", unit.effectiveSkinStyle())
        trailMeshes[unit.id].draw()
      }

      // 8. Render 3D Explosion Shards, Trail Sparks & Confetti
      if (world.particles.isNotEmpty()) {
        particleShader.use()
        for (p in world.particles) {
          val lifeFrac = (1f - p.life / p.maxLife).coerceIn(0f, 1f)
          val s = p.scale * (if (p.isConfetti) 1.0f else lifeFrac)
          Math3D.buildUnitModelMatrix(modelMatrix, p.x, p.y, p.z, p.rotY, p.rotY * 0.7f, p.rotY * 0.4f, s)
          Matrix.multiplyMM(mvpMatrix, 0, camera.vpMatrix, 0, modelMatrix, 0)
          particleShader.setMat4("u_MvpMatrix", mvpMatrix)
          particleShader.setMat4("u_ModelMatrix", modelMatrix)
          particleShader.setVec3("u_Color", p.color)
          particleShader.setFloat("u_Alpha", lifeFrac)
          shardMesh.draw()
        }
      }
      GLES30.glDepthMask(true)
    }
  }

  private fun drawModelWithUnitShader(mesh: GpuMesh) {
    Matrix.multiplyMM(mvpMatrix, 0, camera.vpMatrix, 0, modelMatrix, 0)
    Math3D.computeNormalMatrix3x3(modelMatrix, normalMatrix, tempInvMatrix)
    unitShader.setMat4("u_MvpMatrix", mvpMatrix)
    unitShader.setMat4("u_ModelMatrix", modelMatrix)
    unitShader.setMat3("u_NormalMatrix", normalMatrix)
    mesh.draw()
  }
}

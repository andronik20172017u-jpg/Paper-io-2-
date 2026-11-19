package com.example.game.engine

import com.example.game.math.Math3D
import com.example.game.math.Vec2
import com.example.game.math.Vec3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class UnitModelType(val assetPath: String, val displayName: String) {
  CYBER_DRONE("models/cyber_drone.obj", "Cyber Delta"),
  HEX_CRUISER("models/hex_cruiser.obj", "Hex Cruiser"),
  WATERMELON_SLICE("models/watermelon_wedge.obj", "Melon Slice"),
  THIEF_RAIDER("models/thief_raider.obj", "Thief Raider"),
}

enum class UnitSkin(
  val id: String,
  val displayName: String,
  val price: Long,
  val shaderStyle: Float,
  val baseColor: Vec3,
  val rimColor: Vec3,
  val uiColorHex: Long,
) {
  CYBER_CYAN(
    id = "CYBER_CYAN",
    displayName = "Cyber Neon",
    price = 0L,
    shaderStyle = 0f,
    baseColor = Vec3(0.08f, 0.72f, 0.96f),
    rimColor = Vec3(0.45f, 0.96f, 1.0f),
    uiColorHex = 0xFF14B8F5,
  ),
  WATERMELON(
    id = "WATERMELON",
    displayName = "Watermelon",
    price = 100L,
    shaderStyle = 1f,
    baseColor = Vec3(0.96f, 0.18f, 0.30f),
    rimColor = Vec3(0.24f, 0.90f, 0.42f),
    uiColorHex = 0xFFF43F5E,
  ),
  SOLAR_GOLD(
    id = "SOLAR_GOLD",
    displayName = "Solar Gold",
    price = 250L,
    shaderStyle = 2f,
    baseColor = Vec3(0.98f, 0.72f, 0.14f),
    rimColor = Vec3(1.0f, 0.95f, 0.62f),
    uiColorHex = 0xFFFACC15,
  ),
  TOXIC_VENOM(
    id = "TOXIC_VENOM",
    displayName = "Venom Bio",
    price = 400L,
    shaderStyle = 3f,
    baseColor = Vec3(0.14f, 0.86f, 0.46f),
    rimColor = Vec3(0.55f, 1.0f, 0.75f),
    uiColorHex = 0xFF22C55E,
  );

  companion object {
    fun fromId(id: String): UnitSkin = entries.firstOrNull { it.id == id } ?: CYBER_CYAN
  }
}

enum class BotPersonality {
  AGGRESSIVE,
  CAUTIOUS,
  GREEDY,
  THIEF,
}

data class TeamPalette(
  val id: Int,
  val label: String,
  val baseColor: Vec3,
  val rimColor: Vec3,
  val uiColorHex: Long,
)

object GamePalettes {
  val ALL =
    listOf(
      TeamPalette(
        id = 0,
        label = "YOU",
        baseColor = Vec3(0.08f, 0.72f, 0.96f),
        rimColor = Vec3(0.45f, 0.96f, 1.0f),
        uiColorHex = 0xFF14B8F5,
      ),
      TeamPalette(
        id = 1,
        label = "SHADOW",
        baseColor = Vec3(0.96f, 0.20f, 0.44f),
        rimColor = Vec3(1.0f, 0.56f, 0.72f),
        uiColorHex = 0xFFF53370,
      ),
      TeamPalette(
        id = 2,
        label = "BLITZ",
        baseColor = Vec3(0.98f, 0.64f, 0.12f),
        rimColor = Vec3(1.0f, 0.90f, 0.45f),
        uiColorHex = 0xFFFA9E1F,
      ),
      TeamPalette(
        id = 3,
        label = "VIPER",
        baseColor = Vec3(0.16f, 0.88f, 0.48f),
        rimColor = Vec3(0.55f, 1.0f, 0.75f),
        uiColorHex = 0xFF29E07A,
      ),
      TeamPalette(
        id = 4,
        label = "TITAN",
        baseColor = Vec3(0.68f, 0.34f, 0.98f),
        rimColor = Vec3(0.86f, 0.65f, 1.0f),
        uiColorHex = 0xFFAD57FA,
      ),
      TeamPalette(
        id = 5,
        label = "THIEF",
        baseColor = Vec3(0.24f, 0.26f, 0.34f),
        rimColor = Vec3(1.0f, 0.36f, 0.14f),
        uiColorHex = 0xFFFF5722,
      ),
    )
}

data class BombEntity(
  val id: Int,
  val ownerId: Int,
  val x: Float,
  val z: Float,
  var fuseTimer: Float,
  val maxFuse: Float = 3.5f,
  val blastRadius: Float = 5.4f,
  val isRemote: Boolean = false,
)

class UnitEntity(
  val id: Int,
  val isHuman: Boolean,
  val palette: TeamPalette,
  var modelType: UnitModelType,
  var personality: BotPersonality = BotPersonality.AGGRESSIVE,
  var skin: UnitSkin = UnitSkin.CYBER_CYAN,
) {
  var nickname: String = palette.label
  var x = 0f
  var z = 0f
  var yaw = 0f
  var targetYaw = 0f
  var bankAngle = 0f
  var pitchAngle = 0.08f
  var hoverPhase = Random.nextFloat() * Math3D.TWO_PI
  var speed = BASE_SPEED

  var isAlive = true
  var isDissolving = false
  var dissolveProgress = 0f
  var respawnTimer = 0f
  var flashTimer = 0f
  var bombCooldown = 0f
  var stealCooldown = 0f

  // Multiplayer prediction & interpolation state
  var isRemoteHuman = false
  var remoteUserId: String? = null
  var remoteTargetX = 0f
  var remoteTargetZ = 0f
  var remoteTargetYaw = 0f
  var remoteTrailReceived = false

  var wasInsideTerritory = true
  var isInsideTerritory = true
  var kills = 0
  var territoryPercent = 0f

  val trail = ArrayList<Vec2>(64)
  var trailMeshDirty = false

  private val botAi = if (!isHuman) BotAIController(this) else null

  fun effectiveBaseColor(): Vec3 = if (isHuman || isRemoteHuman) skin.baseColor else palette.baseColor

  fun effectiveRimColor(): Vec3 = if (isHuman || isRemoteHuman) skin.rimColor else palette.rimColor

  fun effectiveSkinStyle(): Float = if (isHuman || isRemoteHuman) skin.shaderStyle else 0f

  fun spawnAt(spawnX: Float, spawnZ: Float, facingYaw: Float) {
    x = spawnX
    z = spawnZ
    remoteTargetX = spawnX
    remoteTargetZ = spawnZ
    yaw = facingYaw
    targetYaw = facingYaw
    remoteTargetYaw = facingYaw
    bankAngle = 0f
    pitchAngle = 0.08f
    isAlive = true
    isDissolving = false
    dissolveProgress = 0f
    respawnTimer = 0f
    flashTimer = 0f
    bombCooldown = 2.0f
    stealCooldown = 1.5f
    wasInsideTerritory = true
    isInsideTerritory = true
    trail.clear()
    trailMeshDirty = true
    botAi?.reset()
  }

  fun startDissolve() {
    if (isDissolving) return
    isAlive = false
    isDissolving = true
    dissolveProgress = 0f
    trail.clear()
    trailMeshDirty = true
  }

  fun updateAi(dt: Float, world: GameWorld3D) {
    if (!isRemoteHuman) {
      botAi?.update(dt, world)
    }
  }

  fun updateKinematics(dt: Float, arenaRadius: Float) {
    hoverPhase += dt * 4.2f
    if (flashTimer > 0f) {
      flashTimer = (flashTimer - dt).coerceAtLeast(0f)
    }
    if (bombCooldown > 0f) {
      bombCooldown = (bombCooldown - dt).coerceAtLeast(0f)
    }
    if (stealCooldown > 0f) {
      stealCooldown = (stealCooldown - dt).coerceAtLeast(0f)
    }

    if (isDissolving) {
      dissolveProgress = (dissolveProgress + dt * 1.35f).coerceAtMost(1f)
      return
    }
    if (!isAlive) return

    if (isRemoteHuman) {
      // Dead-reckoning prediction along last known yaw + smooth error correction
      val predX = x + sin(yaw) * speed * dt
      val predZ = z + cos(yaw) * speed * dt
      remoteTargetX += sin(remoteTargetYaw) * speed * dt
      remoteTargetZ += cos(remoteTargetYaw) * speed * dt
      val corr = (dt * 10f).coerceIn(0f, 1f)
      x = Math3D.lerp(predX, remoteTargetX, corr)
      z = Math3D.lerp(predZ, remoteTargetZ, corr)
      val prevYaw = yaw
      yaw = Math3D.lerpAngle(yaw, remoteTargetYaw, corr)
      val yawDelta = Math3D.angleDiff(prevYaw, yaw)
      bankAngle = Math3D.lerp(bankAngle, (-yawDelta * 5.0f).coerceIn(-0.55f, 0.55f), corr)
      return
    }

    val yawDelta = Math3D.angleDiff(yaw, targetYaw)
    val maxTurn = TURN_RATE * dt
    val appliedTurn = yawDelta.coerceIn(-maxTurn, maxTurn)
    yaw = Math3D.wrapAngle(yaw + appliedTurn)

    val desiredBank = (-yawDelta * 0.72f).coerceIn(-0.58f, 0.58f)
    bankAngle = Math3D.lerp(bankAngle, desiredBank, (dt * 10f).coerceIn(0f, 1f))

    val activeSpeed = if (personality == BotPersonality.THIEF) THIEF_SPEED else speed
    val fx = sin(yaw)
    val fz = cos(yaw)
    x += fx * activeSpeed * dt
    z += fz * activeSpeed * dt

    val maxR = arenaRadius - 0.9f
    val rSq = x * x + z * z
    if (rSq > maxR * maxR) {
      val r = sqrt(rSq)
      x *= maxR / r
      z *= maxR / r
      val inwardYaw = atan2(-x, -z)
      if (isHuman) {
        yaw = Math3D.lerpAngle(yaw, inwardYaw, (dt * 2.5f).coerceIn(0f, 1f))
      } else {
        targetYaw = inwardYaw
      }
    }
  }

  companion object {
    const val BASE_SPEED = 11.2f
    const val THIEF_SPEED = 12.6f
    const val TURN_RATE = 5.8f
  }
}

private class BotAIController(private val self: UnitEntity) {
  private enum class State {
    EXPAND,
    RETURN,
    HUNT,
    RAID_AND_BOMB,
  }

  private var state = State.EXPAND
  private var stateTimer = 0f
  private var curveBias = 0.75f
  private var desiredTrailPoints = 14
  private var rng = Random(self.id * 9173 + 42)

  fun reset() {
    state = if (self.personality == BotPersonality.THIEF) State.RAID_AND_BOMB else State.EXPAND
    stateTimer = 0f
    curveBias = (if (rng.nextBoolean()) 1f else -1f) * (0.45f + rng.nextFloat() * 0.65f)
    desiredTrailPoints =
      when (self.personality) {
        BotPersonality.CAUTIOUS -> 7 + rng.nextInt(5)
        BotPersonality.GREEDY -> 16 + rng.nextInt(12)
        BotPersonality.AGGRESSIVE -> 10 + rng.nextInt(8)
        BotPersonality.THIEF -> 8
      }
  }

  fun update(dt: Float, world: GameWorld3D) {
    stateTimer += dt
    val arenaR = world.territoryManager.arenaRadius
    val distFromCenter = sqrt(self.x * self.x + self.z * self.z)

    if (distFromCenter > arenaR - 5.0f) {
      self.targetYaw = atan2(-self.x, -self.z)
      if (!self.isInsideTerritory && self.trail.size > 4 && self.personality != BotPersonality.THIEF) {
        state = State.RETURN
      }
      return
    }

    // Specialized Thief Raider AI: hunts populated territories, carves loot & drops timed bombs!
    if (self.personality == BotPersonality.THIEF) {
      val targetUnit = world.units.filter { it.id != self.id && it.isAlive && it.territoryPercent > 1.0f }
        .maxByOrNull { it.territoryPercent }
      if (targetUnit != null) {
        val targetPt = world.territoryManager.nearestTerritoryPoint(targetUnit.id, self.x, self.z)
        if (targetPt != null) {
          val dx = targetPt.x - self.x
          val dz = targetPt.y - self.z
          val distSq = dx * dx + dz * dz
          if (distSq < 9.0f && self.bombCooldown <= 0f) {
            // Infiltrated target territory: steal chunk + drop armed bomb + flee!
            world.triggerThiefHeist(self, targetUnit.id)
          } else if (self.bombCooldown > 2.8f) {
            // Fleeing after planting bomb
            self.targetYaw = atan2(-dx, -dz)
          } else {
            self.targetYaw = atan2(dx, dz)
          }
          return
        }
      }
      self.targetYaw = Math3D.wrapAngle(self.targetYaw + 0.65f * dt)
      return
    }

    val huntRangeSq = if (self.personality == BotPersonality.AGGRESSIVE) 19.0f * 19.0f else 12.5f * 12.5f
    var huntTarget: Vec2? = null
    var bestHuntDistSq = huntRangeSq
    for (other in world.units) {
      if (other.id == self.id || !other.isAlive || other.trail.isEmpty()) continue
      for (i in 0 until other.trail.size step 2) {
        val pt = other.trail[i]
        val dx = pt.x - self.x
        val dz = pt.y - self.z
        val d2 = dx * dx + dz * dz
        if (d2 < bestHuntDistSq) {
          bestHuntDistSq = d2
          huntTarget = pt
        }
      }
    }

    var threatNearby = false
    if (!self.isInsideTerritory && self.trail.size > 5) {
      for (other in world.units) {
        if (other.id == self.id || !other.isAlive) continue
        val dx = other.x - self.x
        val dz = other.z - self.z
        if (dx * dx + dz * dz < 11.5f * 11.5f) {
          threatNearby = true
          break
        }
      }
    }

    if (self.isInsideTerritory) {
      val huntChance = if (self.personality == BotPersonality.AGGRESSIVE) 0.85f else 0.45f
      state = if (huntTarget != null && rng.nextFloat() < huntChance) State.HUNT else State.EXPAND
      if (stateTimer > 1.5f) {
        curveBias = (if (rng.nextBoolean()) 1f else -1f) * (0.4f + rng.nextFloat() * 0.75f)
        stateTimer = 0f
      }
    } else if (threatNearby || self.trail.size >= desiredTrailPoints) {
      state = State.RETURN
    } else if (huntTarget != null && self.trail.size < 9) {
      state = State.HUNT
    }

    when (state) {
      State.RETURN -> {
        val home = world.territoryManager.nearestTerritoryPoint(self.id, self.x, self.z)
        self.targetYaw =
          if (home != null) atan2(home.x - self.x, home.y - self.z) else atan2(-self.x, -self.z)
      }
      State.HUNT -> {
        if (huntTarget != null) {
          self.targetYaw = atan2(huntTarget.x - self.x, huntTarget.y - self.z)
        } else {
          state = State.EXPAND
        }
      }
      State.EXPAND, State.RAID_AND_BOMB -> {
        self.targetYaw = Math3D.wrapAngle(self.targetYaw + curveBias * dt)
      }
    }
  }
}

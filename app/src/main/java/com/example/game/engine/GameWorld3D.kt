package com.example.game.engine

import com.example.game.data.RoomPlayer
import com.example.game.math.Math3D
import com.example.game.math.Vec2
import com.example.game.math.Vec3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class MatchPhase {
  MENU,
  PLAYING,
  VICTORY,
  GAME_OVER,
}

data class LeaderboardEntry(
  val id: Int,
  val name: String,
  val percent: Float,
  val colorHex: Long,
  val isHuman: Boolean,
  val isAlive: Boolean,
  val isLeader: Boolean = false,
)

data class FloatingGainPopup(
  val id: Long,
  val text: String,
  val colorHex: Long,
  var age: Float = 0f,
  val maxAge: Float = 1.4f,
)

data class MinimapUnitDot(
  val normX: Float,
  val normZ: Float,
  val colorHex: Long,
  val isHuman: Boolean,
  val isThief: Boolean,
)

data class MinimapBombDot(
  val normX: Float,
  val normZ: Float,
  val pulse: Float,
)

data class GameHudState(
  val phase: MatchPhase = MatchPhase.MENU,
  val selectedModel: UnitModelType = UnitModelType.CYBER_DRONE,
  val selectedSkin: UnitSkin = UnitSkin.CYBER_CYAN,
  val playerTerritoryPercent: Float = 0f,
  val bestTerritoryPercent: Float = 0f,
  val playerKills: Int = 0,
  val matchCoinsEarned: Long = 0L,
  val profileCoins: Long = 100L,
  val unlockedSkins: List<String> = listOf("CYBER_CYAN"),
  val leaderboard: List<LeaderboardEntry> = emptyList(),
  val eliminationBanner: String? = null,
  val floatingPopups: List<FloatingGainPopup> = emptyList(),
  val minimapCells: List<Triple<Float, Float, Int>> = emptyList(),
  val minimapUnits: List<MinimapUnitDot> = emptyList(),
  val minimapBombs: List<MinimapBombDot> = emptyList(),
  val isMultiplayer: Boolean = false,
  val activeRoomCode: String? = null,
  val humanPlayersCount: Int = 1,
)

data class BurstParticle(
  var x: Float,
  var y: Float,
  var z: Float,
  var vx: Float,
  var vy: Float,
  var vz: Float,
  var rotY: Float,
  var rotSpeed: Float,
  var life: Float,
  val maxLife: Float,
  val color: Vec3,
  val scale: Float,
  val isConfetti: Boolean = false,
)

/**
 * Central 3D simulation engine managing territory capture, Thief Raider & timed Bombs,
 * trail sparks, confetti victory, bot personalities, and multiplayer state synchronization.
 */
class GameWorld3D {
  val territoryManager = Territory3DManager()

  val units: List<UnitEntity> =
    listOf(
      UnitEntity(0, true, GamePalettes.ALL[0], UnitModelType.CYBER_DRONE, BotPersonality.AGGRESSIVE),
      UnitEntity(1, false, GamePalettes.ALL[1], UnitModelType.HEX_CRUISER, BotPersonality.AGGRESSIVE),
      UnitEntity(2, false, GamePalettes.ALL[2], UnitModelType.CYBER_DRONE, BotPersonality.GREEDY),
      UnitEntity(3, false, GamePalettes.ALL[3], UnitModelType.HEX_CRUISER, BotPersonality.CAUTIOUS),
      UnitEntity(4, false, GamePalettes.ALL[4], UnitModelType.CYBER_DRONE, BotPersonality.AGGRESSIVE),
      UnitEntity(5, false, GamePalettes.ALL[5], UnitModelType.THIEF_RAIDER, BotPersonality.THIEF),
    )

  val player: UnitEntity
    get() = units[0]

  val particles = ArrayList<BurstParticle>(192)
  val bombs = ArrayList<BombEntity>(8)

  @Volatile var phase: MatchPhase = MatchPhase.MENU
    private set

  @Volatile var selectedModel: UnitModelType = UnitModelType.CYBER_DRONE
    private set

  @Volatile var selectedSkin: UnitSkin = UnitSkin.CYBER_CYAN
    private set

  @Volatile var leaderUnitId: Int = 0
    private set

  var onMatchCompleted: ((coinsEarned: Long, bestPct: Double, kills: Long) -> Unit)? = null

  private var joystickActive = false
  private var joystickDx = 0f
  private var joystickDz = 0f
  private var bestPercent = 0f
  private var hudSyncAccumulator = 0f
  private var sparkAccumulator = 0f
  private var confettiAccumulator = 0f
  private var bannerText: String? = null
  private var bannerTimer = 0f
  private var elapsedTime = 0f
  private var nextBombId = 1
  private var nextPopupId = 1L
  private val activePopups = ArrayList<FloatingGainPopup>(8)

  private var matchCoinsEarned = 0L
  private var profileCoins = 100L
  private var unlockedSkins: List<String> = listOf("CYBER_CYAN")
  private var matchRewardSubmitted = false

  private var isMultiplayer = false
  private var activeRoomCode: String? = null

  private val stateLock = Any()
  private val _hudState = MutableStateFlow(GameHudState())
  val hudState: StateFlow<GameHudState> = _hudState.asStateFlow()

  init {
    setupDemoArena()
  }

  fun withLock(block: () -> Unit) {
    synchronized(stateLock) {
      block()
    }
  }

  fun syncProfileMeta(
    coins: Long,
    unlocked: List<String>,
    equippedSkinId: String,
    equippedModelName: String,
    displayName: String,
  ) {
    synchronized(stateLock) {
      profileCoins = coins
      unlockedSkins = unlocked.ifEmpty { listOf("CYBER_CYAN") }
      val skin = UnitSkin.fromId(equippedSkinId)
      selectedSkin = skin
      player.skin = skin
      val model = UnitModelType.entries.firstOrNull { it.name == equippedModelName } ?: UnitModelType.CYBER_DRONE
      selectedModel = model
      player.modelType = model
      player.nickname = displayName.trim().ifEmpty { "YOU" }.take(12)
      publishHudLocked()
    }
  }

  fun setMultiplayerRoomInfo(enabled: Boolean, roomCode: String?) {
    synchronized(stateLock) {
      isMultiplayer = enabled
      activeRoomCode = roomCode
      if (!enabled) {
        for (i in 1..4) {
          units[i].isRemoteHuman = false
          units[i].remoteUserId = null
          units[i].nickname = units[i].palette.label
        }
      }
      publishHudLocked()
    }
  }

  fun setJoystickInput(active: Boolean, dx: Float, dyScreen: Float) {
    synchronized(stateLock) {
      joystickActive = active
      if (active && (dx * dx + dyScreen * dyScreen) > 0.01f) {
        joystickDx = dx
        joystickDz = dyScreen
      }
    }
  }

  fun selectPlayerModel(model: UnitModelType) {
    synchronized(stateLock) {
      selectedModel = model
      player.modelType = model
      publishHudLocked()
    }
  }

  fun selectPlayerSkin(skin: UnitSkin) {
    synchronized(stateLock) {
      selectedSkin = skin
      player.skin = skin
      if (skin == UnitSkin.WATERMELON) {
        selectedModel = UnitModelType.WATERMELON_SLICE
        player.modelType = UnitModelType.WATERMELON_SLICE
      }
      publishHudLocked()
    }
  }

  fun startNewMatch() {
    synchronized(stateLock) {
      phase = MatchPhase.PLAYING
      bannerText = null
      bannerTimer = 0f
      matchCoinsEarned = 0L
      matchRewardSubmitted = false
      particles.clear()
      bombs.clear()
      activePopups.clear()
      territoryManager.resetArena()
      player.modelType = selectedModel
      player.skin = selectedSkin

      val spawnRadius = 21.0f
      for (i in 0..4) {
        val unit = units[i]
        unit.kills = 0
        val angle = (i.toFloat() / 5.0f) * Math3D.TWO_PI
        val sx = if (i == 0) 0f else cos(angle) * spawnRadius
        val sz = if (i == 0) 0f else sin(angle) * spawnRadius
        val facing = if (i == 0) -PI.toFloat() * 0.5f else atan2(-sx, -sz)
        unit.spawnAt(sx, sz, facing)
        territoryManager.spawnBase(unit.id, sx, sz, baseRadius = 3.8f)
      }

      // Spawn Thief Raider on the outer patrol ring (no permanent territory base)
      val thief = units[5]
      thief.kills = 0
      thief.spawnAt(29f, 0f, PI.toFloat())

      refreshTerritoryStatsLocked()
      publishHudLocked()
    }
  }

  fun returnToMenu() {
    synchronized(stateLock) {
      bannerText = null
      bannerTimer = 0f
      particles.clear()
      bombs.clear()
      activePopups.clear()
      setupDemoArena()
    }
  }

  fun dropPlayerBomb() {
    synchronized(stateLock) {
      if (phase != MatchPhase.PLAYING || !player.isAlive || player.bombCooldown > 0f) return
      bombs.add(BombEntity(id = nextBombId++, ownerId = player.id, x = player.x, z = player.z, fuseTimer = 3.2f))
      player.bombCooldown = 6.0f
      addPopupLocked("BOMB ARMED!", 0xFFFF5722)
    }
  }

  fun triggerThiefHeist(thief: UnitEntity, victimId: Int) {
    val victim = units.getOrNull(victimId) ?: return
    val wiped = territoryManager.carveCrater(thief.x, thief.z, 2.3f)
    bombs.add(BombEntity(id = nextBombId++, ownerId = thief.id, x = thief.x, z = thief.z, fuseTimer = 3.5f))
    thief.bombCooldown = 7.5f
    spawnBurst(thief.x, 0.5f, thief.z, Vec3(1f, 0.45f, 0.12f), count = 14)

    if (victim.isHuman && phase == MatchPhase.PLAYING) {
      bannerText = "THIEF PLANTED A BOMB!"
      bannerTimer = 2.0f
    }
    for (wipedId in wiped) {
      val w = units.getOrNull(wipedId)
      if (w != null && w.isAlive) {
        eliminateUnitLocked(w, killer = thief, reason = "THIEF HEIST")
      }
    }
    refreshTerritoryStatsLocked()
  }

  /**
   * Synchronizes remote human players from Firestore snapshot into arena slots 1..4
   * with smooth dead-reckoning targets and remote trail/bomb states.
   */
  fun applyRemotePlayers(remotePlayers: List<RoomPlayer>, localUid: String) {
    synchronized(stateLock) {
      val others = remotePlayers.filter { it.userId != localUid && it.userId.isNotBlank() }.take(4)
      val usedSlots = HashSet<Int>()

      for ((idx, remote) in others.withIndex()) {
        val slot = (idx + 1).coerceIn(1, 4)
        usedSlots.add(slot)
        val unit = units[slot]
        val firstAttach = !unit.isRemoteHuman || unit.remoteUserId != remote.userId
        unit.isRemoteHuman = true
        unit.remoteUserId = remote.userId
        unit.nickname = remote.displayName.take(12)
        unit.skin = UnitSkin.fromId(remote.skinId)
        unit.modelType = UnitModelType.entries.firstOrNull { it.name == remote.modelType } ?: UnitModelType.CYBER_DRONE

        if (firstAttach) {
          unit.spawnAt(remote.x.toFloat(), remote.z.toFloat(), remote.yaw.toFloat())
          territoryManager.spawnBase(unit.id, unit.x, unit.z, baseRadius = 3.6f)
        } else {
          unit.remoteTargetX = remote.x.toFloat()
          unit.remoteTargetZ = remote.z.toFloat()
          unit.remoteTargetYaw = remote.yaw.toFloat()
          unit.speed = remote.speed.toFloat().coerceIn(4f, 22f)
          if (!remote.alive && unit.isAlive) {
            unit.startDissolve()
            territoryManager.clearOwner(unit.id)
          } else if (remote.alive && !unit.isAlive && !unit.isDissolving) {
            unit.spawnAt(remote.x.toFloat(), remote.z.toFloat(), remote.yaw.toFloat())
            territoryManager.spawnBase(unit.id, unit.x, unit.z, baseRadius = 3.5f)
          }
        }

        // Sync remote trail polyline when present
        if (remote.trailFlat.size >= 4) {
          val nextTrail = ArrayList<Vec2>(remote.trailFlat.size / 2)
          var k = 0
          while (k + 1 < remote.trailFlat.size) {
            nextTrail.add(Vec2(remote.trailFlat[k].toFloat(), remote.trailFlat[k + 1].toFloat()))
            k += 2
          }
          unit.trail.clear()
          unit.trail.addAll(nextTrail)
          unit.trailMeshDirty = true
        }

        // Sync remote active bomb if any
        if (remote.bombActive && bombs.none { it.isRemote && it.ownerId == unit.id }) {
          bombs.add(
            BombEntity(
              id = nextBombId++,
              ownerId = unit.id,
              x = remote.bombX.toFloat(),
              z = remote.bombZ.toFloat(),
              fuseTimer = remote.bombTimer.toFloat().coerceIn(0.4f, 3.8f),
              isRemote = true,
            )
          )
        }
      }

      // Unoccupied slots revert seamlessly to AI bots
      for (slot in 1..4) {
        if (!usedSlots.contains(slot) && units[slot].isRemoteHuman) {
          units[slot].isRemoteHuman = false
          units[slot].remoteUserId = null
          units[slot].nickname = units[slot].palette.label
        }
      }
    }
  }

  fun buildLocalRoomPlayerState(uid: String, displayName: String, slotIndex: Int): RoomPlayer {
    synchronized(stateLock) {
      val flat = ArrayList<Double>(player.trail.size * 2)
      val step = if (player.trail.size > 70) 2 else 1
      for (i in 0 until player.trail.size step step) {
        flat.add(player.trail[i].x.toDouble())
        flat.add(player.trail[i].y.toDouble())
      }
      val myBomb = bombs.firstOrNull { it.ownerId == player.id }
      return RoomPlayer(
        userId = uid,
        displayName = displayName.ifEmpty { "Pilot" }.take(30),
        slotIndex = slotIndex.toLong(),
        x = player.x.toDouble(),
        z = player.z.toDouble(),
        yaw = player.yaw.toDouble(),
        speed = player.speed.toDouble(),
        alive = player.isAlive,
        kills = player.kills.toLong(),
        territoryPct = player.territoryPercent.toDouble(),
        skinId = selectedSkin.id,
        modelType = selectedModel.name,
        trailFlat = flat,
        bombActive = myBomb != null,
        bombX = (myBomb?.x ?: 0f).toDouble(),
        bombZ = (myBomb?.z ?: 0f).toDouble(),
        bombTimer = (myBomb?.fuseTimer ?: 0f).toDouble(),
        roomOpen = true,
      )
    }
  }

  private fun setupDemoArena() {
    synchronized(stateLock) {
      phase = MatchPhase.MENU
      territoryManager.resetArena()
      val spawnRadius = 20.0f
      for (i in 0..4) {
        val unit = units[i]
        val angle = (i.toFloat() / 5.0f) * Math3D.TWO_PI
        val sx = cos(angle) * spawnRadius
        val sz = sin(angle) * spawnRadius
        unit.spawnAt(sx, sz, atan2(-sx, -sz))
        territoryManager.spawnBase(unit.id, sx, sz, baseRadius = 3.8f)
      }
      units[5].spawnAt(28f, 0f, PI.toFloat())
      refreshTerritoryStatsLocked()
      publishHudLocked()
    }
  }

  fun update(rawDt: Float): Float {
    val dt = rawDt.coerceIn(0.001f, 0.045f)
    synchronized(stateLock) {
      elapsedTime += dt

      if (bannerTimer > 0f) {
        bannerTimer -= dt
        if (bannerTimer <= 0f) bannerText = null
      }

      // Age floating gain popups
      val pIter = activePopups.iterator()
      while (pIter.hasNext()) {
        val pop = pIter.next()
        pop.age += dt
        if (pop.age >= pop.maxAge) pIter.remove()
      }

      // Victory 3D Confetti fountain!
      if (phase == MatchPhase.VICTORY) {
        confettiAccumulator += dt
        if (confettiAccumulator >= 0.12f) {
          confettiAccumulator = 0f
          spawnConfetti(player.x, 4.5f, player.z, count = 8)
        }
      }

      if (phase == MatchPhase.PLAYING && player.isAlive) {
        if (joystickActive && (joystickDx * joystickDx + joystickDz * joystickDz) > 0.01f) {
          player.targetYaw = Math3D.yawFromDirection(joystickDx, joystickDz)
        }
      } else if (phase == MatchPhase.MENU) {
        player.targetYaw = Math3D.wrapAngle(player.targetYaw + 0.38f * dt)
      }

      // Trail movement sparks
      sparkAccumulator += dt
      val emitSparks = sparkAccumulator >= 0.09f
      if (emitSparks) sparkAccumulator = 0f

      for (unit in units) {
        if (!unit.isAlive) {
          if (unit.isDissolving) {
            unit.updateKinematics(dt, territoryManager.arenaRadius)
            if (unit.dissolveProgress >= 1f) unit.isDissolving = false
          } else if (!unit.isHuman && !unit.isRemoteHuman && (phase == MatchPhase.PLAYING || phase == MatchPhase.MENU)) {
            unit.respawnTimer -= dt
            if (unit.respawnTimer <= 0f) respawnBotLocked(unit)
          }
          continue
        }

        if (!unit.isHuman) {
          unit.updateAi(dt, this)
        }
        unit.updateKinematics(dt, territoryManager.arenaRadius)

        // Thief leaves a short lethal trail too; doesn't own territory!
        if (unit.personality == BotPersonality.THIEF) {
          if (emitSparks) {
            spawnTrailSpark(unit.x, unit.z, unit.effectiveRimColor())
          }
          val currPt = Vec2(unit.x, unit.z)
          val last = unit.trail.lastOrNull()
          if (last == null || (currPt - last).lengthSq() >= TRAIL_SAMPLE_DIST_SQ) {
            unit.trail.add(currPt)
            if (unit.trail.size > 14) unit.trail.removeAt(0)
            unit.trailMeshDirty = true
          }
          continue
        }

        val inside = territoryManager.isInsideTerritory(unit.id, unit.x, unit.z)
        unit.isInsideTerritory = inside

        if (!inside) {
          if (emitSparks) {
            spawnTrailSpark(unit.x, unit.z, unit.effectiveRimColor())
          }
          val currPt = Vec2(unit.x, unit.z)
          if (unit.wasInsideTerritory) {
            val back = Math3D.forwardFromYaw(unit.yaw) * -0.55f
            unit.trail.add(Vec2(unit.x + back.x, unit.z + back.y))
            unit.trail.add(currPt)
            unit.trailMeshDirty = true
          } else {
            val last = unit.trail.lastOrNull()
            if (last == null || (currPt - last).lengthSq() >= TRAIL_SAMPLE_DIST_SQ) {
              unit.trail.add(currPt)
              unit.trailMeshDirty = true
            }
          }
        } else if (!unit.wasInsideTerritory && unit.trail.isNotEmpty()) {
          unit.trail.add(Vec2(unit.x, unit.z))
          val prevPct = territoryManager.getTerritoryPercent(unit.id)
          val wipedEnemies = territoryManager.captureLoop(unit.id, unit.trail)
          unit.trail.clear()
          unit.trailMeshDirty = true

          val gainedPct = (territoryManager.getTerritoryPercent(unit.id) - prevPct).coerceAtLeast(0f)
          if (gainedPct > 0.05f) {
            unit.flashTimer = 0.65f
            spawnBurst(unit.x, 0.35f, unit.z, unit.effectiveRimColor(), count = 16)
            if (unit.isHuman && phase == MatchPhase.PLAYING) {
              val gainCoins = (gainedPct * 2f).toLong().coerceAtLeast(1L)
              matchCoinsEarned += gainCoins
              addPopupLocked(String.format(Locale.US, "+%.2f%%", gainedPct), 0xFF67E8F9)
            }
          }

          for (wipedId in wipedEnemies) {
            val victim = units.getOrNull(wipedId)
            if (victim != null && victim.isAlive) {
              eliminateUnitLocked(victim, killer = unit, reason = "TERRITORY OVERRUN")
            }
          }
          refreshTerritoryStatsLocked()

          if (phase == MatchPhase.PLAYING && player.territoryPercent >= 98.5f) {
            triggerVictoryLocked()
          }
        }

        unit.wasInsideTerritory = inside
      }

      updateBombsLocked(dt)
      resolveCollisionsLocked()
      updateParticlesLocked(dt)

      hudSyncAccumulator += dt
      if (hudSyncAccumulator >= 0.08f) {
        hudSyncAccumulator = 0f
        refreshTerritoryStatsLocked()
        publishHudLocked()
      }
    }
    return elapsedTime
  }

  private fun updateBombsLocked(dt: Float) {
    val iter = bombs.iterator()
    while (iter.hasNext()) {
      val b = iter.next()
      b.fuseTimer -= dt
      if (b.fuseTimer <= 0f) {
        iter.remove()
        detonateBombLocked(b)
      }
    }
  }

  private fun detonateBombLocked(bomb: BombEntity) {
    spawnBurst(bomb.x, 0.6f, bomb.z, Vec3(1.0f, 0.38f, 0.10f), count = 34)
    val wiped = territoryManager.carveCrater(bomb.x, bomb.z, bomb.blastRadius)
    val rSq = bomb.blastRadius * bomb.blastRadius

    // Check units & trails caught inside blast radius
    val attacker = units.getOrNull(bomb.ownerId)
    for (unit in units) {
      if (!unit.isAlive || unit.id == bomb.ownerId) continue
      val dx = unit.x - bomb.x
      val dz = unit.z - bomb.z
      if (dx * dx + dz * dz <= rSq) {
        eliminateUnitLocked(unit, killer = attacker, reason = "BOMB BLAST")
        continue
      }
      // Check if unit's exposed trail crosses the blast radius
      if (unit.trail.any { pt -> (pt.x - bomb.x) * (pt.x - bomb.x) + (pt.y - bomb.z) * (pt.y - bomb.z) <= rSq }) {
        eliminateUnitLocked(unit, killer = attacker, reason = "TRAIL BLASTED")
      }
    }

    for (wipedId in wiped) {
      val v = units.getOrNull(wipedId)
      if (v != null && v.isAlive) {
        eliminateUnitLocked(v, killer = attacker, reason = "CRATER WIPEOUT")
      }
    }
    refreshTerritoryStatsLocked()
  }

  private fun resolveCollisionsLocked() {
    for (attacker in units) {
      if (!attacker.isAlive) continue
      val p = Vec2(attacker.x, attacker.z)

      for (victim in units) {
        if (victim.id == attacker.id || !victim.isAlive || victim.trail.size < 2) continue

        var hit = false
        for (i in 0 until victim.trail.size - 1) {
          if (Math3D.distSqPointToSegment(p, victim.trail[i], victim.trail[i + 1]) < TRAIL_HIT_RADIUS_SQ) {
            hit = true
            break
          }
        }
        if (!hit && victim.trail.isNotEmpty()) {
          val tail = victim.trail.last()
          if (Math3D.distSqPointToSegment(p, tail, Vec2(victim.x, victim.z)) < TRAIL_HIT_RADIUS_SQ) {
            hit = true
          }
        }

        if (hit) {
          eliminateUnitLocked(victim, killer = attacker, reason = "TRAIL SEVERED")
          break
        }
      }
    }

    for (i in units.indices) {
      val a = units[i]
      if (!a.isAlive) continue
      for (j in i + 1 until units.size) {
        val b = units[j]
        if (!b.isAlive) continue

        val dx = a.x - b.x
        val dz = a.z - b.z
        val distSq = dx * dx + dz * dz
        if (distSq < HEAD_ON_RADIUS_SQ) {
          val aIn = a.isInsideTerritory
          val bIn = b.isInsideTerritory
          when {
            aIn && !bIn -> eliminateUnitLocked(b, killer = a, reason = "DEFENSE TAKEDOWN")
            bIn && !aIn -> eliminateUnitLocked(a, killer = b, reason = "DEFENSE TAKEDOWN")
            !aIn && !bIn -> {
              val distA = territoryManager.distanceSqToTerritory(a.id, a.x, a.z)
              val distB = territoryManager.distanceSqToTerritory(b.id, b.x, b.z)
              if (distA <= distB) {
                eliminateUnitLocked(b, killer = a, reason = "HEAD-ON CLASH")
              } else {
                eliminateUnitLocked(a, killer = b, reason = "HEAD-ON CLASH")
              }
            }
            else -> {
              val d = sqrt(distSq).coerceAtLeast(0.01f)
              val push = (1.5f - d) * 0.5f
              a.x += (dx / d) * push
              a.z += (dz / d) * push
              b.x -= (dx / d) * push
              b.z -= (dz / d) * push
            }
          }
        }
      }
    }
  }

  private fun eliminateUnitLocked(victim: UnitEntity, killer: UnitEntity?, reason: String) {
    if (!victim.isAlive) return
    spawnBurst(victim.x, 0.5f, victim.z, victim.effectiveRimColor(), count = 28)
    victim.startDissolve()
    if (victim.personality != BotPersonality.THIEF) {
      territoryManager.clearOwner(victim.id)
    }

    if (killer != null && killer.id != victim.id) {
      killer.kills++
      if (killer.isHuman && phase == MatchPhase.PLAYING) {
        val bounty = if (victim.personality == BotPersonality.THIEF) 25L else 15L
        matchCoinsEarned += bounty
        bannerText = "$reason: ${victim.nickname}!"
        bannerTimer = 2.0f
        addPopupLocked("+$bounty COINS", 0xFFFACC15)
      }
    }

    if (victim.isHuman) {
      if (phase == MatchPhase.PLAYING) {
        phase = MatchPhase.GAME_OVER
        bannerText = reason
        bannerTimer = 2.5f
        submitMatchRewardsLocked()
        publishHudLocked()
      } else if (phase == MatchPhase.MENU) {
        respawnBotLocked(victim)
      }
    } else if (!victim.isRemoteHuman) {
      victim.respawnTimer = if (victim.personality == BotPersonality.THIEF) 4.0f else 2.6f
    }
    refreshTerritoryStatsLocked()
  }

  private fun triggerVictoryLocked() {
    if (phase == MatchPhase.VICTORY) return
    phase = MatchPhase.VICTORY
    matchCoinsEarned += 100L
    addPopupLocked("+100 WIN COINS!", 0xFF29E07A)
    spawnConfetti(player.x, 5.0f, player.z, count = 45)
    submitMatchRewardsLocked()
    publishHudLocked()
  }

  private fun submitMatchRewardsLocked() {
    if (matchRewardSubmitted) return
    matchRewardSubmitted = true
    val coins = matchCoinsEarned
    val best = bestPercent.toDouble()
    val kills = player.kills.toLong()
    onMatchCompleted?.invoke(coins, best, kills)
  }

  private fun respawnBotLocked(bot: UnitEntity) {
    val maxR = territoryManager.arenaRadius - 8.5f
    var bestX = 0f
    var bestZ = 0f
    var bestMinDistSq = -1f

    for (attempt in 0 until 12) {
      val angle = Random.nextFloat() * Math3D.TWO_PI
      val rad = 8f + Random.nextFloat() * (maxR - 8f)
      val cx = cos(angle) * rad
      val cz = sin(angle) * rad
      var minDist = Float.MAX_VALUE
      for (u in units) {
        if (!u.isAlive) continue
        val dx = u.x - cx
        val dz = u.z - cz
        val d2 = dx * dx + dz * dz
        if (d2 < minDist) minDist = d2
      }
      if (minDist > bestMinDistSq) {
        bestMinDistSq = minDist
        bestX = cx
        bestZ = cz
      }
    }

    bot.spawnAt(bestX, bestZ, atan2(-bestX, -bestZ))
    if (bot.personality != BotPersonality.THIEF) {
      territoryManager.spawnBase(bot.id, bestX, bestZ, baseRadius = 3.4f)
    }
  }

  private fun addPopupLocked(text: String, colorHex: Long) {
    activePopups.add(FloatingGainPopup(id = nextPopupId++, text = text, colorHex = colorHex))
    if (activePopups.size > 5) activePopups.removeAt(0)
  }

  private fun spawnTrailSpark(x: Float, z: Float, color: Vec3) {
    if (particles.size > 170) return
    particles.add(
      BurstParticle(
        x = x + (Random.nextFloat() - 0.5f) * 0.4f,
        y = 0.25f,
        z = z + (Random.nextFloat() - 0.5f) * 0.4f,
        vx = (Random.nextFloat() - 0.5f) * 2.2f,
        vy = 1.6f + Random.nextFloat() * 1.8f,
        vz = (Random.nextFloat() - 0.5f) * 2.2f,
        rotY = Random.nextFloat() * Math3D.TWO_PI,
        rotSpeed = 10f,
        life = 0f,
        maxLife = 0.35f,
        color = color,
        scale = 0.11f,
      )
    )
  }

  private fun spawnConfetti(x: Float, y: Float, z: Float, count: Int) {
    val palette = listOf(
      Vec3(1f, 0.25f, 0.45f),
      Vec3(0.2f, 0.9f, 1f),
      Vec3(1f, 0.85f, 0.2f),
      Vec3(0.3f, 0.95f, 0.5f),
      Vec3(0.75f, 0.4f, 1f),
    )
    repeat(count) {
      val angle = Random.nextFloat() * Math3D.TWO_PI
      val spd = 2.0f + Random.nextFloat() * 6.0f
      particles.add(
        BurstParticle(
          x = x + (Random.nextFloat() - 0.5f) * 6f,
          y = y + Random.nextFloat() * 2.5f,
          z = z + (Random.nextFloat() - 0.5f) * 6f,
          vx = cos(angle) * spd,
          vy = 2.0f + Random.nextFloat() * 4.5f,
          vz = sin(angle) * spd,
          rotY = Random.nextFloat() * Math3D.TWO_PI,
          rotSpeed = (Random.nextFloat() - 0.5f) * 16f,
          life = 0f,
          maxLife = 1.8f,
          color = palette[Random.nextInt(palette.size)],
          scale = 0.20f,
          isConfetti = true,
        )
      )
    }
  }

  private fun spawnBurst(x: Float, y: Float, z: Float, color: Vec3, count: Int) {
    for (i in 0 until count) {
      val angle = Random.nextFloat() * Math3D.TWO_PI
      val speed = 3.5f + Random.nextFloat() * 8.5f
      val vy = 2.5f + Random.nextFloat() * 6.5f
      particles.add(
        BurstParticle(
          x = x,
          y = y,
          z = z,
          vx = cos(angle) * speed,
          vy = vy,
          vz = sin(angle) * speed,
          rotY = Random.nextFloat() * Math3D.TWO_PI,
          rotSpeed = (Random.nextFloat() - 0.5f) * 14f,
          life = 0f,
          maxLife = 0.55f + Random.nextFloat() * 0.45f,
          color = color,
          scale = 0.16f + Random.nextFloat() * 0.22f,
        )
      )
    }
  }

  private fun updateParticlesLocked(dt: Float) {
    val iter = particles.iterator()
    while (iter.hasNext()) {
      val p = iter.next()
      p.life += dt
      if (p.life >= p.maxLife) {
        iter.remove()
        continue
      }
      p.x += p.vx * dt
      p.y += p.vy * dt
      p.z += p.vz * dt
      val gravity = if (p.isConfetti) 4.2f else 14.5f
      p.vy -= gravity * dt
      if (p.y < 0.08f) {
        p.y = 0.08f
        p.vy = -p.vy * 0.35f
      }
      p.rotY += p.rotSpeed * dt
    }
  }

  private fun refreshTerritoryStatsLocked() {
    var topId = 0
    var topPct = -1f
    for (unit in units) {
      if (unit.personality == BotPersonality.THIEF) {
        unit.territoryPercent = 0f
        continue
      }
      val pct = if (unit.isAlive) territoryManager.getTerritoryPercent(unit.id) else 0f
      unit.territoryPercent = pct
      if (unit.isAlive && pct > topPct) {
        topPct = pct
        topId = unit.id
      }
    }
    leaderUnitId = topId
    if (phase == MatchPhase.PLAYING && player.territoryPercent > bestPercent) {
      bestPercent = player.territoryPercent
    }
  }

  private fun publishHudLocked() {
    val entries =
      units
        .filter { it.personality != BotPersonality.THIEF }
        .sortedByDescending { it.territoryPercent }
        .map { u ->
          LeaderboardEntry(
            id = u.id,
            name = u.nickname,
            percent = u.territoryPercent,
            colorHex = if (u.isHuman || u.isRemoteHuman) u.skin.uiColorHex else u.palette.uiColorHex,
            isHuman = u.isHuman,
            isAlive = u.isAlive,
            isLeader = u.id == leaderUnitId && u.territoryPercent > 0.1f,
          )
        }

    val arenaR = territoryManager.arenaRadius
    val unitDots =
      units.filter { it.isAlive }.map { u ->
        MinimapUnitDot(
          normX = (u.x / arenaR).coerceIn(-1f, 1f),
          normZ = (u.z / arenaR).coerceIn(-1f, 1f),
          colorHex = if (u.isHuman || u.isRemoteHuman) u.skin.uiColorHex else u.palette.uiColorHex,
          isHuman = u.isHuman,
          isThief = u.personality == BotPersonality.THIEF,
        )
      }
    val bombDots =
      bombs.map { b ->
        MinimapBombDot(
          normX = (b.x / arenaR).coerceIn(-1f, 1f),
          normZ = (b.z / arenaR).coerceIn(-1f, 1f),
          pulse = (1f - (b.fuseTimer / b.maxFuse)).coerceIn(0f, 1f),
        )
      }
    val humansCount = 1 + units.count { it.isRemoteHuman }

    _hudState.value =
      GameHudState(
        phase = phase,
        selectedModel = selectedModel,
        selectedSkin = selectedSkin,
        playerTerritoryPercent = player.territoryPercent,
        bestTerritoryPercent = bestPercent,
        playerKills = player.kills,
        matchCoinsEarned = matchCoinsEarned,
        profileCoins = profileCoins,
        unlockedSkins = unlockedSkins,
        leaderboard = entries,
        eliminationBanner = bannerText,
        floatingPopups = activePopups.toList(),
        minimapCells = territoryManager.sampleMinimapCells(stride = 4),
        minimapUnits = unitDots,
        minimapBombs = bombDots,
        isMultiplayer = isMultiplayer,
        activeRoomCode = activeRoomCode,
        humanPlayersCount = humansCount,
      )
  }

  companion object {
    private const val TRAIL_SAMPLE_DIST_SQ = 0.45f * 0.45f
    private const val TRAIL_HIT_RADIUS_SQ = 0.85f * 0.85f
    private const val HEAD_ON_RADIUS_SQ = 1.48f * 1.48f
  }
}

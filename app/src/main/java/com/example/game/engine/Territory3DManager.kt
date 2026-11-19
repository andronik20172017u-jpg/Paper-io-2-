package com.example.game.engine

import com.example.game.graphics.MeshData
import com.example.game.math.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Manages the circular 3D arena territory grid, flood-fill loop capture, enemy land stealing,
 * Marching Squares contour extraction, Taubin smoothing, Earcut triangulation, and
 * 3D extruded slab mesh construction (top animated surface + beveled side walls).
 */
class Territory3DManager(
  val arenaRadius: Float = DEFAULT_ARENA_RADIUS,
  val gridSize: Int = DEFAULT_GRID_SIZE,
) {
  companion object {
    const val DEFAULT_ARENA_RADIUS = 38.0f
    const val DEFAULT_GRID_SIZE = 108
    const val SLAB_HEIGHT = 0.24f
    const val EMPTY_OWNER: Byte = -1
    const val OUTSIDE_ARENA: Byte = -2
  }

  private val cellSize = (arenaRadius * 2f) / gridSize
  private val origin = -arenaRadius

  private val owners = ByteArray(gridSize * gridSize)
  private val validArenaMask = BooleanArray(gridSize * gridSize)
  private val totalArenaCells: Int

  // Reusable BFS buffers for zero-GC capture
  private val visited = BooleanArray((gridSize + 2) * (gridSize + 2))
  private val trailMask = BooleanArray(gridSize * gridSize)
  private val bfsQueue = IntArray((gridSize + 2) * (gridSize + 2))

  private val dirtyOwners = BooleanArray(16)
  private val ownerCellCounts = IntArray(16)

  init {
    var count = 0
    for (gz in 0 until gridSize) {
      for (gx in 0 until gridSize) {
        val wx = origin + (gx + 0.5f) * cellSize
        val wz = origin + (gz + 0.5f) * cellSize
        val inside = (wx * wx + wz * wz) <= (arenaRadius - cellSize * 0.4f).let { it * it }
        val idx = gz * gridSize + gx
        validArenaMask[idx] = inside
        owners[idx] = if (inside) EMPTY_OWNER else OUTSIDE_ARENA
        if (inside) count++
      }
    }
    totalArenaCells = count.coerceAtLeast(1)
  }

  fun resetArena() {
    for (i in owners.indices) {
      owners[i] = if (validArenaMask[i]) EMPTY_OWNER else OUTSIDE_ARENA
    }
    ownerCellCounts.fill(0)
    dirtyOwners.fill(false)
  }

  fun spawnBase(ownerId: Int, centerX: Float, centerZ: Float, baseRadius: Float = 3.6f) {
    val rSq = baseRadius * baseRadius
    val minGx = max(0, worldToGridX(centerX - baseRadius - cellSize))
    val maxGx = min(gridSize - 1, worldToGridX(centerX + baseRadius + cellSize))
    val minGz = max(0, worldToGridZ(centerZ - baseRadius - cellSize))
    val maxGz = min(gridSize - 1, worldToGridZ(centerZ + baseRadius + cellSize))

    for (gz in minGz..maxGz) {
      for (gx in minGx..maxGx) {
        val idx = gz * gridSize + gx
        if (!validArenaMask[idx]) continue
        val wx = origin + (gx + 0.5f) * cellSize
        val wz = origin + (gz + 0.5f) * cellSize
        val dx = wx - centerX
        val dz = wz - centerZ
        if (dx * dx + dz * dz <= rSq) {
          val prev = owners[idx].toInt()
          if (prev >= 0 && prev != ownerId) {
            ownerCellCounts[prev] = (ownerCellCounts[prev] - 1).coerceAtLeast(0)
            dirtyOwners[prev] = true
          }
          if (prev != ownerId) {
            owners[idx] = ownerId.toByte()
            ownerCellCounts[ownerId]++
            dirtyOwners[ownerId] = true
          }
        }
      }
    }
  }

  fun clearOwner(ownerId: Int) {
    var changed = false
    for (i in owners.indices) {
      if (owners[i].toInt() == ownerId) {
        owners[i] = EMPTY_OWNER
        changed = true
      }
    }
    ownerCellCounts[ownerId] = 0
    if (changed) {
      dirtyOwners[ownerId] = true
    }
  }

  /**
   * Blasts a circular crater out of any territory inside [radius] (used by Thief Bomb detonations).
   * Returns owner IDs whose remaining territory dropped to zero.
   */
  fun carveCrater(centerX: Float, centerZ: Float, radius: Float): List<Int> {
    val rSq = radius * radius
    val minGx = max(0, worldToGridX(centerX - radius - cellSize))
    val maxGx = min(gridSize - 1, worldToGridX(centerX + radius + cellSize))
    val minGz = max(0, worldToGridZ(centerZ - radius - cellSize))
    val maxGz = min(gridSize - 1, worldToGridZ(centerZ + radius + cellSize))

    val affected = HashSet<Int>()
    for (gz in minGz..maxGz) {
      for (gx in minGx..maxGx) {
        val idx = gz * gridSize + gx
        if (!validArenaMask[idx]) continue
        val prev = owners[idx].toInt()
        if (prev < 0) continue
        val wx = origin + (gx + 0.5f) * cellSize
        val wz = origin + (gz + 0.5f) * cellSize
        val dx = wx - centerX
        val dz = wz - centerZ
        if (dx * dx + dz * dz <= rSq) {
          owners[idx] = EMPTY_OWNER
          ownerCellCounts[prev] = (ownerCellCounts[prev] - 1).coerceAtLeast(0)
          dirtyOwners[prev] = true
          affected.add(prev)
        }
      }
    }
    return affected.filter { ownerCellCounts[it] <= 0 }
  }

  /**
   * Samples downscaled normalized coordinates [-1..1] with owner ID for the Compose minimap radar.
   */
  fun sampleMinimapCells(stride: Int = 4): List<Triple<Float, Float, Int>> {
    val result = ArrayList<Triple<Float, Float, Int>>(512)
    for (gz in 0 until gridSize step stride) {
      for (gx in 0 until gridSize step stride) {
        val owner = owners[gz * gridSize + gx].toInt()
        if (owner >= 0) {
          val wx = origin + (gx + 0.5f) * cellSize
          val wz = origin + (gz + 0.5f) * cellSize
          result.add(Triple(wx / arenaRadius, wz / arenaRadius, owner))
        }
      }
    }
    return result
  }

  fun isInsideTerritory(ownerId: Int, wx: Float, wz: Float): Boolean {
    val gx = worldToGridX(wx)
    val gz = worldToGridZ(wz)
    if (gx !in 0 until gridSize || gz !in 0 until gridSize) return false
    return owners[gz * gridSize + gx].toInt() == ownerId
  }

  fun getTerritoryPercent(ownerId: Int): Float {
    return (ownerCellCounts[ownerId].toFloat() / totalArenaCells.toFloat()) * 100f
  }

  fun hasTerritory(ownerId: Int): Boolean = ownerCellCounts[ownerId] > 0

  fun consumeDirtyFlag(ownerId: Int): Boolean {
    val d = dirtyOwners[ownerId]
    dirtyOwners[ownerId] = false
    return d
  }

  fun markDirty(ownerId: Int) {
    dirtyOwners[ownerId] = true
  }

  /**
   * Captures the loop formed by [trailPoints] + existing territory of [ownerId].
   * Uses padded exterior BFS flood fill so concave regions, islands, and stolen enemy land
   * are resolved cleanly in one pass.
   * Returns list of enemy owner IDs whose remaining territory dropped to 0 (eliminated).
   */
  fun captureLoop(ownerId: Int, trailPoints: List<Vec2>): List<Int> {
    if (trailPoints.isEmpty()) return emptyList()

    trailMask.fill(false)
    // Rasterize trail polyline with 1-cell thickness so no diagonal leaks occur
    for (i in trailPoints.indices) {
      val a = trailPoints[i]
      if (i + 1 < trailPoints.size) {
        rasterizeSegment(a, trailPoints[i + 1])
      } else {
        markTrailCell(worldToGridX(a.x), worldToGridZ(a.y))
      }
    }

    // Padded grid (gridSize + 2) x (gridSize + 2) where border = outside world
    val padW = gridSize + 2
    visited.fill(false)
    var head = 0
    var tail = 0

    // Seed border cell (0, 0)
    visited[0] = true
    bfsQueue[tail++] = 0

    val ownerByte = ownerId.toByte()
    while (head < tail) {
      val curr = bfsQueue[head++]
      val px = curr % padW
      val pz = curr / padW

      // 4-neighbors
      if (px > 0) tryPush(px - 1, pz, padW, ownerByte, tail).also { tail = it }
      if (px + 1 < padW) tryPush(px + 1, pz, padW, ownerByte, tail).also { tail = it }
      if (pz > 0) tryPush(px, pz - 1, padW, ownerByte, tail).also { tail = it }
      if (pz + 1 < padW) tryPush(px, pz + 1, padW, ownerByte, tail).also { tail = it }
    }

    // Any valid arena cell NOT visited from outside is now owned by ownerId!
    val affectedEnemies = HashSet<Int>()
    for (gz in 0 until gridSize) {
      for (gx in 0 until gridSize) {
        val idx = gz * gridSize + gx
        if (!validArenaMask[idx]) continue
        val padIdx = (gz + 1) * padW + (gx + 1)
        if (!visited[padIdx]) {
          val prev = owners[idx].toInt()
          if (prev != ownerId) {
            if (prev >= 0) {
              ownerCellCounts[prev] = (ownerCellCounts[prev] - 1).coerceAtLeast(0)
              dirtyOwners[prev] = true
              affectedEnemies.add(prev)
            }
            owners[idx] = ownerByte
            ownerCellCounts[ownerId]++
            dirtyOwners[ownerId] = true
          }
        }
      }
    }

    return affectedEnemies.filter { ownerCellCounts[it] <= 0 }
  }

  private fun tryPush(px: Int, pz: Int, padW: Int, ownerByte: Byte, tailIn: Int): Int {
    val pIdx = pz * padW + px
    if (visited[pIdx]) return tailIn
    val gx = px - 1
    val gz = pz - 1
    if (gx in 0 until gridSize && gz in 0 until gridSize) {
      val idx = gz * gridSize + gx
      if (owners[idx] == ownerByte || trailMask[idx]) {
        return tailIn // Wall!
      }
    }
    visited[pIdx] = true
    bfsQueue[tailIn] = pIdx
    return tailIn + 1
  }

  private fun rasterizeSegment(a: Vec2, b: Vec2) {
    val dx = b.x - a.x
    val dz = b.y - a.y
    val dist = sqrt(dx * dx + dz * dz)
    val steps = max(1, (dist / (cellSize * 0.35f)).toInt())
    for (s in 0..steps) {
      val t = s.toFloat() / steps.toFloat()
      val wx = a.x + dx * t
      val wz = a.y + dz * t
      val gx = worldToGridX(wx)
      val gz = worldToGridZ(wz)
      markTrailCell(gx, gz)
    }
  }

  private fun markTrailCell(gx: Int, gz: Int) {
    if (gx in 0 until gridSize && gz in 0 until gridSize) {
      val idx = gz * gridSize + gx
      if (validArenaMask[idx]) {
        trailMask[idx] = true
      }
    }
  }

  /**
   * Finds the closest world point belonging to [ownerId]'s territory (for bot navigation & collision).
   */
  fun nearestTerritoryPoint(ownerId: Int, wx: Float, wz: Float): Vec2? {
    var bestDistSq = Float.MAX_VALUE
    var bestX = 0f
    var bestZ = 0f
    var found = false
    val ownerByte = ownerId.toByte()

    // Coarse-to-fine stride scan for fast AI queries
    for (gz in 0 until gridSize step 2) {
      for (gx in 0 until gridSize step 2) {
        if (owners[gz * gridSize + gx] == ownerByte) {
          val cx = origin + (gx + 0.5f) * cellSize
          val cz = origin + (gz + 0.5f) * cellSize
          val dx = cx - wx
          val dz = cz - wz
          val d2 = dx * dx + dz * dz
          if (d2 < bestDistSq) {
            bestDistSq = d2
            bestX = cx
            bestZ = cz
            found = true
          }
        }
      }
    }
    return if (found) Vec2(bestX, bestZ) else null
  }

  fun distanceSqToTerritory(ownerId: Int, wx: Float, wz: Float): Float {
    if (isInsideTerritory(ownerId, wx, wz)) return 0f
    val nearest = nearestTerritoryPoint(ownerId, wx, wz) ?: return Float.MAX_VALUE
    val dx = nearest.x - wx
    val dz = nearest.y - wz
    return dx * dx + dz * dz
  }

  /**
   * Builds a smooth extruded 3D slab MeshData (triangulated top face + beveled side walls)
   * for [ownerId] using Marching Squares -> Taubin smoothing -> Earcut triangulation.
   */
  fun buildOwnerMesh(ownerId: Int): MeshData {
    val loops = extractSmoothedContours(ownerId)
    if (loops.isEmpty()) return MeshData.EMPTY

    // Classify outer loops vs hole loops via signed area
    // In our Marching Squares table (occupied on left), outer rings have positive signedArea2,
    // hole rings (if an enemy island sits completely inside) have negative signedArea2.
    val outers = ArrayList<List<Vec2>>()
    val holes = ArrayList<List<Vec2>>()
    for (loop in loops) {
      val area = EarcutTriangulator.signedArea2(loop)
      if (abs(area) < 0.05f) continue
      if (area > 0f) outers.add(loop) else holes.add(loop)
    }

    if (outers.isEmpty()) return MeshData.EMPTY

    val vertices = ArrayList<Float>()
    val indices = ArrayList<Int>()

    // Assign each hole to the smallest enclosing outer ring
    val outerHolesMap = Array(outers.size) { ArrayList<List<Vec2>>() }
    for (hole in holes) {
      val testPt = hole[0]
      var bestOuter = -1
      var bestAbsArea = Float.MAX_VALUE
      for (oi in outers.indices) {
        if (EarcutTriangulator.pointInPolygon(testPt, outers[oi])) {
          val a = abs(EarcutTriangulator.signedArea2(outers[oi]))
          if (a < bestAbsArea) {
            bestAbsArea = a
            bestOuter = oi
          }
        }
      }
      if (bestOuter >= 0) {
        outerHolesMap[bestOuter].add(hole)
      }
    }

    // 1. Top surface triangles
    for (oi in outers.indices) {
      val (triVerts, triIndices) = EarcutTriangulator.triangulate(outers[oi], outerHolesMap[oi])
      if (triIndices.isEmpty()) continue
      val baseVertIndex = vertices.size / 8
      for (p in triVerts) {
        vertices.add(p.x)
        vertices.add(SLAB_HEIGHT)
        vertices.add(p.y)
        vertices.add(0f)
        vertices.add(1f)
        vertices.add(0f)
        vertices.add(p.x * 0.1f)
        vertices.add(p.y * 0.1f)
      }
      for (ti in triIndices) {
        indices.add(baseVertIndex + ti)
      }
    }

    // 2. Extruded 3D side skirt walls for every outer & hole contour
    for (loop in loops) {
      if (loop.size < 3 || abs(EarcutTriangulator.signedArea2(loop)) < 0.05f) continue
      appendSideSkirt(loop, vertices, indices)
    }

    return MeshData(vertices.toFloatArray(), indices.toIntArray())
  }

  private fun appendSideSkirt(
    ring: List<Vec2>,
    vertices: MutableList<Float>,
    indices: MutableList<Int>,
  ) {
    val n = ring.size
    // Because Marching Squares keeps interior on LEFT (signedArea2 > 0 for outer, < 0 for hole),
    // the exterior normal of directed segment (dx, dz) is always (+dz, 0, -dx).
    for (i in 0 until n) {
      val a = ring[i]
      val b = ring[(i + 1) % n]
      val dx = b.x - a.x
      val dz = b.y - a.y
      val len = sqrt(dx * dx + dz * dz)
      if (len < 1e-5f) continue
      val nx = dz / len
      val nz = -dx / len

      val baseIdx = vertices.size / 8
      // 0: a top, 1: b top, 2: b bottom, 3: a bottom
      addVertex(vertices, a.x, SLAB_HEIGHT, a.y, nx, 0.15f, nz, 0f, 1f)
      addVertex(vertices, b.x, SLAB_HEIGHT, b.y, nx, 0.15f, nz, 1f, 1f)
      addVertex(vertices, b.x, 0.0f, b.y, nx, 0.15f, nz, 1f, 0f)
      addVertex(vertices, a.x, 0.0f, a.y, nx, 0.15f, nz, 0f, 0f)

      // Outward CCW winding along (+dz, 0, -dx)
      indices.add(baseIdx)
      indices.add(baseIdx + 1)
      indices.add(baseIdx + 2)

      indices.add(baseIdx)
      indices.add(baseIdx + 2)
      indices.add(baseIdx + 3)
    }
  }

  private fun addVertex(
    out: MutableList<Float>,
    x: Float,
    y: Float,
    z: Float,
    nx: Float,
    ny: Float,
    nz: Float,
    u: Float,
    v: Float,
  ) {
    out.add(x); out.add(y); out.add(z)
    out.add(nx); out.add(ny); out.add(nz)
    out.add(u); out.add(v)
  }

  /**
   * Extracts closed contour polygons via Marching Squares with exact integer edge keys,
   * then applies Taubin smoothing + collinearity decimation.
   */
  private fun extractSmoothedContours(ownerId: Int): List<List<Vec2>> {
    val ownerByte = ownerId.toByte()
    val padW = gridSize + 2

    fun occ(px: Int, pz: Int): Boolean {
      val gx = px - 1
      val gz = pz - 1
      if (gx !in 0 until gridSize || gz !in 0 until gridSize) return false
      return owners[gz * gridSize + gx] == ownerByte
    }

    // Directed segments keyed by start integer edge key -> list of end integer edge keys
    val edges = HashMap<Long, MutableList<Long>>()

    fun edgeKey(kx: Int, kz: Int): Long = (kx.toLong() shl 32) or (kz.toLong() and 0xFFFFFFFFL)

    fun addSeg(kx0: Int, kz0: Int, kx1: Int, kz1: Int) {
      val k0 = edgeKey(kx0, kz0)
      val k1 = edgeKey(kx1, kz1)
      edges.getOrPut(k0) { ArrayList(1) }.add(k1)
    }

    for (cz in 0 until padW - 1) {
      for (cx in 0 until padW - 1) {
        val b0 = occ(cx, cz)
        val b1 = occ(cx + 1, cz)
        val b2 = occ(cx + 1, cz + 1)
        val b3 = occ(cx, cz + 1)
        val mask = (if (b0) 1 else 0) or (if (b1) 2 else 0) or (if (b2) 4 else 0) or (if (b3) 8 else 0)
        if (mask == 0 || mask == 15) continue

        // Edge integer coordinates (doubled):
        val e0x = 2 * cx + 1; val e0z = 2 * cz       // bottom
        val e1x = 2 * cx + 2; val e1z = 2 * cz + 1   // right
        val e2x = 2 * cx + 1; val e2z = 2 * cz + 2   // top
        val e3x = 2 * cx;     val e3z = 2 * cz + 1   // left

        when (mask) {
          1 -> addSeg(e0x, e0z, e3x, e3z)
          2 -> addSeg(e1x, e1z, e0x, e0z)
          3 -> addSeg(e1x, e1z, e3x, e3z)
          4 -> addSeg(e2x, e2z, e1x, e1z)
          5 -> {
            addSeg(e0x, e0z, e3x, e3z)
            addSeg(e2x, e2z, e1x, e1z)
          }
          6 -> addSeg(e2x, e2z, e0x, e0z)
          7 -> addSeg(e2x, e2z, e3x, e3z)
          8 -> addSeg(e3x, e3z, e2x, e2z)
          9 -> addSeg(e0x, e0z, e2x, e2z)
          10 -> {
            addSeg(e1x, e1z, e0x, e0z)
            addSeg(e3x, e3z, e2x, e2z)
          }
          11 -> addSeg(e1x, e1z, e2x, e2z)
          12 -> addSeg(e3x, e3z, e1x, e1z)
          13 -> addSeg(e0x, e0z, e1x, e1z)
          14 -> addSeg(e3x, e3z, e0x, e0z)
        }
      }
    }

    val rawLoops = ArrayList<List<Vec2>>()
    val maxR = arenaRadius - 0.12f

    while (edges.isNotEmpty()) {
      val startKey = edges.keys.first()
      val loop = ArrayList<Vec2>()
      var currKey = startKey
      var guard = 10000

      while (guard-- > 0) {
        val kx = (currKey shr 32).toInt()
        val kz = currKey.toInt()
        // Convert doubled padded index back to world coordinates
        val wx = origin + (kx * 0.5f - 0.5f) * cellSize
        val wz = origin + (kz * 0.5f - 0.5f) * cellSize
        // Clamp within arena disc
        val r = sqrt(wx * wx + wz * wz)
        val pt = if (r > maxR) Vec2(wx * (maxR / r), wz * (maxR / r)) else Vec2(wx, wz)
        loop.add(pt)

        val nextList = edges[currKey] ?: break
        val nextKey = nextList.removeAt(nextList.size - 1)
        if (nextList.isEmpty()) {
          edges.remove(currKey)
        }
        if (nextKey == startKey) break
        currKey = nextKey
      }

      if (loop.size >= 3) {
        val smoothed = taubinSmooth(loop, iterations = 2)
        val simplified = simplifyLoop(smoothed)
        if (simplified.size >= 3) {
          rawLoops.add(simplified)
        }
      }
    }

    return rawLoops
  }

  private fun taubinSmooth(loop: List<Vec2>, iterations: Int): List<Vec2> {
    var curr = loop
    val lambda = 0.5f
    val mu = -0.52f
    repeat(iterations) {
      curr = laplacianPass(curr, lambda)
      curr = laplacianPass(curr, mu)
    }
    return curr
  }

  private fun laplacianPass(pts: List<Vec2>, factor: Float): List<Vec2> {
    val n = pts.size
    val out = ArrayList<Vec2>(n)
    val maxR = arenaRadius - 0.12f
    for (i in 0 until n) {
      val prev = pts[(i + n - 1) % n]
      val p = pts[i]
      val next = pts[(i + 1) % n]
      val avgX = (prev.x + next.x) * 0.5f
      val avgY = (prev.y + next.y) * 0.5f
      var nx = p.x + (avgX - p.x) * factor
      var ny = p.y + (avgY - p.y) * factor
      val r = sqrt(nx * nx + ny * ny)
      if (r > maxR) {
        nx *= maxR / r
        ny *= maxR / r
      }
      out.add(Vec2(nx, ny))
    }
    return out
  }

  private fun simplifyLoop(pts: List<Vec2>): List<Vec2> {
    if (pts.size <= 4) return pts
    val out = ArrayList<Vec2>(pts.size)
    val n = pts.size
    for (i in 0 until n) {
      val prev = if (out.isEmpty()) pts[n - 1] else out.last()
      val curr = pts[i]
      val next = pts[(i + 1) % n]
      val dx1 = curr.x - prev.x
      val dy1 = curr.y - prev.y
      val len1 = sqrt(dx1 * dx1 + dy1 * dy1)
      if (len1 < 0.18f) continue

      val dx2 = next.x - curr.x
      val dy2 = next.y - curr.y
      val len2 = sqrt(dx2 * dx2 + dy2 * dy2)
      if (len2 > 1e-5f) {
        val cross = abs(dx1 * dy2 - dy1 * dx2) / (len1 * len2)
        if (cross < 0.045f && len1 < 1.2f) continue
      }
      out.add(curr)
    }
    return if (out.size >= 3) out else pts
  }

  private fun worldToGridX(wx: Float): Int = floor((wx - origin) / cellSize).toInt()

  private fun worldToGridZ(wz: Float): Int = floor((wz - origin) / cellSize).toInt()
}

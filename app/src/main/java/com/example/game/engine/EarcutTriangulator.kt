package com.example.game.engine

import com.example.game.math.Vec2
import kotlin.math.abs

/**
 * Robust 2D polygon ear-clipping triangulator (supports concave polygons and holes).
 * Operates in XZ arena coordinates where signedArea2(ring) > 0 means CCW in (x, z)
 * and < 0 means CW in (x, z).
 */
object EarcutTriangulator {

  fun signedArea2(ring: List<Vec2>): Float {
    var sum = 0f
    val n = ring.size
    for (i in 0 until n) {
      val a = ring[i]
      val b = ring[(i + 1) % n]
      sum += (a.x * b.y - b.x * a.y)
    }
    return sum
  }

  /**
   * Triangulates a single outer polygon ring (and optional hole rings) in (x, z).
   * Returns vertex list (possibly merged with hole bridges) and triangle index list
   * ordered so that (v0, v1, v2) has +Y upward normal in right-handed OpenGL (X right, Y up, Z forward/down).
   */
  fun triangulate(
    outerRing: List<Vec2>,
    holes: List<List<Vec2>> = emptyList(),
  ): Pair<List<Vec2>, IntArray> {
    if (outerRing.size < 3) return emptyList<Vec2>() to IntArray(0)

    // For OpenGL +Y normal on XZ plane: (p1 - p0) x (p2 - p0) has Y = (z1-z0)*(x2-x0) - (x1-x0)*(z2-z0)
    // Which is -((x1-x0)*(z2-z0) - (z1-z0)*(x2-x0)) = -cross2D.
    // So +Y upward normal requires cross2D < 0 (signedArea2 < 0).
    val outer =
      if (signedArea2(outerRing) > 0f) outerRing.reversed().toMutableList()
      else outerRing.toMutableList()

    // Merge holes via visibility bridge if any exist
    if (holes.isNotEmpty()) {
      val sortedHoles =
        holes
          .filter { it.size >= 3 }
          .map { hole -> if (signedArea2(hole) < 0f) hole.reversed() else hole }
          .sortedByDescending { ring -> ring.maxOf { it.x } }

      for (hole in sortedHoles) {
        bridgeHole(outer, hole)
      }
    }

    val n = outer.size
    if (n < 3) return emptyList<Vec2>() to IntArray(0)

    val indices = MutableList(n) { it }
    val outTris = ArrayList<Int>((n - 2) * 3)

    var guard = n * n * 2
    var i = 0
    while (indices.size > 3 && guard-- > 0) {
      val count = indices.size
      val prevIdx = indices[(i + count - 1) % count]
      val currIdx = indices[i % count]
      val nextIdx = indices[(i + 1) % count]

      val a = outer[prevIdx]
      val b = outer[currIdx]
      val c = outer[nextIdx]

      if (isEar(a, b, c, prevIdx, currIdx, nextIdx, indices, outer)) {
        outTris.add(prevIdx)
        outTris.add(currIdx)
        outTris.add(nextIdx)
        indices.removeAt(i % count)
        if (indices.isNotEmpty()) {
          i %= indices.size
        }
      } else {
        i = (i + 1) % count
      }
    }

    if (indices.size == 3) {
      val a = outer[indices[0]]
      val b = outer[indices[1]]
      val c = outer[indices[2]]
      if (abs(cross2(a, b, c)) > 1e-7f) {
        outTris.add(indices[0])
        outTris.add(indices[1])
        outTris.add(indices[2])
      }
    }

    return outer to outTris.toIntArray()
  }

  private fun cross2(a: Vec2, b: Vec2, c: Vec2): Float {
    return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
  }

  private fun isEar(
    a: Vec2,
    b: Vec2,
    c: Vec2,
    ia: Int,
    ib: Int,
    ic: Int,
    indices: List<Int>,
    verts: List<Vec2>,
  ): Boolean {
    // Must be a convex vertex in negative-signed-area orientation (cross2 <= 0)
    val cr = cross2(a, b, c)
    if (cr >= -1e-7f) return false

    for (idx in indices) {
      if (idx == ia || idx == ib || idx == ic) continue
      val p = verts[idx]
      if ((p.x == a.x && p.y == a.y) || (p.x == b.x && p.y == b.y) || (p.x == c.x && p.y == c.y)) {
        continue
      }
      if (pointInTriangle(p, a, b, c)) return false
    }
    return true
  }

  fun pointInTriangle(p: Vec2, a: Vec2, b: Vec2, c: Vec2): Boolean {
    // Handles CW (negative cross2) winding
    val d1 = cross2(a, b, p)
    val d2 = cross2(b, c, p)
    val d3 = cross2(c, a, p)
    val hasPos = (d1 > 1e-6f) || (d2 > 1e-6f) || (d3 > 1e-6f)
    val hasNeg = (d1 < -1e-6f) || (d2 < -1e-6f) || (d3 < -1e-6f)
    return !(hasPos && hasNeg)
  }

  fun pointInPolygon(p: Vec2, poly: List<Vec2>): Boolean {
    var inside = false
    val n = poly.size
    var j = n - 1
    for (i in 0 until n) {
      val pi = poly[i]
      val pj = poly[j]
      val intersects =
        ((pi.y > p.y) != (pj.y > p.y)) &&
          (p.x < (pj.x - pi.x) * (p.y - pi.y) / ((pj.y - pi.y) + 1e-12f) + pi.x)
      if (intersects) inside = !inside
      j = i
    }
    return inside
  }

  private fun bridgeHole(outer: MutableList<Vec2>, hole: List<Vec2>) {
    // Find rightmost vertex in hole
    var holeIdx = 0
    for (i in 1 until hole.size) {
      if (hole[i].x > hole[holeIdx].x) holeIdx = i
    }
    val hx = hole[holeIdx].x
    val hy = hole[holeIdx].y

    // Cast ray +X to find closest visible intersection on outer edges
    var bestDist = Float.MAX_VALUE
    var bestOuterIdx = 0
    val m = outer.size
    for (i in 0 until m) {
      val a = outer[i]
      val b = outer[(i + 1) % m]
      if ((a.y <= hy && b.y > hy) || (b.y <= hy && a.y > hy)) {
        val t = (hy - a.y) / (b.y - a.y)
        val ix = a.x + t * (b.x - a.x)
        if (ix >= hx && (ix - hx) < bestDist) {
          bestDist = ix - hx
          bestOuterIdx = if (a.x >= b.x) i else (i + 1) % m
        }
      }
    }

    // Fallback to nearest outer vertex if ray didn't intersect cleanly
    if (bestDist == Float.MAX_VALUE) {
      var minSq = Float.MAX_VALUE
      for (i in 0 until m) {
        val dx = outer[i].x - hx
        val dy = outer[i].y - hy
        val d2 = dx * dx + dy * dy
        if (d2 < minSq) {
          minSq = d2
          bestOuterIdx = i
        }
      }
    }

    val bridge = ArrayList<Vec2>(hole.size + 2)
    for (k in 0..hole.size) {
      bridge.add(hole[(holeIdx + k) % hole.size])
    }
    bridge.add(outer[bestOuterIdx])
    outer.addAll(bestOuterIdx + 1, bridge)
  }
}

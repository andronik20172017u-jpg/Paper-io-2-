package com.example.game.engine

import com.example.game.graphics.MeshData
import com.example.game.math.Vec2
import kotlin.math.sqrt

/**
 * Generates a 3D chamfered energy ribbon mesh from a unit's trail polyline.
 * Cross-section has 3 vertices per station: left base (y=0.06), center ridge (y=0.30), right base (y=0.06).
 */
object TrailMeshBuilder {

  private const val HALF_WIDTH = 0.42f
  private const val BASE_Y = 0.06f
  private const val RIDGE_Y = 0.30f

  fun build(trail: List<Vec2>, headPos: Vec2?): MeshData {
    val count = trail.size + (if (headPos != null) 1 else 0)
    if (count < 2) return MeshData.EMPTY

    val pts = ArrayList<Vec2>(count)
    pts.addAll(trail)
    if (headPos != null) {
      val last = trail.lastOrNull()
      if (last == null || (headPos - last).lengthSq() > 0.002f) {
        pts.add(headPos)
      }
    }
    if (pts.size < 2) return MeshData.EMPTY

    val stations = pts.size
    val vertices = FloatArray(stations * 3 * 8)
    val indices = IntArray((stations - 1) * 12)

    var arcDist = 0f
    for (i in 0 until stations) {
      val curr = pts[i]
      if (i > 0) {
        arcDist += (curr - pts[i - 1]).length()
      }

      val dir =
        when {
          i == 0 -> pts[1] - pts[0]
          i == stations - 1 -> pts[i] - pts[i - 1]
          else -> pts[i + 1] - pts[i - 1]
        }
      val len = sqrt(dir.x * dir.x + dir.y * dir.y).coerceAtLeast(1e-5f)
      val tx = dir.x / len
      val tz = dir.y / len
      // Perpendicular in XZ
      val px = -tz
      val pz = tx

      val lx = curr.x + px * HALF_WIDTH
      val lz = curr.y + pz * HALF_WIDTH
      val rx = curr.x - px * HALF_WIDTH
      val rz = curr.y - pz * HALF_WIDTH

      // Left slope normal, top ridge normal, right slope normal
      val lnx = px * 0.55f
      val lnz = pz * 0.55f
      val rnx = -px * 0.55f
      val rnz = -pz * 0.55f

      val vBase = i * 24
      // Left base (uv.y = 0.0)
      putVert(vertices, vBase, lx, BASE_Y, lz, lnx, 0.83f, lnz, arcDist, 0.0f)
      // Center ridge (uv.y = 0.5)
      putVert(vertices, vBase + 8, curr.x, RIDGE_Y, curr.y, 0f, 1f, 0f, arcDist, 0.5f)
      // Right base (uv.y = 1.0)
      putVert(vertices, vBase + 16, rx, BASE_Y, rz, rnx, 0.83f, rnz, arcDist, 1.0f)
    }

    var ii = 0
    for (i in 0 until stations - 1) {
      val row0 = i * 3
      val row1 = (i + 1) * 3

      // Left slope quad: (row0+0, row0+1, row1+1, row1+0)
      indices[ii++] = row0
      indices[ii++] = row1
      indices[ii++] = row0 + 1

      indices[ii++] = row0 + 1
      indices[ii++] = row1
      indices[ii++] = row1 + 1

      // Right slope quad: (row0+1, row0+2, row1+2, row1+1)
      indices[ii++] = row0 + 1
      indices[ii++] = row1 + 1
      indices[ii++] = row0 + 2

      indices[ii++] = row0 + 2
      indices[ii++] = row1 + 1
      indices[ii++] = row1 + 2
    }

    return MeshData(vertices, indices)
  }

  private fun putVert(
    arr: FloatArray,
    offset: Int,
    x: Float,
    y: Float,
    z: Float,
    nx: Float,
    ny: Float,
    nz: Float,
    u: Float,
    v: Float,
  ) {
    arr[offset] = x
    arr[offset + 1] = y
    arr[offset + 2] = z
    arr[offset + 3] = nx
    arr[offset + 4] = ny
    arr[offset + 5] = nz
    arr[offset + 6] = u
    arr[offset + 7] = v
  }
}

package com.example.game.renderer

import com.example.game.graphics.MeshData
import com.example.game.math.Math3D
import com.example.game.math.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates the 3D circular arena platform (top disc + beveled metallic rim + tapered under-hull)
 * and reusable 3D octahedral burst shard geometry.
 */
object ArenaMeshBuilder {

  fun buildArenaPlatform(radius: Float, segments: Int = 96): MeshData {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()

    // 1. Top circular disc at y = 0.0
    val centerIdx = 0
    addVert(verts, 0f, 0f, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
    for (i in 0..segments) {
      val a = (i.toFloat() / segments.toFloat()) * Math3D.TWO_PI
      val x = cos(a) * radius
      val z = sin(a) * radius
      addVert(verts, x, 0f, z, 0f, 1f, 0f, 0.5f + cos(a) * 0.5f, 0.5f + sin(a) * 0.5f)
    }
    for (i in 1..segments) {
      indices.add(centerIdx)
      indices.add(i + 1)
      indices.add(i)
    }

    // 2. Outer beveled rim ring: y = 0.0 (r = radius) -> y = -0.35 (r = radius + 0.55) -> y = -3.4 (r = radius - 4.0)
    val rimStart = verts.size / 8
    val rTop = radius
    val rMid = radius + 0.55f
    val rBot = radius - 4.2f
    for (i in 0..segments) {
      val a = (i.toFloat() / segments.toFloat()) * Math3D.TWO_PI
      val ca = cos(a)
      val sa = sin(a)
      val u = i.toFloat() / segments.toFloat()

      // Upper bevel normal
      val nTop = Vec3(ca * 0.75f, 0.66f, sa * 0.75f).normalized()
      // Lower hull normal
      val nBot = Vec3(ca * 0.85f, -0.52f, sa * 0.85f).normalized()

      addVert(verts, ca * rTop, 0.0f, sa * rTop, nTop.x, nTop.y, nTop.z, u, 1.0f)
      addVert(verts, ca * rMid, -0.38f, sa * rMid, nTop.x, nTop.y, nTop.z, u, 0.7f)
      addVert(verts, ca * rMid, -0.38f, sa * rMid, nBot.x, nBot.y, nBot.z, u, 0.7f)
      addVert(verts, ca * rBot, -3.5f, sa * rBot, nBot.x, nBot.y, nBot.z, u, 0.0f)
    }

    for (i in 0 until segments) {
      val b0 = rimStart + i * 4
      val b1 = rimStart + (i + 1) * 4

      // Upper bevel quad
      indices.add(b0)
      indices.add(b1)
      indices.add(b0 + 1)

      indices.add(b0 + 1)
      indices.add(b1)
      indices.add(b1 + 1)

      // Lower under-hull quad
      indices.add(b0 + 2)
      indices.add(b1 + 2)
      indices.add(b0 + 3)

      indices.add(b0 + 3)
      indices.add(b1 + 2)
      indices.add(b1 + 3)
    }

    return MeshData(verts.toFloatArray(), indices.toIntArray())
  }

  fun buildShardOctahedron(): MeshData {
    val top = Vec3(0f, 1f, 0f)
    val bot = Vec3(0f, -1f, 0f)
    val p0 = Vec3(0.7f, 0f, 0f)
    val p1 = Vec3(0f, 0f, 0.7f)
    val p2 = Vec3(-0.7f, 0f, 0f)
    val p3 = Vec3(0f, 0f, -0.7f)

    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    fun pushTri(a: Vec3, b: Vec3, c: Vec3) {
      val n = (b - a).cross(c - a).normalized()
      val base = verts.size / 8
      addVert(verts, a.x, a.y, a.z, n.x, n.y, n.z, 0f, 0f)
      addVert(verts, b.x, b.y, b.z, n.x, n.y, n.z, 1f, 0f)
      addVert(verts, c.x, c.y, c.z, n.x, n.y, n.z, 0.5f, 1f)
      indices.add(base); indices.add(base + 1); indices.add(base + 2)
    }

    pushTri(top, p1, p0)
    pushTri(top, p2, p1)
    pushTri(top, p3, p2)
    pushTri(top, p0, p3)
    pushTri(bot, p0, p1)
    pushTri(bot, p1, p2)
    pushTri(bot, p2, p3)
    pushTri(bot, p3, p0)

    return MeshData(verts.toFloatArray(), indices.toIntArray())
  }

  private fun addVert(
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
}

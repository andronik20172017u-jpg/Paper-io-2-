package com.example.game.graphics

import android.content.res.AssetManager
import com.example.game.math.Vec3
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads Wavefront .obj 3D models (positions, UVs, normals, and triangulated/n-gon faces)
 * into an indexed interleaved MeshData ready for OpenGL ES 3.0 VAO/VBO/IBO upload.
 */
object ObjLoader {

  private data class VertexKey(val vi: Int, val ti: Int, val ni: Int)

  fun loadFromAssets(assets: AssetManager, assetPath: String, scale: Float = 1f): MeshData {
    val positions = ArrayList<Float>()
    val uvs = ArrayList<Float>()
    val normals = ArrayList<Float>()

    val outVertices = ArrayList<Float>()
    val outIndices = ArrayList<Int>()
    val vertexMap = HashMap<VertexKey, Int>()

    BufferedReader(InputStreamReader(assets.open(assetPath))).use { reader ->
      var line: String? = reader.readLine()
      while (line != null) {
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
          val tokens = trimmed.split(Regex("\\s+"))
          when (tokens[0]) {
            "v" -> {
              if (tokens.size >= 4) {
                positions.add(tokens[1].toFloat() * scale)
                positions.add(tokens[2].toFloat() * scale)
                positions.add(tokens[3].toFloat() * scale)
              }
            }
            "vt" -> {
              if (tokens.size >= 3) {
                uvs.add(tokens[1].toFloat())
                uvs.add(1f - tokens[2].toFloat()) // Flip V for OpenGL convention
              }
            }
            "vn" -> {
              if (tokens.size >= 4) {
                normals.add(tokens[1].toFloat())
                normals.add(tokens[2].toFloat())
                normals.add(tokens[3].toFloat())
              }
            }
            "f" -> {
              if (tokens.size >= 4) {
                val faceCount = tokens.size - 1
                val parsed = Array(faceCount) { idx ->
                  parseFaceToken(tokens[idx + 1], positions.size / 3, uvs.size / 2, normals.size / 3)
                }

                // Fan-triangulate polygons (supports tris, quads, n-gons)
                for (i in 1 until faceCount - 1) {
                  val k0 = parsed[0]
                  val k1 = parsed[i]
                  val k2 = parsed[i + 1]

                  val fallbackNormal =
                    if (k0.ni < 0 || k1.ni < 0 || k2.ni < 0) {
                      computeFaceNormal(positions, k0.vi, k1.vi, k2.vi)
                    } else null

                  outIndices.add(
                    getOrInsertVertex(k0, positions, uvs, normals, fallbackNormal, outVertices, vertexMap)
                  )
                  outIndices.add(
                    getOrInsertVertex(k1, positions, uvs, normals, fallbackNormal, outVertices, vertexMap)
                  )
                  outIndices.add(
                    getOrInsertVertex(k2, positions, uvs, normals, fallbackNormal, outVertices, vertexMap)
                  )
                }
              }
            }
          }
        }
        line = reader.readLine()
      }
    }

    return MeshData(outVertices.toFloatArray(), outIndices.toIntArray())
  }

  private fun parseFaceToken(token: String, vCount: Int, tCount: Int, nCount: Int): VertexKey {
    val parts = token.split('/')
    val vi = resolveObjIndex(parts[0].toInt(), vCount)
    val ti = if (parts.size > 1 && parts[1].isNotEmpty()) resolveObjIndex(parts[1].toInt(), tCount) else -1
    val ni = if (parts.size > 2 && parts[2].isNotEmpty()) resolveObjIndex(parts[2].toInt(), nCount) else -1
    return VertexKey(vi, ti, ni)
  }

  private fun resolveObjIndex(raw: Int, count: Int): Int {
    return if (raw > 0) raw - 1 else count + raw
  }

  private fun computeFaceNormal(positions: List<Float>, i0: Int, i1: Int, i2: Int): Vec3 {
    val p0 = Vec3(positions[i0 * 3], positions[i0 * 3 + 1], positions[i0 * 3 + 2])
    val p1 = Vec3(positions[i1 * 3], positions[i1 * 3 + 1], positions[i1 * 3 + 2])
    val p2 = Vec3(positions[i2 * 3], positions[i2 * 3 + 1], positions[i2 * 3 + 2])
    return (p1 - p0).cross(p2 - p0).normalized()
  }

  private fun getOrInsertVertex(
    key: VertexKey,
    positions: List<Float>,
    uvs: List<Float>,
    normals: List<Float>,
    fallbackNormal: Vec3?,
    outVertices: MutableList<Float>,
    vertexMap: MutableMap<VertexKey, Int>,
  ): Int {
    if (key.ni >= 0) {
      vertexMap[key]?.let { return it }
    }
    val index = outVertices.size / MeshData.FLOATS_PER_VERTEX
    val vBase = key.vi * 3
    outVertices.add(positions[vBase])
    outVertices.add(positions[vBase + 1])
    outVertices.add(positions[vBase + 2])

    if (key.ni >= 0) {
      val nBase = key.ni * 3
      outVertices.add(normals[nBase])
      outVertices.add(normals[nBase + 1])
      outVertices.add(normals[nBase + 2])
      vertexMap[key] = index
    } else {
      val n = fallbackNormal ?: Vec3(0f, 1f, 0f)
      outVertices.add(n.x)
      outVertices.add(n.y)
      outVertices.add(n.z)
    }

    if (key.ti >= 0) {
      val tBase = key.ti * 2
      outVertices.add(uvs[tBase])
      outVertices.add(uvs[tBase + 1])
    } else {
      outVertices.add(0.5f)
      outVertices.add(0.5f)
    }

    return index
  }
}

package com.example.game.graphics

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Interleaved vertex layout: [x, y, z, nx, ny, nz, u, v] = 8 floats (32 bytes).
 */
class MeshData(
  val vertices: FloatArray,
  val indices: IntArray,
) {
  companion object {
    const val FLOATS_PER_VERTEX = 8
    const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4
    val EMPTY = MeshData(FloatArray(0), IntArray(0))
  }
}

class GpuMesh(private val dynamic: Boolean = false) {
  private val vao = IntArray(1)
  private val vbo = IntArray(1)
  private val ibo = IntArray(1)
  private var indexCount = 0
  private var initialized = false

  private fun ensureCreated() {
    if (initialized) return
    GLES30.glGenVertexArrays(1, vao, 0)
    GLES30.glGenBuffers(1, vbo, 0)
    GLES30.glGenBuffers(1, ibo, 0)

    GLES30.glBindVertexArray(vao[0])
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])

    // a_Position
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, MeshData.STRIDE_BYTES, 0)

    // a_Normal
    GLES30.glEnableVertexAttribArray(1)
    GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, MeshData.STRIDE_BYTES, 3 * 4)

    // a_Uv
    GLES30.glEnableVertexAttribArray(2)
    GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, MeshData.STRIDE_BYTES, 6 * 4)

    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[0])
    GLES30.glBindVertexArray(0)
    initialized = true
  }

  fun upload(data: MeshData) {
    if (data.indices.isEmpty() || data.vertices.isEmpty()) {
      indexCount = 0
      return
    }
    ensureCreated()

    val usage = if (dynamic) GLES30.GL_DYNAMIC_DRAW else GLES30.GL_STATIC_DRAW

    val vBuffer =
      ByteBuffer.allocateDirect(data.vertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
          put(data.vertices)
          position(0)
        }

    val iBuffer =
      ByteBuffer.allocateDirect(data.indices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asIntBuffer()
        .apply {
          put(data.indices)
          position(0)
        }

    GLES30.glBindVertexArray(vao[0])
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.vertices.size * 4, vBuffer, usage)

    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[0])
    GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.indices.size * 4, iBuffer, usage)

    GLES30.glBindVertexArray(0)
    indexCount = data.indices.size
  }

  fun draw() {
    if (!initialized || indexCount <= 0) return
    GLES30.glBindVertexArray(vao[0])
    GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
    GLES30.glBindVertexArray(0)
  }

  fun release() {
    if (!initialized) return
    GLES30.glDeleteVertexArrays(1, vao, 0)
    GLES30.glDeleteBuffers(1, vbo, 0)
    GLES30.glDeleteBuffers(1, ibo, 0)
    initialized = false
    indexCount = 0
  }
}

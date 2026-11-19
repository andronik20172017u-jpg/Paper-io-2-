package com.example.game.graphics

import android.content.res.AssetManager
import android.opengl.GLES30
import android.util.Log
import com.example.game.math.Vec3

class ShaderProgram private constructor(private val programId: Int) {
  private val uniformCache = HashMap<String, Int>()

  fun use() {
    GLES30.glUseProgram(programId)
  }

  fun getUniformLocation(name: String): Int {
    return uniformCache.getOrPut(name) {
      GLES30.glGetUniformLocation(programId, name)
    }
  }

  fun setMat4(name: String, matrix: FloatArray) {
    val loc = getUniformLocation(name)
    if (loc >= 0) {
      GLES30.glUniformMatrix4fv(loc, 1, false, matrix, 0)
    }
  }

  fun setMat3(name: String, matrix: FloatArray) {
    val loc = getUniformLocation(name)
    if (loc >= 0) {
      GLES30.glUniformMatrix3fv(loc, 1, false, matrix, 0)
    }
  }

  fun setVec3(name: String, x: Float, y: Float, z: Float) {
    val loc = getUniformLocation(name)
    if (loc >= 0) {
      GLES30.glUniform3f(loc, x, y, z)
    }
  }

  fun setVec3(name: String, v: Vec3) = setVec3(name, v.x, v.y, v.z)

  fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
    val loc = getUniformLocation(name)
    if (loc >= 0) {
      GLES30.glUniform4f(loc, x, y, z, w)
    }
  }

  fun setFloat(name: String, value: Float) {
    val loc = getUniformLocation(name)
    if (loc >= 0) {
      GLES30.glUniform1f(loc, value)
    }
  }

  fun release() {
    GLES30.glDeleteProgram(programId)
  }

  companion object {
    private const val TAG = "ShaderProgram"

    fun fromAssets(
      assets: AssetManager,
      vertexAssetPath: String,
      fragmentAssetPath: String,
    ): ShaderProgram {
      val vertSrc = assets.open(vertexAssetPath).bufferedReader().use { it.readText() }
      val fragSrc = assets.open(fragmentAssetPath).bufferedReader().use { it.readText() }
      val vertShader = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc, vertexAssetPath)
      val fragShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc, fragmentAssetPath)

      val program = GLES30.glCreateProgram()
      GLES30.glAttachShader(program, vertShader)
      GLES30.glAttachShader(program, fragShader)
      GLES30.glLinkProgram(program)

      val linkStatus = IntArray(1)
      GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
      if (linkStatus[0] == 0) {
        val log = GLES30.glGetProgramInfoLog(program)
        GLES30.glDeleteProgram(program)
        GLES30.glDeleteShader(vertShader)
        GLES30.glDeleteShader(fragShader)
        throw IllegalStateException("Shader link failed ($vertexAssetPath / $fragmentAssetPath): $log")
      }

      GLES30.glDeleteShader(vertShader)
      GLES30.glDeleteShader(fragShader)
      return ShaderProgram(program)
    }

    private fun compileShader(type: Int, source: String, label: String): Int {
      val shader = GLES30.glCreateShader(type)
      GLES30.glShaderSource(shader, source)
      GLES30.glCompileShader(shader)

      val compiled = IntArray(1)
      GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
      if (compiled[0] == 0) {
        val log = GLES30.glGetShaderInfoLog(shader)
        Log.e(TAG, "Compile error in $label:\n$log")
        GLES30.glDeleteShader(shader)
        throw IllegalStateException("Shader compile failed ($label): $log")
      }
      return shader
    }
  }
}

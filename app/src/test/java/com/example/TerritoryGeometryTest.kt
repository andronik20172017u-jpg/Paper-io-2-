package com.example

import com.example.game.engine.EarcutTriangulator
import com.example.game.engine.GameWorld3D
import com.example.game.engine.MatchPhase
import com.example.game.engine.Territory3DManager
import com.example.game.engine.TrailMeshBuilder
import com.example.game.graphics.MeshData
import com.example.game.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerritoryGeometryTest {

  @Test
  fun earcut_triangulatesConcaveAndHoledPolygon() {
    // Concave L-shape outer ring
    val lShape =
      listOf(
        Vec2(0f, 0f),
        Vec2(4f, 0f),
        Vec2(4f, 1f),
        Vec2(1f, 1f),
        Vec2(1f, 4f),
        Vec2(0f, 4f),
      )
    val (verts, tris) = EarcutTriangulator.triangulate(lShape)
    assertEquals(6, verts.size)
    assertEquals((6 - 2) * 3, tris.size)

    // Square with a centered hole
    val outer = listOf(Vec2(-5f, -5f), Vec2(5f, -5f), Vec2(5f, 5f), Vec2(-5f, 5f))
    val hole = listOf(Vec2(-1f, -1f), Vec2(1f, -1f), Vec2(1f, 1f), Vec2(-1f, 1f))
    val (hVerts, hTris) = EarcutTriangulator.triangulate(outer, listOf(hole))
    assertTrue(hVerts.size >= 8)
    assertTrue(hTris.isNotEmpty())
    assertEquals(0, hTris.size % 3)
    for (idx in hTris) {
      assertTrue(idx in hVerts.indices)
    }
  }

  @Test
  fun territoryManager_capturesLoopAndBuildsValid3DMesh() {
    val manager = Territory3DManager()
    manager.spawnBase(ownerId = 0, centerX = 0f, centerZ = 0f, baseRadius = 3.5f)
    val initialPct = manager.getTerritoryPercent(0)
    assertTrue(initialPct > 0f)

    // Draw an outward loop and return to base
    val loop =
      listOf(
        Vec2(3.2f, 0f),
        Vec2(9.0f, 0f),
        Vec2(9.0f, 7.0f),
        Vec2(0f, 7.0f),
        Vec2(0f, 3.2f),
      )
    manager.captureLoop(ownerId = 0, trailPoints = loop)
    val expandedPct = manager.getTerritoryPercent(0)
    assertTrue("Expected territory to grow ($expandedPct > $initialPct)", expandedPct > initialPct)

    val mesh = manager.buildOwnerMesh(0)
    assertTrue(mesh.vertices.isNotEmpty())
    assertTrue(mesh.indices.isNotEmpty())
    assertEquals(0, mesh.vertices.size % MeshData.FLOATS_PER_VERTEX)
    assertEquals(0, mesh.indices.size % 3)

    val vertexCount = mesh.vertices.size / MeshData.FLOATS_PER_VERTEX
    for (idx in mesh.indices) {
      assertTrue("Index $idx out of bounds $vertexCount", idx in 0 until vertexCount)
    }
  }

  @Test
  fun trailMeshBuilder_generatesChamfered3DRibbon() {
    val trail = listOf(Vec2(0f, 0f), Vec2(2f, 0f), Vec2(4f, 2f))
    val mesh = TrailMeshBuilder.build(trail, Vec2(5f, 3f))
    assertTrue(mesh.vertices.isNotEmpty())
    assertTrue(mesh.indices.isNotEmpty())
    assertEquals(0, mesh.indices.size % 3)
  }

  @Test
  fun gameWorld_simulatesMatchWithoutErrors() {
    val world = GameWorld3D()
    world.startNewMatch()
    assertEquals(MatchPhase.PLAYING, world.phase)
    world.setJoystickInput(active = true, dx = 1f, dyScreen = 0f)
    repeat(140) {
      world.update(0.016f)
    }
    assertTrue(world.player.territoryPercent > 0f)

    world.dropPlayerBomb()
    assertTrue(world.bombs.isNotEmpty())
    repeat(240) {
      world.update(0.016f)
    }
    assertTrue(world.bombs.isEmpty())

    world.returnToMenu()
    assertEquals(MatchPhase.MENU, world.phase)
  }

  @Test
  fun territoryManager_carvesCraterAndSamplesMinimap() {
    val manager = Territory3DManager()
    manager.spawnBase(ownerId = 0, centerX = 0f, centerZ = 0f, baseRadius = 5.0f)
    val beforePct = manager.getTerritoryPercent(0)
    assertTrue(manager.sampleMinimapCells(stride = 4).isNotEmpty())

    manager.carveCrater(centerX = 0f, centerZ = 0f, radius = 2.6f)
    val afterPct = manager.getTerritoryPercent(0)
    assertTrue("Expected crater to reduce territory ($afterPct < $beforePct)", afterPct < beforePct)
  }
}

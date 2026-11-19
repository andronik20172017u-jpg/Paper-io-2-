package com.example.game.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

class MultiplayerRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before joining multiplayer.")

  fun observeRoomPlayers(roomId: String): Flow<List<RoomPlayer>> = flow {
    val uid = requireUserId()
    val query =
      db.collection("rooms")
        .document(roomId)
        .collection("players")
        .whereEqualTo("roomOpen", true)
    emitAll(
      query
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            RoomPlayer::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val fsErr =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (fsErr == null && error is CancellationException) throw error
          val ex = fsErr ?: (error as? Exception)
          if (ex != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(ex, OperationType.LIST, "rooms/$roomId/players")
          }
          throw error
        }
    )
  }

  suspend fun quickJoinOrCreateRoom(
    displayName: String,
    skinId: String,
    modelType: String,
  ): Result<JoinedRoomInfo> {
    return try {
      val uid = requireUserId()
      val openRooms =
        db.collection("rooms")
          .whereEqualTo("isPublic", true)
          .whereEqualTo("status", "playing")
          .limit(8)
          .get()
          .await()
          .toObjects(MatchRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

      val candidate = openRooms.firstOrNull { it.playerCount < 5L }
      if (candidate != null) {
        joinExistingRoom(candidate, uid, displayName, skinId, modelType)
      } else {
        createNewRoom(uid, displayName, skinId, modelType, isPublic = true)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, "rooms")
      Result.failure(e)
    }
  }

  suspend fun joinByCode(
    roomCode: String,
    displayName: String,
    skinId: String,
    modelType: String,
  ): Result<JoinedRoomInfo> {
    val cleanCode = roomCode.trim().uppercase(Locale.US)
    if (cleanCode.length !in 4..12) {
      return Result.failure(IllegalArgumentException("Room code must be 4-12 characters"))
    }
    return try {
      val uid = requireUserId()
      val matches =
        db.collection("rooms")
          .whereEqualTo("isPublic", true)
          .whereEqualTo("roomCode", cleanCode)
          .limit(1)
          .get()
          .await()
          .toObjects(MatchRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      val room = matches.firstOrNull() ?: return Result.failure(IllegalStateException("Room $cleanCode not found"))
      joinExistingRoom(room, uid, displayName, skinId, modelType)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, "rooms")
      Result.failure(e)
    }
  }

  suspend fun createPrivateOrPublicRoom(
    displayName: String,
    skinId: String,
    modelType: String,
  ): Result<JoinedRoomInfo> {
    return try {
      val uid = requireUserId()
      createNewRoom(uid, displayName, skinId, modelType, isPublic = true)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, "rooms")
      Result.failure(e)
    }
  }

  private suspend fun createNewRoom(
    uid: String,
    displayName: String,
    skinId: String,
    modelType: String,
    isPublic: Boolean,
  ): Result<JoinedRoomInfo> {
    val roomRef = db.collection("rooms").document()
    val code = generateRoomCode()
    val roomPayload =
      mapOf(
        "hostId" to uid,
        "roomCode" to code,
        "status" to "playing",
        "playerCount" to 1L,
        "memberUids" to listOf(uid),
        "isPublic" to isPublic,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    roomRef.set(roomPayload).await()
    writeInitialPlayerDoc(roomRef.id, uid, displayName, slotIndex = 0, skinId, modelType)
    return Result.success(JoinedRoomInfo(roomId = roomRef.id, roomCode = code, slotIndex = 0, isHost = true))
  }

  private suspend fun joinExistingRoom(
    room: MatchRoom,
    uid: String,
    displayName: String,
    skinId: String,
    modelType: String,
  ): Result<JoinedRoomInfo> {
    val roomRef = db.collection("rooms").document(room.id)
    val members = room.memberUids.toMutableList()
    val existingIdx = members.indexOf(uid)
    val slotIdx =
      if (existingIdx >= 0) {
        existingIdx.coerceIn(0, 4)
      } else {
        if (members.size >= 5) return Result.failure(IllegalStateException("Room is full"))
        members.add(uid)
        roomRef.update(
          mapOf(
            "memberUids" to members,
            "playerCount" to members.size.toLong(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
        (members.size - 1).coerceIn(0, 4)
      }

    writeInitialPlayerDoc(room.id, uid, displayName, slotIdx, skinId, modelType)
    return Result.success(
      JoinedRoomInfo(
        roomId = room.id,
        roomCode = room.roomCode,
        slotIndex = slotIdx,
        isHost = room.hostId == uid,
      )
    )
  }

  private suspend fun writeInitialPlayerDoc(
    roomId: String,
    uid: String,
    displayName: String,
    slotIndex: Int,
    skinId: String,
    modelType: String,
  ) {
    val playerRef = db.collection("rooms").document(roomId).collection("players").document(uid)
    val payload =
      mapOf(
        "userId" to uid,
        "displayName" to displayName.trim().ifEmpty { "Pilot" }.take(30),
        "slotIndex" to slotIndex.toLong(),
        "x" to 0.0,
        "z" to 0.0,
        "yaw" to 0.0,
        "speed" to 11.2,
        "alive" to true,
        "kills" to 0L,
        "territoryPct" to 1.0,
        "skinId" to skinId,
        "modelType" to modelType,
        "trailFlat" to emptyList<Double>(),
        "bombActive" to false,
        "bombX" to 0.0,
        "bombZ" to 0.0,
        "bombTimer" to 0.0,
        "roomOpen" to true,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    playerRef.set(payload).await()
  }

  suspend fun syncLocalPlayerState(
    roomId: String,
    state: RoomPlayer,
  ): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
    val docRef = db.collection("rooms").document(roomId).collection("players").document(uid)
    return try {
      val clampedTrail = state.trailFlat.take(150)
      docRef.update(
        mapOf(
          "displayName" to state.displayName.take(30),
          "slotIndex" to state.slotIndex.coerceIn(0L, 4L),
          "x" to state.x.coerceIn(-49.0, 49.0),
          "z" to state.z.coerceIn(-49.0, 49.0),
          "yaw" to state.yaw.coerceIn(-9.9, 9.9),
          "speed" to state.speed.coerceIn(0.0, 29.0),
          "alive" to state.alive,
          "kills" to state.kills.coerceIn(0L, 9999L),
          "territoryPct" to state.territoryPct.coerceIn(0.0, 100.0),
          "skinId" to state.skinId,
          "modelType" to state.modelType,
          "trailFlat" to clampedTrail,
          "bombActive" to state.bombActive,
          "bombX" to state.bombX.coerceIn(-49.0, 49.0),
          "bombZ" to state.bombZ.coerceIn(-49.0, 49.0),
          "bombTimer" to state.bombTimer.coerceIn(0.0, 9.9),
          "roomOpen" to true,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun leaveRoom(roomId: String) {
    val uid = auth.currentUser?.uid ?: return
    withContext(NonCancellable) {
      try {
        db.collection("rooms").document(roomId).collection("players").document(uid).delete().await()
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "rooms/$roomId/players/$uid")
      }
    }
  }

  private fun generateRoomCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
  }
}

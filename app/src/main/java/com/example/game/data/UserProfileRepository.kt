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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.max

class UserProfileRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  fun observeProfile(userId: String): Flow<UserProfile?> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Refusing cross-user profile observation." }
    val docRef = db.collection("users").document(uid)
    emitAll(
      docRef
        .snapshots()
        .map { snap ->
          snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val fsErr =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (fsErr == null && error is CancellationException) throw error
          val ex = fsErr ?: (error as? Exception)
          if (ex != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(ex, OperationType.GET, docRef.path)
          }
          throw error
        }
    )
  }

  suspend fun ensureProfile(displayName: String): Result<UserProfile> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
    val docRef = db.collection("users").document(uid)
    return try {
      val snap = docRef.get().await()
      val existing =
        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      if (existing != null) {
        Result.success(existing)
      } else {
        val cleanName = displayName.trim().ifEmpty { "Pilot" }.take(30)
        val payload =
          mapOf(
            "userId" to uid,
            "displayName" to cleanName,
            "coins" to 100L,
            "unlockedSkins" to listOf("CYBER_CYAN"),
            "equippedSkin" to "CYBER_CYAN",
            "equippedModel" to "CYBER_DRONE",
            "bestPercent" to 0.0,
            "totalKills" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(payload).await()
        val created =
          docRef.get().await().toObject(
            UserProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ) ?: UserProfile(id = uid, userId = uid, displayName = cleanName)
        Result.success(created)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun unlockAndEquipSkin(skinId: String, price: Long): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
    val docRef = db.collection("users").document(uid)
    return try {
      db.runTransaction { tx ->
        val snap = tx.get(docRef)
        val currentCoins = snap.getLong("coins") ?: 0L
        @Suppress("UNCHECKED_CAST")
        val unlocked = (snap.get("unlockedSkins") as? List<String>)?.toMutableList() ?: mutableListOf("CYBER_CYAN")
        val alreadyOwned = unlocked.contains(skinId)
        if (!alreadyOwned && currentCoins < price) {
          throw IllegalStateException("Not enough coins")
        }
        val nextCoins = if (alreadyOwned) currentCoins else (currentCoins - price).coerceAtLeast(0L)
        if (!alreadyOwned && unlocked.size < 10) {
          unlocked.add(skinId)
        }
        val modelForSkin = if (skinId == "WATERMELON") "WATERMELON_SLICE" else snap.getString("equippedModel") ?: "CYBER_DRONE"
        tx.update(
          docRef,
          mapOf(
            "coins" to nextCoins,
            "unlockedSkins" to unlocked,
            "equippedSkin" to skinId,
            "equippedModel" to modelForSkin,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun equipModel(modelName: String): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
    val docRef = db.collection("users").document(uid)
    return try {
      docRef.update(
        mapOf(
          "equippedModel" to modelName.take(30),
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

  suspend fun recordMatchRewards(coinsEarned: Long, matchBestPct: Double, killsAdded: Long): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
    val docRef = db.collection("users").document(uid)
    return try {
      db.runTransaction { tx ->
        val snap = tx.get(docRef)
        val currCoins = snap.getLong("coins") ?: 0L
        val currBest = snap.getDouble("bestPercent") ?: 0.0
        val currKills = snap.getLong("totalKills") ?: 0L
        tx.update(
          docRef,
          mapOf(
            "coins" to (currCoins + coinsEarned.coerceAtLeast(0L)).coerceAtMost(10_000_000L),
            "bestPercent" to max(currBest, matchBestPct).coerceIn(0.0, 100.0),
            "totalKills" to (currKills + killsAdded.coerceAtLeast(0L)).coerceAtMost(1_000_000L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }
}

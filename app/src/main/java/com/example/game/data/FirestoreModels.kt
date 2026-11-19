package com.example.game.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "Pilot",
  val coins: Long = 100L,
  val unlockedSkins: List<String> = listOf("CYBER_CYAN"),
  val equippedSkin: String = "CYBER_CYAN",
  val equippedModel: String = "CYBER_DRONE",
  val bestPercent: Double = 0.0,
  val totalKills: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class MatchRoom(
  @DocumentId val id: String = "",
  val hostId: String = "",
  val roomCode: String = "",
  val status: String = "playing",
  val playerCount: Long = 1L,
  val memberUids: List<String> = emptyList(),
  @get:PropertyName("isPublic") @PropertyName("isPublic") val isPublic: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class RoomPlayer(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "Pilot",
  val slotIndex: Long = 0L,
  val x: Double = 0.0,
  val z: Double = 0.0,
  val yaw: Double = 0.0,
  val speed: Double = 11.2,
  val alive: Boolean = true,
  val kills: Long = 0L,
  val territoryPct: Double = 0.0,
  val skinId: String = "CYBER_CYAN",
  val modelType: String = "CYBER_DRONE",
  val trailFlat: List<Double> = emptyList(),
  val bombActive: Boolean = false,
  val bombX: Double = 0.0,
  val bombZ: Double = 0.0,
  val bombTimer: Double = 0.0,
  val roomOpen: Boolean = true,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class JoinedRoomInfo(
  val roomId: String,
  val roomCode: String,
  val slotIndex: Int,
  val isHost: Boolean,
)

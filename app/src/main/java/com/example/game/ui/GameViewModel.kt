package com.example.game.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.data.JoinedRoomInfo
import com.example.game.data.MultiplayerRepository
import com.example.game.data.UserProfile
import com.example.game.data.UserProfileRepository
import com.example.game.engine.GameHudState
import com.example.game.engine.GameWorld3D
import com.example.game.engine.UnitModelType
import com.example.game.engine.UnitSkin
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameViewModel(
  private val profileRepository: UserProfileRepository,
  private val multiplayerRepository: MultiplayerRepository,
  private val currentUserId: String,
  private val initialDisplayName: String,
) : ViewModel() {

  val world = GameWorld3D()

  val hudState: StateFlow<GameHudState> = world.hudState

  private val _joystickState = MutableStateFlow(JoystickOverlayState())
  val joystickState: StateFlow<JoystickOverlayState> = _joystickState.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private var profileJob: Job? = null
  private var roomObserveJob: Job? = null
  private var roomSyncJob: Job? = null
  private var activeRoom: JoinedRoomInfo? = null
  private var latestProfile: UserProfile? = null

  init {
    world.onMatchCompleted = { coinsEarned, bestPct, kills ->
      if (coinsEarned > 0L || kills > 0L || bestPct > 0.0) {
        viewModelScope.launch {
          profileRepository.recordMatchRewards(coinsEarned, bestPct, kills)
        }
      }
    }

    viewModelScope.launch {
      profileRepository.ensureProfile(initialDisplayName).onFailure { err ->
        _statusBanner.value = err.localizedMessage
      }
    }

    profileJob =
      viewModelScope.launch {
        profileRepository
          .observeProfile(currentUserId)
          .catch { error ->
            val fsErr =
              generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Profile stream error", root)
            _statusBanner.value = root.message ?: "Failed to sync profile"
          }
          .collect { profile ->
            if (profile != null) {
              latestProfile = profile
              world.syncProfileMeta(
                coins = profile.coins,
                unlocked = profile.unlockedSkins,
                equippedSkinId = profile.equippedSkin,
                equippedModelName = profile.equippedModel,
                displayName = profile.displayName,
              )
            }
          }
      }
  }

  fun clearStatusBanner() {
    _statusBanner.value = null
  }

  fun updateJoystickOverlay(state: JoystickOverlayState) {
    _joystickState.value = state
  }

  fun selectModel(model: UnitModelType) {
    world.selectPlayerModel(model)
    viewModelScope.launch {
      profileRepository.equipModel(model.name)
    }
  }

  fun selectOrBuySkin(skin: UnitSkin) {
    val owned = latestProfile?.unlockedSkins?.contains(skin.id) == true || skin.price == 0L
    val coins = latestProfile?.coins ?: world.hudState.value.profileCoins
    if (!owned && coins < skin.price) {
      _statusBanner.value = "Need ${skin.price} coins to unlock ${skin.displayName}"
      return
    }
    world.selectPlayerSkin(skin)
    viewModelScope.launch {
      profileRepository.unlockAndEquipSkin(skin.id, skin.price).onFailure { err ->
        _statusBanner.value = err.localizedMessage ?: "Failed to unlock skin"
      }
    }
  }

  fun dropBomb() {
    world.dropPlayerBomb()
  }

  fun startSoloMatch() {
    leaveActiveRoomAsync()
    _joystickState.value = JoystickOverlayState(isActive = false)
    world.setMultiplayerRoomInfo(enabled = false, roomCode = null)
    world.startNewMatch()
  }

  fun returnToLobby() {
    leaveActiveRoomAsync()
    _joystickState.value = JoystickOverlayState(isActive = false)
    world.setMultiplayerRoomInfo(enabled = false, roomCode = null)
    world.returnToMenu()
  }

  fun startQuickMultiplayer() {
    if (_isBusy.value) return
    _isBusy.value = true
    _statusBanner.value = null
    viewModelScope.launch {
      val state = world.hudState.value
      val nick = latestProfile?.displayName ?: initialDisplayName
      val res =
        multiplayerRepository.quickJoinOrCreateRoom(
          displayName = nick,
          skinId = state.selectedSkin.id,
          modelType = state.selectedModel.name,
        )
      _isBusy.value = false
      res.fold(
        onSuccess = { room -> attachToRoom(room, nick) },
        onFailure = { err -> _statusBanner.value = err.localizedMessage ?: "Failed to join room" },
      )
    }
  }

  fun joinMultiplayerByCode(code: String) {
    if (_isBusy.value) return
    _isBusy.value = true
    _statusBanner.value = null
    viewModelScope.launch {
      val state = world.hudState.value
      val nick = latestProfile?.displayName ?: initialDisplayName
      val res =
        multiplayerRepository.joinByCode(
          roomCode = code,
          displayName = nick,
          skinId = state.selectedSkin.id,
          modelType = state.selectedModel.name,
        )
      _isBusy.value = false
      res.fold(
        onSuccess = { room -> attachToRoom(room, nick) },
        onFailure = { err -> _statusBanner.value = err.localizedMessage ?: "Room not found" },
      )
    }
  }

  private fun attachToRoom(room: JoinedRoomInfo, displayName: String) {
    roomObserveJob?.cancel()
    roomSyncJob?.cancel()
    activeRoom = room
    _joystickState.value = JoystickOverlayState(isActive = false)
    world.setMultiplayerRoomInfo(enabled = true, roomCode = room.roomCode)
    world.startNewMatch()

    roomObserveJob =
      viewModelScope.launch {
        multiplayerRepository
          .observeRoomPlayers(room.roomId)
          .catch { error ->
            val fsErr =
              generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Room players stream error", fsErr ?: error)
          }
          .collect { players ->
            world.applyRemotePlayers(players, currentUserId)
          }
      }

    roomSyncJob =
      viewModelScope.launch {
        while (isActive) {
          val localState = world.buildLocalRoomPlayerState(currentUserId, displayName, room.slotIndex)
          multiplayerRepository.syncLocalPlayerState(room.roomId, localState)
          delay(120L)
        }
      }
  }

  fun leaveActiveRoomAsync() {
    val room = activeRoom ?: return
    activeRoom = null
    roomObserveJob?.cancel()
    roomSyncJob?.cancel()
    world.setMultiplayerRoomInfo(enabled = false, roomCode = null)
    viewModelScope.launch {
      multiplayerRepository.leaveRoom(room.roomId)
    }
  }

  suspend fun cleanupBeforeSignOut() {
    withContext(NonCancellable) {
      profileJob?.cancel()
      roomObserveJob?.cancel()
      roomSyncJob?.cancel()
      activeRoom?.let { multiplayerRepository.leaveRoom(it.roomId) }
      activeRoom = null
    }
  }

  override fun onCleared() {
    super.onCleared()
    roomObserveJob?.cancel()
    roomSyncJob?.cancel()
    profileJob?.cancel()
  }

  private companion object {
    const val TAG = "GameViewModel"
  }
}

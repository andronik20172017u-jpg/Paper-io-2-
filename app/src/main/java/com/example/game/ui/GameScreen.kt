package com.example.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.game.engine.GameHudState
import com.example.game.engine.GamePalettes
import com.example.game.engine.LeaderboardEntry
import com.example.game.engine.MatchPhase
import com.example.game.engine.UnitModelType
import com.example.game.engine.UnitSkin
import com.example.ui.theme.BrightCyan
import com.example.ui.theme.CyberSlate
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VenomGreen
import java.util.Locale

@Composable
fun GameScreen(viewModel: GameViewModel) {
  val hud by viewModel.hudState.collectAsStateWithLifecycle()
  val joystick by viewModel.joystickState.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  val lifecycleOwner = LocalLifecycleOwner.current

  BackHandler(enabled = hud.phase != MatchPhase.MENU) {
    viewModel.returnToLobby()
  }

  val glView = remember {
    GameGLSurfaceView(
      context = context,
      world = viewModel.world,
      onJoystickChanged = viewModel::updateJoystickOverlay,
    )
  }

  DisposableEffect(lifecycleOwner, glView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> glView.onResume()
        Lifecycle.Event.ON_PAUSE -> glView.onPause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      glView.onPause()
    }
  }

  Box(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
    AndroidView(
      factory = { glView },
      modifier = Modifier.fillMaxSize().testTag("gl_arena_viewport"),
    )

    if (joystick.isActive && hud.phase == MatchPhase.PLAYING) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val anchor = Offset(joystick.anchorX, joystick.anchorY)
        val knob = Offset(joystick.knobX, joystick.knobY)
        drawCircle(
          color = ElectricCyan.copy(alpha = 0.14f),
          radius = joystick.radiusPx,
          center = anchor,
        )
        drawCircle(
          color = BrightCyan.copy(alpha = 0.55f),
          radius = joystick.radiusPx,
          center = anchor,
          style = Stroke(width = 3.dp.toPx()),
        )
        drawLine(
          color = BrightCyan.copy(alpha = 0.4f),
          start = anchor,
          end = knob,
          strokeWidth = 2.dp.toPx(),
        )
        drawCircle(
          color = BrightCyan.copy(alpha = 0.85f),
          radius = joystick.radiusPx * 0.36f,
          center = knob,
        )
      }
    }

    BoxWithConstraints(
      modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)
    ) {
      val isWide = maxWidth >= 600.dp

      // Active match HUD
      if (hud.phase == MatchPhase.PLAYING) {
        TopHudBar(
          hud = hud,
          isWide = isWide,
          modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        )

        // Floating "+0.45%" / "+15 COINS" popups above center unit
        Column(
          modifier = Modifier.align(Alignment.Center).padding(bottom = 110.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          hud.floatingPopups.forEach { pop ->
            val alpha = (1f - pop.age / pop.maxAge).coerceIn(0f, 1f)
            Text(
              text = pop.text,
              color = Color(pop.colorHex).copy(alpha = alpha),
              fontWeight = FontWeight.Black,
              fontSize = 18.sp,
            )
          }
        }

        // Bottom HUD row: Tactical Bomb button + Hint + Circular Radar Minimap
        Row(
          modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Bottom,
        ) {
          // Drop Bomb tactical button
          Button(
            onClick = viewModel::dropBomb,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48), contentColor = Color.White),
            shape = CircleShape,
            border = BorderStroke(2.dp, Color(0xFFFDA4AF)),
            modifier = Modifier.size(66.dp).testTag("drop_bomb_button"),
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = stringResource(R.string.drop_bomb),
                modifier = Modifier.size(22.dp),
              )
              Text(
                text = stringResource(R.string.drop_bomb),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
              )
            }
          }

          if (!joystick.isActive) {
            Surface(
              color = CyberSlate.copy(alpha = 0.68f),
              shape = RoundedCornerShape(999.dp),
              border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.25f)),
              modifier = Modifier.padding(bottom = 8.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Explore,
                  contentDescription = null,
                  tint = BrightCyan,
                  modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text =
                    if (hud.isMultiplayer) "ROOM ${hud.activeRoomCode} • ${hud.humanPlayersCount}P"
                    else stringResource(R.string.joystick_hint),
                  color = TextMuted,
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }
          } else {
            Spacer(Modifier.width(8.dp))
          }

          // Live Circular Radar Minimap
          MinimapRadar(hud = hud, modifier = Modifier.size(104.dp))
        }
      }

      // Elimination / Takedown Banner
      AnimatedVisibility(
        visible = hud.eliminationBanner != null && hud.phase == MatchPhase.PLAYING,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 104.dp),
      ) {
        Surface(
          color = NeonCrimson.copy(alpha = 0.90f),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
        ) {
          Text(
            text = hud.eliminationBanner.orEmpty(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
      }

      // Status error / info toast
      if (statusBanner != null) {
        Surface(
          color = CyberSlate.copy(alpha = 0.94f),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, SolarAmber.copy(alpha = 0.6f)),
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          ) {
            Text(text = statusBanner.orEmpty(), color = TextPrimary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = viewModel::clearStatusBanner, modifier = Modifier.size(24.dp)) {
              Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
          }
        }
      }

      // Main Menu: Skins, Coins, Craft Selector, Solo & Multiplayer Rooms
      AnimatedVisibility(
        visible = hud.phase == MatchPhase.MENU,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center),
      ) {
        MenuOverlayCard(
          hud = hud,
          isBusy = isBusy,
          onSelectModel = viewModel::selectModel,
          onSelectOrBuySkin = viewModel::selectOrBuySkin,
          onLaunchSolo = viewModel::startSoloMatch,
          onQuickMultiplayer = viewModel::startQuickMultiplayer,
          onJoinRoomCode = viewModel::joinMultiplayerByCode,
          onSignOut = {
            signOutUser(
              credentialManager = credentialManager,
              scope = scope,
              onPreSignOutCleanup = { viewModel.cleanupBeforeSignOut() },
            )
          },
        )
      }

      // "100% PERFECT WIN!" / Game Over Overlay
      AnimatedVisibility(
        visible = hud.phase == MatchPhase.GAME_OVER || hud.phase == MatchPhase.VICTORY,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center),
      ) {
        EndMatchCard(
          hud = hud,
          onRedeploy = {
            if (hud.isMultiplayer) viewModel.startQuickMultiplayer() else viewModel.startSoloMatch()
          },
          onBackToLobby = viewModel::returnToLobby,
          onChangeUnit = {
            val next =
              when (hud.selectedModel) {
                UnitModelType.CYBER_DRONE -> UnitModelType.HEX_CRUISER
                UnitModelType.HEX_CRUISER -> UnitModelType.WATERMELON_SLICE
                else -> UnitModelType.CYBER_DRONE
              }
            viewModel.selectModel(next)
          },
        )
      }
    }
  }
}

@Composable
private fun TopHudBar(hud: GameHudState, isWide: Boolean, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
  ) {
    // Territory 0%-100% progress + Skull Kills + Coins
    Surface(
      color = CyberSlate.copy(alpha = 0.80f),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f)),
      modifier = Modifier.widthIn(min = 165.dp, max = if (isWide) 260.dp else 198.dp),
    ) {
      Column(modifier = Modifier.padding(11.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = stringResource(R.string.territory_label),
            color = TextMuted,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
          )
          Text(
            text = String.format(Locale.US, "%.1f%%", hud.playerTerritoryPercent),
            color = BrightCyan,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
          progress = { (hud.playerTerritoryPercent / 100f).coerceIn(0.02f, 1f) },
          color = ElectricCyan,
          trackColor = DeepSpace,
          modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.Dangerous,
              contentDescription = stringResource(R.string.eliminations_label),
              tint = NeonCrimson,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "${hud.playerKills}",
              color = TextPrimary,
              fontWeight = FontWeight.ExtraBold,
              style = MaterialTheme.typography.labelLarge,
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.MonetizationOn,
              contentDescription = "Coins",
              tint = SolarAmber,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "+${hud.matchCoinsEarned}",
              color = SolarAmber,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }
    }

    // Live Arena Leaderboard with Crown icon on #1
    Surface(
      color = CyberSlate.copy(alpha = 0.78f),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
      modifier = Modifier.widthIn(min = 140.dp, max = if (isWide) 220.dp else 164.dp),
    ) {
      Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
        Text(
          text = stringResource(R.string.leaderboard_title),
          color = TextMuted,
          style = MaterialTheme.typography.labelSmall,
          letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(5.dp))
        hud.leaderboard.take(5).forEach { entry ->
          LeaderboardRow(entry)
        }
      }
    }
  }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
      if (entry.isLeader) {
        Icon(
          imageVector = Icons.Filled.WorkspacePremium,
          contentDescription = "Leader",
          tint = SolarAmber,
          modifier = Modifier.size(13.dp),
        )
      } else {
        Box(
          modifier =
            Modifier.size(8.dp)
              .clip(CircleShape)
              .background(Color(entry.colorHex).copy(alpha = if (entry.isAlive) 1f else 0.35f))
        )
      }
      Spacer(Modifier.width(5.dp))
      Text(
        text = entry.name,
        color = if (entry.isHuman) BrightCyan else TextPrimary.copy(alpha = if (entry.isAlive) 0.9f else 0.45f),
        fontWeight = if (entry.isHuman) FontWeight.Bold else FontWeight.Medium,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.width(4.dp))
    Text(
      text = String.format(Locale.US, "%.1f%%", entry.percent),
      color = if (entry.isHuman) BrightCyan else TextMuted,
      fontWeight = FontWeight.SemiBold,
      style = MaterialTheme.typography.labelMedium,
    )
  }
}

@Composable
private fun MinimapRadar(hud: GameHudState, modifier: Modifier = Modifier) {
  Surface(
    color = CyberSlate.copy(alpha = 0.78f),
    shape = CircleShape,
    border = BorderStroke(1.5.dp, ElectricCyan.copy(alpha = 0.45f)),
    modifier = modifier,
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
      val radius = size.minDimension / 2f
      val cx = size.width / 2f
      val cy = size.height / 2f

      drawCircle(color = DeepSpace.copy(alpha = 0.85f), radius = radius, center = Offset(cx, cy))
      drawCircle(
        color = ElectricCyan.copy(alpha = 0.25f),
        radius = radius * 0.5f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.dp.toPx()),
      )

      // Territory cells
      for ((nx, nz, ownerId) in hud.minimapCells) {
        val col =
          if (ownerId == 0) Color(hud.selectedSkin.uiColorHex)
          else Color(GamePalettes.ALL.getOrNull(ownerId)?.uiColorHex ?: 0xFFFFFFFF)
        drawCircle(
          color = col.copy(alpha = 0.65f),
          radius = 2.1.dp.toPx(),
          center = Offset(cx + nx * radius * 0.92f, cy + nz * radius * 0.92f),
        )
      }

      // Bomb dots
      for (b in hud.minimapBombs) {
        drawCircle(
          color = Color(0xFFFF4500),
          radius = (3.2f + b.pulse * 2.5f).dp.toPx(),
          center = Offset(cx + b.normX * radius * 0.92f, cy + b.normZ * radius * 0.92f),
        )
      }

      // Units
      for (u in hud.minimapUnits) {
        val dotR = if (u.isHuman) 4.dp.toPx() else if (u.isThief) 3.4.dp.toPx() else 2.8.dp.toPx()
        drawCircle(
          color = if (u.isThief) Color(0xFFFF6D00) else Color(u.colorHex),
          radius = dotR,
          center = Offset(cx + u.normX * radius * 0.92f, cy + u.normZ * radius * 0.92f),
        )
        if (u.isHuman) {
          drawCircle(
            color = Color.White,
            radius = dotR + 1.dp.toPx(),
            center = Offset(cx + u.normX * radius * 0.92f, cy + u.normZ * radius * 0.92f),
            style = Stroke(width = 1.dp.toPx()),
          )
        }
      }
    }
  }
}

@Composable
private fun MenuOverlayCard(
  hud: GameHudState,
  isBusy: Boolean,
  onSelectModel: (UnitModelType) -> Unit,
  onSelectOrBuySkin: (UnitSkin) -> Unit,
  onLaunchSolo: () -> Unit,
  onQuickMultiplayer: () -> Unit,
  onJoinRoomCode: (String) -> Unit,
  onSignOut: () -> Unit,
) {
  var roomCodeInput by remember { mutableStateOf("") }

  Card(
    colors = CardDefaults.cardColors(containerColor = CyberSlate.copy(alpha = 0.90f)),
    shape = RoundedCornerShape(24.dp),
    border = BorderStroke(1.5.dp, ElectricCyan.copy(alpha = 0.45f)),
    modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
  ) {
    Column(
      modifier =
        Modifier.background(
          Brush.verticalGradient(listOf(ElectricCyan.copy(alpha = 0.14f), Color.Transparent))
        )
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = SolarAmber.copy(alpha = 0.16f),
          shape = RoundedCornerShape(999.dp),
          border = BorderStroke(1.dp, SolarAmber.copy(alpha = 0.5f)),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = SolarAmber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(
              text = "${hud.profileCoins}",
              color = SolarAmber,
              fontWeight = FontWeight.ExtraBold,
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }

        IconButton(onClick = onSignOut, modifier = Modifier.testTag("sign_out_button")) {
          Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = TextMuted)
        }
      }

      Text(
        text = stringResource(R.string.app_name).uppercase(Locale.US),
        color = BrightCyan,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        letterSpacing = 2.sp,
      )
      Text(
        text = stringResource(R.string.tagline),
        color = TextMuted,
        style = MaterialTheme.typography.bodySmall,
      )

      Spacer(Modifier.height(14.dp))

      Text(
        text = stringResource(R.string.choose_craft),
        color = TextMuted,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.2.sp,
      )
      Spacer(Modifier.height(8.dp))

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CraftOptionButton(
          label = stringResource(R.string.craft_drone),
          icon = Icons.Filled.RocketLaunch,
          selected = hud.selectedModel == UnitModelType.CYBER_DRONE,
          onClick = { onSelectModel(UnitModelType.CYBER_DRONE) },
          modifier = Modifier.weight(1f).testTag("select_cyber_drone"),
        )
        CraftOptionButton(
          label = stringResource(R.string.craft_cruiser),
          icon = Icons.Filled.Shield,
          selected = hud.selectedModel == UnitModelType.HEX_CRUISER,
          onClick = { onSelectModel(UnitModelType.HEX_CRUISER) },
          modifier = Modifier.weight(1f).testTag("select_hex_cruiser"),
        )
        CraftOptionButton(
          label = stringResource(R.string.craft_melon),
          icon = Icons.Filled.Spa,
          selected = hud.selectedModel == UnitModelType.WATERMELON_SLICE,
          onClick = { onSelectModel(UnitModelType.WATERMELON_SLICE) },
          modifier = Modifier.weight(1f).testTag("select_melon_slice"),
        )
      }

      Spacer(Modifier.height(14.dp))

      Text(
        text = stringResource(R.string.choose_skin),
        color = TextMuted,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.2.sp,
      )
      Spacer(Modifier.height(8.dp))

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(UnitSkin.entries) { skin ->
          val unlocked = hud.unlockedSkins.contains(skin.id) || skin.price == 0L
          val selected = hud.selectedSkin == skin
          SkinPillCard(
            skin = skin,
            unlocked = unlocked,
            selected = selected,
            onClick = { onSelectOrBuySkin(skin) },
          )
        }
      }

      Spacer(Modifier.height(16.dp))

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = onLaunchSolo,
          enabled = !isBusy,
          colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepSpace),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).height(50.dp).testTag("launch_arena_button"),
        ) {
          Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.play_now),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
          )
        }

        Button(
          onClick = onQuickMultiplayer,
          enabled = !isBusy,
          colors = ButtonDefaults.buttonColors(containerColor = VenomGreen, contentColor = DeepSpace),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).height(50.dp).testTag("quick_online_button"),
        ) {
          Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.play_multiplayer),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
          )
        }
      }

      Spacer(Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = roomCodeInput,
          onValueChange = { roomCodeInput = it.uppercase(Locale.US).take(10) },
          placeholder = { Text(stringResource(R.string.room_code_hint), fontSize = 13.sp) },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("room_code_input"),
        )
        OutlinedButton(
          onClick = { onJoinRoomCode(roomCodeInput) },
          enabled = !isBusy && roomCodeInput.length >= 4,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(52.dp).testTag("join_room_button"),
        ) {
          Text(stringResource(R.string.join_room), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
      }
    }
  }
}

@Composable
private fun SkinPillCard(
  skin: UnitSkin,
  unlocked: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val borderColor = if (selected) BrightCyan else Color.White.copy(alpha = 0.16f)
  Surface(
    color = if (selected) Color(skin.uiColorHex).copy(alpha = 0.24f) else DeepSpace.copy(alpha = 0.6f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.5.dp, borderColor),
    modifier = Modifier.clickable(onClick = onClick).testTag("skin_${skin.id}"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier =
          Modifier.size(22.dp)
            .clip(CircleShape)
            .background(Color(skin.uiColorHex))
            .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        if (!unlocked) {
          Icon(Icons.Filled.Lock, contentDescription = null, tint = DeepSpace, modifier = Modifier.size(13.dp))
        }
      }
      Spacer(Modifier.height(4.dp))
      Text(
        text = skin.displayName,
        color = TextPrimary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        style = MaterialTheme.typography.labelSmall,
      )
      Text(
        text = if (unlocked) (if (selected) "EQUIPPED" else "OWNED") else "${skin.price} C",
        color = if (unlocked) BrightCyan else SolarAmber,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun CraftOptionButton(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val borderColor = if (selected) BrightCyan else Color.White.copy(alpha = 0.16f)
  val bgColor = if (selected) ElectricCyan.copy(alpha = 0.18f) else DeepSpace.copy(alpha = 0.55f)
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(14.dp))
        .background(bgColor)
        .border(BorderStroke(1.5.dp, borderColor), RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .padding(vertical = 10.dp, horizontal = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = if (selected) BrightCyan else TextMuted,
      modifier = Modifier.size(22.dp),
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = label,
      color = if (selected) TextPrimary else TextMuted,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      style = MaterialTheme.typography.labelSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun EndMatchCard(
  hud: GameHudState,
  onRedeploy: () -> Unit,
  onBackToLobby: () -> Unit,
  onChangeUnit: () -> Unit,
) {
  val isVictory = hud.phase == MatchPhase.VICTORY
  val accent = if (isVictory) VenomGreen else NeonCrimson

  Card(
    colors = CardDefaults.cardColors(containerColor = CyberSlate.copy(alpha = 0.92f)),
    shape = RoundedCornerShape(24.dp),
    border = BorderStroke(2.dp, accent.copy(alpha = 0.72f)),
    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = if (isVictory) Icons.Filled.WorkspacePremium else Icons.Filled.EmojiEvents,
        contentDescription = null,
        tint = if (isVictory) SolarAmber else accent,
        modifier = Modifier.size(52.dp),
      )
      Spacer(Modifier.height(10.dp))
      Text(
        text = stringResource(if (isVictory) R.string.victory_title else R.string.defeat_title),
        color = if (isVictory) SolarAmber else accent,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        letterSpacing = 1.5.sp,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = stringResource(if (isVictory) R.string.victory_hint else R.string.defeat_hint),
        color = TextMuted,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
      )

      Spacer(Modifier.height(18.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        StatMetric(
          label = stringResource(R.string.best_label),
          value = String.format(Locale.US, "%.1f%%", hud.bestTerritoryPercent),
        )
        StatMetric(
          label = stringResource(R.string.eliminations_label),
          value = hud.playerKills.toString(),
        )
        StatMetric(
          label = "COINS",
          value = "+${hud.matchCoinsEarned}",
        )
      }

      Spacer(Modifier.height(18.dp))

      Button(
        onClick = onRedeploy,
        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepSpace),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("redeploy_button"),
      ) {
        Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.play_again),
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 1.sp,
        )
      }

      Spacer(Modifier.height(8.dp))

      Surface(
        color = DeepSpace.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onChangeUnit).testTag("switch_unit_button"),
      ) {
        Text(
          text = stringResource(R.string.switch_unit_format, hud.selectedModel.displayName),
          color = TextPrimary,
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.padding(vertical = 12.dp),
        )
      }

      Spacer(Modifier.height(8.dp))

      OutlinedButton(
        onClick = onBackToLobby,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("back_to_lobby_button"),
      ) {
        Text(
          text = stringResource(R.string.back_to_lobby),
          color = BrightCyan,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
        )
      }
    }
  }
}

@Composable
private fun StatMetric(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = label, color = TextMuted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
    Spacer(Modifier.height(2.dp))
    Text(
      text = value,
      color = BrightCyan,
      fontWeight = FontWeight.ExtraBold,
      fontSize = 20.sp,
    )
  }
}

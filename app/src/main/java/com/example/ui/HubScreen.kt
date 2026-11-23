package com.example.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.signOutFromApp
import com.example.model.CameraMode
import com.example.model.ConvoyMessage
import com.example.model.ConvoyPlayer
import com.example.model.ConvoyRoom
import com.example.model.DeliveryRecord
import com.example.model.DriverProfile
import com.example.model.EnvironmentTheme
import com.example.model.LiveryStyle
import com.example.model.RouteCatalog
import com.example.model.RouteSpec
import com.example.model.SteeringMode
import com.example.model.TruckCabStyle
import com.example.model.TruckCatalog
import com.example.model.TruckSpec
import com.example.model.TruckVisualConfig
import com.example.model.WorkshopCatalog
import com.example.sim.Truck2DArtwork.parseHexColor
import com.example.ui.theme.AmberDiesel
import com.example.ui.theme.CabMetal
import com.example.ui.theme.CobaltBeacon
import com.example.ui.theme.CrimsonBrake
import com.example.ui.theme.EmeraldCargo
import com.example.ui.theme.GoldTrophy
import com.example.ui.theme.GunmetalBorder
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SteelSlate
import com.example.viewmodel.CareerViewModel
import com.example.viewmodel.ConvoyViewModel
import com.example.viewmodel.UiState

@Composable
fun HubScreen(
    viewModel: CareerViewModel,
    convoyViewModel: ConvoyViewModel,
    onStartDrive: (
        truck: TruckSpec,
        route: RouteSpec,
        steeringMode: SteeringMode,
        sensitivity: Float,
        cameraMode: CameraMode,
        visualConfig: TruckVisualConfig,
        initialDamagePercent: Int,
        activeConvoyRoomId: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val deliveriesState by viewModel.deliveriesState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val convoyRoomsState by convoyViewModel.publicRoomsState.collectAsStateWithLifecycle()
    val activeConvoyRoom by convoyViewModel.activeRoom.collectAsStateWithLifecycle()
    val convoyPlayers by convoyViewModel.players.collectAsStateWithLifecycle()
    val convoyMessages by convoyViewModel.messages.collectAsStateWithLifecycle()
    val convoyMessage by convoyViewModel.actionMessage.collectAsStateWithLifecycle()
    val convoyBusy by convoyViewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(convoyMessage) {
        val msg = convoyMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            convoyViewModel.clearMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianNight)) {
        val useRail = maxWidth >= 600.dp

        val tabs = listOf(
            Triple(stringResource(id = R.string.tab_routes), Icons.Filled.Route, Icons.Outlined.Route),
            Triple(stringResource(id = R.string.tab_garage), Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
            Triple(stringResource(id = R.string.tab_convoy), Icons.Filled.Groups, Icons.Outlined.Groups),
            Triple(stringResource(id = R.string.tab_career), Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents)
        )

        Scaffold(
            containerColor = ObsidianNight,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = SteelSlate,
                        contentColor = Color.White,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        tabs.forEachIndexed { idx, (label, filled, outlined) ->
                            val selected = selectedTab == idx
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) filled else outlined,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ObsidianNight,
                                    selectedTextColor = AmberDiesel,
                                    indicatorColor = AmberDiesel,
                                    unselectedIconColor = MutedSteel,
                                    unselectedTextColor = MutedSteel
                                ),
                                modifier = Modifier.testTag("nav_tab_$idx")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = SteelSlate,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()
                    ) {
                        Spacer(Modifier.height(16.dp))
                        tabs.forEachIndexed { idx, (label, filled, outlined) ->
                            val selected = selectedTab == idx
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(if (selected) filled else outlined, contentDescription = label)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ObsidianNight,
                                    selectedTextColor = AmberDiesel,
                                    indicatorColor = AmberDiesel,
                                    unselectedIconColor = MutedSteel,
                                    unselectedTextColor = MutedSteel
                                )
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val pState = profileState) {
                        is UiState.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = AmberDiesel)
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = "Sincronizando flota y licencia…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedSteel
                                    )
                                }
                            }
                        }
                        is UiState.Error -> {
                            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SteelSlate),
                                    modifier = Modifier.widthIn(max = 460.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Error de conexión",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = CrimsonBrake
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = pState.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MutedSteel
                                        )
                                    }
                                }
                            }
                        }
                        is UiState.Success -> {
                            val profile = pState.data
                            val activeTruck = TruckCatalog.byId(profile.selectedTruckId)
                            val baseVisualConfig = profile.toVisualConfig(activeTruck)

                            DriverTopBar(
                                profile = profile,
                                activeTruck = activeTruck,
                                onSignOut = { signOutFromApp(credentialManager, scope) }
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 780.dp)
                            ) {
                                when (selectedTab) {
                                    0 -> RoutesDispatchTab(
                                        profile = profile,
                                        activeTruck = activeTruck,
                                        visualConfig = baseVisualConfig,
                                        onLaunchRoute = { route ->
                                            val routeVisual = profile.toVisualConfig(activeTruck, route.trailerType)
                                            onStartDrive(
                                                activeTruck,
                                                route,
                                                profile.resolvedSteeringMode(),
                                                profile.steeringSensitivity.toFloat(),
                                                profile.resolvedCameraMode(),
                                                routeVisual,
                                                profile.truckDamagePercent.toInt(),
                                                null
                                            )
                                        }
                                    )
                                    1 -> GarageFleetTab(
                                        profile = profile,
                                        activeTruck = activeTruck,
                                        isBusy = isBusy,
                                        onSelectOrPurchase = { truck ->
                                            viewModel.selectOrPurchaseTruck(truck)
                                        },
                                        onSaveWorkshop = { paintHex, livery, bull, led, aero, exh, cost ->
                                            viewModel.saveWorkshopCustomization(
                                                paintColorHex = paintHex,
                                                liveryStyle = livery,
                                                bullbar = bull,
                                                roofLightbar = led,
                                                aeroSpoiler = aero,
                                                chromeExhaust = exh,
                                                totalCostCredits = cost
                                            )
                                        },
                                        onRepairInGarage = { viewModel.repairTruckInGarage() }
                                    )
                                    2 -> ConvoyMultiplayerTab(
                                        profile = profile,
                                        publicRoomsState = convoyRoomsState,
                                        activeRoom = activeConvoyRoom,
                                        players = convoyPlayers,
                                        messages = convoyMessages,
                                        isBusy = convoyBusy,
                                        onCreateRoom = { name, routeId, isPrivate ->
                                            convoyViewModel.createRoom(name, routeId, isPrivate, profile)
                                        },
                                        onJoinRoom = { codeOrId ->
                                            convoyViewModel.joinRoom(codeOrId, profile)
                                        },
                                        onLeaveRoom = { convoyViewModel.leaveRoom() },
                                        onUpdateHostRoute = { room, newRouteId ->
                                            convoyViewModel.updateHostRouteOrStatus(newRouteId, room.status)
                                        },
                                        onSendChat = { text ->
                                            convoyViewModel.sendChatMessage(profile.driverName, text)
                                        },
                                        onLaunchConvoyDrive = { room ->
                                            val convoyRoute = RouteCatalog.byId(room.routeId)
                                            if (room.hostId == profile.userId) {
                                                convoyViewModel.updateHostRouteOrStatus(room.routeId, "DRIVING")
                                            }
                                            val routeVisual = profile.toVisualConfig(activeTruck, convoyRoute.trailerType)
                                            onStartDrive(
                                                activeTruck,
                                                convoyRoute,
                                                profile.resolvedSteeringMode(),
                                                profile.steeringSensitivity.toFloat(),
                                                profile.resolvedCameraMode(),
                                                routeVisual,
                                                profile.truckDamagePercent.toInt(),
                                                room.id
                                            )
                                        }
                                    )
                                    3 -> CareerAndControlsTab(
                                        profile = profile,
                                        deliveriesState = deliveriesState,
                                        isBusy = isBusy,
                                        onSaveSettings = { mode, sens, cam, name ->
                                            viewModel.updateControlSettings(mode, sens, cam, name)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverTopBar(
    profile: DriverProfile,
    activeTruck: TruckSpec,
    onSignOut: () -> Unit
) {
    Surface(
        color = SteelSlate,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GunmetalBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Image(
                    painter = painterResource(id = R.drawable.img_truck_icon),
                    contentDescription = "Icono del conductor",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = profile.driverName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile.driverRankTitle()} • ${activeTruck.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberDiesel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = GoldTrophy.copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier.border(1.dp, GoldTrophy.copy(alpha = 0.55f), CircleShape)
                ) {
                    Text(
                        text = "$${profile.credits}",
                        style = MaterialTheme.typography.labelLarge,
                        color = GoldTrophy,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier.size(48.dp).testTag("btn_sign_out")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(id = R.string.sign_out),
                        tint = MutedSteel
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutesDispatchTab(
    profile: DriverProfile,
    activeTruck: TruckSpec,
    visualConfig: TruckVisualConfig,
    onLaunchRoute: (RouteSpec) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = SteelSlate),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GunmetalBorder, MaterialTheme.shapes.large)
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(152.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.img_truck_hero),
                            contentDescription = "Banner de despacho de rutas",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(ObsidianNight.copy(alpha = 0.94f), ObsidianNight.copy(alpha = 0.42f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "TRACTOCAMIÓN ACTIVO • ${activeTruck.horsepower} HP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberDiesel
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = activeTruck.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Control: ${profile.resolvedSteeringMode().label} • ${profile.resolvedCameraMode().label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniStatBadge("${profile.deliveriesCompleted} Entregas")
                                MiniStatBadge("${profile.totalDistanceKm.toInt()} km")
                                if (profile.truckDamagePercent > 0L) {
                                    MiniStatBadge("Daño ${profile.truckDamagePercent}%")
                                }
                            }
                        }
                    }
                    Truck2DShowroomPreview(
                        truck = activeTruck,
                        visualConfig = visualConfig,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                    )
                }
            }
        }

        item {
            Text(
                text = "MUNDO ABIERTO Y CONTRATOS DE CARGA",
                style = MaterialTheme.typography.labelLarge,
                color = MutedSteel,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(RouteCatalog.routes, key = { it.id }) { route ->
            RouteCard(route = route, onLaunch = { onLaunchRoute(route) })
        }
    }
}

@Composable
private fun RouteCard(route: RouteSpec, onLaunch: () -> Unit) {
    val routePhotoRes = when (route.environment) {
        EnvironmentTheme.DESERT_NIGHT -> R.drawable.img_real_highway_night
        EnvironmentTheme.COASTAL_SUNSET, EnvironmentTheme.OPEN_VALLEY -> R.drawable.img_truck_hero
        EnvironmentTheme.ALPINE_DAY, EnvironmentTheme.STORM_SUMMIT -> R.drawable.img_real_highway_alpine
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = SteelSlate),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (route.isFreeRoam) 1.5.dp else 1.dp,
                color = if (route.isFreeRoam) EmeraldCargo else GunmetalBorder,
                shape = MaterialTheme.shapes.medium
            )
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(122.dp)) {
                Image(
                    painter = painterResource(id = routePhotoRes),
                    contentDescription = "Vista previa real de ${route.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ObsidianNight.copy(alpha = 0.25f),
                                    SteelSlate.copy(alpha = 0.94f)
                                )
                            )
                        )
                )
                if (route.isFreeRoam) {
                    Surface(
                        color = EmeraldCargo,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.Explore, contentDescription = null, tint = ObsidianNight, modifier = Modifier.size(14.dp))
                            Text("EXPLORACIÓN LIBRE", style = MaterialTheme.typography.labelSmall, color = ObsidianNight, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${route.originCity} → ${route.destinationCity}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CobaltBeacon
                        )
                        Text(
                            text = route.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                    Surface(
                        color = GoldTrophy.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.border(1.dp, GoldTrophy.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = "+$${route.baseRewardCredits}",
                            style = MaterialTheme.typography.titleMedium,
                            color = GoldTrophy,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${route.cargoName} • ${route.cargoTons}t • ${route.environment.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    text = route.biomeSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = CobaltBeacon
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniStatBadge("${route.displayDistanceKm.toInt()} km")
                    MiniStatBadge("Límite ${route.speedLimitKmh} km/h")
                    MiniStatBadge(route.trailerType.label)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianNight.copy(alpha = 0.65f))
                        .border(1.dp, GunmetalBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                ) {
                    val padX = 12f
                    val wSpan = size.width - padX * 2f
                    val midY = size.height * 0.5f
                    val amp = size.height * 0.28f * route.curveIntensity.coerceIn(0.2f, 1.4f)
                    val path = Path().apply {
                        moveTo(padX, midY)
                        cubicTo(
                            padX + wSpan * 0.30f, midY - amp,
                            padX + wSpan * 0.70f, midY + amp,
                            padX + wSpan, midY
                        )
                    }
                    drawPath(path, color = CobaltBeacon.copy(alpha = 0.78f), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                    drawCircle(color = EmeraldCargo, radius = 4f, center = Offset(padX, midY))
                    drawCircle(color = AmberDiesel, radius = 4f, center = Offset(padX + wSpan * 0.45f, midY))
                    drawCircle(color = AmberDiesel, radius = 4f, center = Offset(padX + wSpan * 0.80f, midY))
                    drawCircle(color = GoldTrophy, radius = 4.5f, center = Offset(padX + wSpan, midY))
                }

                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (route.isFreeRoam) EmeraldCargo else AmberDiesel,
                        contentColor = ObsidianNight
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_drive_route_${route.id}")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (route.isFreeRoam) "EXPLORAR MUNDO ABIERTO" else stringResource(id = R.string.drive_now),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GarageFleetTab(
    profile: DriverProfile,
    activeTruck: TruckSpec,
    isBusy: Boolean,
    onSelectOrPurchase: (TruckSpec) -> Unit,
    onSaveWorkshop: (
        paintHex: String,
        livery: LiveryStyle,
        bullbar: Boolean,
        lightbar: Boolean,
        spoiler: Boolean,
        exhaust: Boolean,
        totalCostCredits: Long
    ) -> Unit,
    onRepairInGarage: () -> Unit
) {
    var selectedPaintHex by remember(profile.paintColorHex) { mutableStateOf(profile.paintColorHex) }
    var selectedLivery by remember(profile.liveryStyle) { mutableStateOf(profile.resolvedLiveryStyle()) }
    var bullbar by remember(profile.bullbarInstalled) { mutableStateOf(profile.bullbarInstalled) }
    var lightbar by remember(profile.roofLightbarInstalled) { mutableStateOf(profile.roofLightbarInstalled) }
    var spoiler by remember(profile.aeroSpoilerInstalled) { mutableStateOf(profile.aeroSpoilerInstalled) }
    var exhaust by remember(profile.chromeExhaustInstalled) { mutableStateOf(profile.chromeExhaustInstalled) }

    val paintCost = if (!selectedPaintHex.equals(profile.paintColorHex, ignoreCase = true)) {
        WorkshopCatalog.paints.firstOrNull { it.hex.equals(selectedPaintHex, ignoreCase = true) }?.priceCredits ?: 0L
    } else 0L
    val liveryCost = if (selectedLivery != profile.resolvedLiveryStyle()) selectedLivery.priceCredits else 0L
    val bullCost = if (bullbar && !profile.bullbarInstalled) WorkshopCatalog.BULLBAR_PRICE else 0L
    val ledCost = if (lightbar && !profile.roofLightbarInstalled) WorkshopCatalog.LIGHTBAR_PRICE else 0L
    val aeroCost = if (spoiler && !profile.aeroSpoilerInstalled) WorkshopCatalog.SPOILER_PRICE else 0L
    val exhCost = if (exhaust && !profile.chromeExhaustInstalled) WorkshopCatalog.EXHAUST_PRICE else 0L
    val totalWorkshopCost = paintCost + liveryCost + bullCost + ledCost + aeroCost + exhCost

    val hasPendingWorkshopChanges =
        !selectedPaintHex.equals(profile.paintColorHex, ignoreCase = true) ||
            selectedLivery != profile.resolvedLiveryStyle() ||
            bullbar != profile.bullbarInstalled ||
            lightbar != profile.roofLightbarInstalled ||
            spoiler != profile.aeroSpoilerInstalled ||
            exhaust != profile.chromeExhaustInstalled

    val previewVisualConfig = TruckVisualConfig(
        paintHex = selectedPaintHex,
        liveryStyle = selectedLivery,
        bullbarInstalled = bullbar,
        roofLightbarInstalled = lightbar,
        aeroSpoilerInstalled = spoiler,
        chromeExhaustInstalled = exhaust,
        trailerType = activeTruck.defaultTrailerType
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. BUSSID-style Interactive Workshop & Customization Studio
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SteelSlate),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, AmberDiesel.copy(alpha = 0.65f), MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TALLER DE PERSONALIZACIÓN 2D",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberDiesel
                            )
                            Text(
                                text = activeTruck.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }
                        if (profile.truckDamagePercent > 0L) {
                            val repairCost = profile.truckDamagePercent * WorkshopCatalog.REPAIR_COST_PER_PERCENT
                            Button(
                                onClick = onRepairInGarage,
                                enabled = !isBusy && profile.credits >= repairCost,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CrimsonBrake,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.height(48.dp).testTag("btn_garage_repair")
                            ) {
                                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reparar ${profile.truckDamagePercent}% (-$$repairCost)", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            MiniStatBadge("Chasis 100% Óptimo")
                        }
                    }

                    Truck2DShowroomPreview(
                        truck = activeTruck,
                        visualConfig = previewVisualConfig,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, GunmetalBorder, RoundedCornerShape(14.dp))
                    )

                    Text("Pintura de Cabina", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WorkshopCatalog.paints.forEach { preset ->
                            val isSelected = selectedPaintHex.equals(preset.hex, ignoreCase = true)
                            val swatchColor = parseHexColor(preset.hex, CobaltBeacon)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { selectedPaintHex = preset.hex }
                                    .padding(2.dp)
                                    .testTag("paint_${preset.id}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.5.dp,
                                            color = if (isSelected) AmberDiesel else Color.White.copy(alpha = 0.35f),
                                            shape = CircleShape
                                        )
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = preset.name,
                                            tint = if (preset.id == "pearl") ObsidianNight else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (preset.priceCredits == 0L || profile.paintColorHex.equals(preset.hex, true)) {
                                        preset.name.split(" ").first()
                                    } else "$${preset.priceCredits}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) AmberDiesel else MutedSteel
                                )
                            }
                        }
                    }

                    Text("Vinilo / Diseño Livery", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiveryStyle.entries.forEach { style ->
                            val selected = selectedLivery == style
                            Surface(
                                onClick = { selectedLivery = style },
                                color = if (selected) CobaltBeacon.copy(alpha = 0.24f) else CabMetal,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .border(
                                        1.dp,
                                        if (selected) CobaltBeacon else GunmetalBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .testTag("livery_${style.name}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                    val priceTag = if (style.priceCredits > 0L && style != profile.resolvedLiveryStyle()) {
                                        " ($${style.priceCredits})"
                                    } else ""
                                    Text(
                                        text = "${style.label}$priceTag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) CobaltBeacon else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Text("Accesorios Físicos en Vivo", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccessoryToggleRow(
                            label = "Defensa Frontal Cromada",
                            priceCredits = if (profile.bullbarInstalled) 0L else WorkshopCatalog.BULLBAR_PRICE,
                            checked = bullbar,
                            onCheckedChange = { bullbar = it },
                            testTag = "acc_bullbar"
                        )
                        AccessoryToggleRow(
                            label = "Barra de Luces LED en Techo",
                            priceCredits = if (profile.roofLightbarInstalled) 0L else WorkshopCatalog.LIGHTBAR_PRICE,
                            checked = lightbar,
                            onCheckedChange = { lightbar = it },
                            testTag = "acc_lightbar"
                        )
                        AccessoryToggleRow(
                            label = "Alerón Aerodinámico de Cabina",
                            priceCredits = if (profile.aeroSpoilerInstalled) 0L else WorkshopCatalog.SPOILER_PRICE,
                            checked = spoiler,
                            onCheckedChange = { spoiler = it },
                            testTag = "acc_spoiler"
                        )
                        AccessoryToggleRow(
                            label = "Escapes Dobles Verticales Cromados",
                            priceCredits = if (profile.chromeExhaustInstalled) 0L else WorkshopCatalog.EXHAUST_PRICE,
                            checked = exhaust,
                            onCheckedChange = { exhaust = it },
                            testTag = "acc_exhaust"
                        )
                    }

                    Button(
                        onClick = {
                            onSaveWorkshop(
                                selectedPaintHex,
                                selectedLivery,
                                bullbar,
                                lightbar,
                                spoiler,
                                exhaust,
                                totalWorkshopCost
                            )
                        },
                        enabled = !isBusy && hasPendingWorkshopChanges && profile.credits >= totalWorkshopCost,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldCargo, contentColor = ObsidianNight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_save_workshop")
                    ) {
                        Text(
                            text = when {
                                !hasPendingWorkshopChanges -> "CONFIGURACIÓN APLICADA"
                                profile.credits < totalWorkshopCost -> "CRÉDITOS INSUFICIENTES ($$totalWorkshopCost)"
                                totalWorkshopCost > 0L -> "APLICAR EN TALLER (-$$totalWorkshopCost)"
                                else -> "GUARDAR CAMBIOS DEL TALLER"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "CONCESIONARIO • 8 TRACTOCAMIONES",
                style = MaterialTheme.typography.labelLarge,
                color = MutedSteel
            )
        }

        items(TruckCatalog.trucks, key = { it.id }) { truck ->
            val owned = truck.id in profile.unlockedTruckIds
            val equipped = truck.id == profile.selectedTruckId
            val canAfford = profile.credits >= truck.priceCredits
            val swatch = Color(truck.primaryColorRgb[0], truck.primaryColorRgb[1], truck.primaryColorRgb[2])
            val truckVisual = if (equipped) {
                previewVisualConfig
            } else {
                TruckVisualConfig(
                    paintHex = truck.defaultPaintHex,
                    liveryStyle = LiveryStyle.CLASSIC,
                    trailerType = truck.defaultTrailerType
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SteelSlate),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (equipped) 1.5.dp else 1.dp,
                        color = if (equipped) AmberDiesel else GunmetalBorder,
                        shape = MaterialTheme.shapes.medium
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Truck2DShowroomPreview(
                        truck = truck,
                        visualConfig = truckVisual,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(114.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, GunmetalBorder, RoundedCornerShape(12.dp))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(swatch)
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalShipping,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = truck.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = truck.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel
                                )
                            }
                        }

                        if (equipped) {
                            Surface(
                                color = EmeraldCargo.copy(alpha = 0.2f),
                                shape = CircleShape,
                                modifier = Modifier.border(1.dp, EmeraldCargo, CircleShape)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldCargo, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("ACTIVO", style = MaterialTheme.typography.labelSmall, color = EmeraldCargo)
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStatBadge(truck.roleTag)
                        MiniStatBadge("${truck.horsepower} HP")
                        MiniStatBadge("Máx ${truck.maxSpeedKmh} km/h")
                    }

                    SpecBar("Potencia", (truck.horsepower / 850f).coerceIn(0f, 1f), AmberDiesel)
                    SpecBar("Frenado y Retarder", (truck.brakingPower / 1.35f).coerceIn(0f, 1f), CobaltBeacon)

                    Button(
                        onClick = { onSelectOrPurchase(truck) },
                        enabled = !equipped && !isBusy && (owned || canAfford),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (owned) CobaltBeacon else AmberDiesel,
                            contentColor = ObsidianNight
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_truck_${truck.id}")
                    ) {
                        Text(
                            text = when {
                                equipped -> "CAMIÓN EQUIPADO"
                                owned -> "EQUIPAR CAMIÓN"
                                canAfford -> "COMPRAR POR $${truck.priceCredits}"
                                else -> "REQUIERE $${truck.priceCredits}"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessoryToggleRow(
    label: String,
    priceCredits: Long,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        color = CabMetal,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (checked) AmberDiesel.copy(alpha = 0.6f) else GunmetalBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Text(
                    text = if (priceCredits > 0L) "Instalación: +$$priceCredits" else "Instalado / Incluido",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (priceCredits > 0L) AmberDiesel else EmeraldCargo
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianNight,
                    checkedTrackColor = AmberDiesel
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConvoyMultiplayerTab(
    profile: DriverProfile,
    publicRoomsState: UiState<List<ConvoyRoom>>,
    activeRoom: ConvoyRoom?,
    players: List<ConvoyPlayer>,
    messages: List<ConvoyMessage>,
    isBusy: Boolean,
    onCreateRoom: (roomName: String, routeId: String, privateRoom: Boolean) -> Unit,
    onJoinRoom: (codeOrId: String) -> Unit,
    onLeaveRoom: () -> Unit,
    onUpdateHostRoute: (ConvoyRoom, String) -> Unit,
    onSendChat: (String) -> Unit,
    onLaunchConvoyDrive: (ConvoyRoom) -> Unit
) {
    val context = LocalContext.current
    var roomNameInput by remember { mutableStateOf("Convoy ${profile.driverName.take(14)}") }
    var selectedRouteId by remember { mutableStateOf(RouteCatalog.routes.first().id) }
    var privateRoomSwitch by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (activeRoom == null) {
            // Join by Invite Code
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SteelSlate),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CobaltBeacon.copy(alpha = 0.55f), MaterialTheme.shapes.large)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("MODO CONVOY MULTIJUGADOR", style = MaterialTheme.typography.labelSmall, color = CobaltBeacon)
                        Text("Unirse por Código de Invitación", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = joinCodeInput,
                                onValueChange = { if (it.length <= 16) joinCodeInput = it.uppercase() },
                                label = { Text("Código (ej. AB12CD)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("convoy_code_input")
                            )
                            Button(
                                onClick = { onJoinRoom(joinCodeInput) },
                                enabled = !isBusy && joinCodeInput.trim().length >= 4,
                                colors = ButtonDefaults.buttonColors(containerColor = CobaltBeacon, contentColor = ObsidianNight),
                                modifier = Modifier.height(54.dp).testTag("btn_join_convoy_code")
                            ) {
                                Text("UNIRSE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Create New Convoy Room
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SteelSlate),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GunmetalBorder, MaterialTheme.shapes.large)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Crear Sala de Convoy", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        OutlinedTextField(
                            value = roomNameInput,
                            onValueChange = { if (it.length <= 45) roomNameInput = it },
                            label = { Text("Nombre de la Sala") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("convoy_room_name_input")
                        )

                        Text("Ruta del Convoy", style = MaterialTheme.typography.labelLarge, color = AmberDiesel)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RouteCatalog.routes.forEach { r ->
                                val selected = r.id == selectedRouteId
                                Surface(
                                    onClick = { selectedRouteId = r.id },
                                    color = if (selected) AmberDiesel.copy(alpha = 0.22f) else CabMetal,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .border(1.dp, if (selected) AmberDiesel else GunmetalBorder, RoundedCornerShape(10.dp))
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                        Text(
                                            text = r.originCity + " → " + r.destinationCity,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) AmberDiesel else Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Sala Privada (solo con código)", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text(
                                    text = if (privateRoomSwitch) "Oculta de la lista pública" else "Visible para todos los camioneros",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedSteel
                                )
                            }
                            Switch(
                                checked = privateRoomSwitch,
                                onCheckedChange = { privateRoomSwitch = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = ObsidianNight, checkedTrackColor = AmberDiesel)
                            )
                        }

                        Button(
                            onClick = { onCreateRoom(roomNameInput, selectedRouteId, privateRoomSwitch) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberDiesel, contentColor = ObsidianNight),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("btn_create_convoy")
                        ) {
                            Icon(Icons.Filled.Groups, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("CREAR SALA CONVOY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Text("SALAS PÚBLICAS ACTIVAS", style = MaterialTheme.typography.labelLarge, color = MutedSteel)
            }

            when (publicRoomsState) {
                is UiState.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AmberDiesel)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(publicRoomsState.message, style = MaterialTheme.typography.bodyMedium, color = CrimsonBrake)
                    }
                }
                is UiState.Success -> {
                    if (publicRoomsState.data.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SteelSlate),
                                modifier = Modifier.fillMaxWidth().border(1.dp, GunmetalBorder, MaterialTheme.shapes.medium)
                            ) {
                                Text(
                                    text = "No hay salas públicas abiertas ahora. ¡Crea una arriba e invita a tus amigos!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(publicRoomsState.data, key = { it.id }) { room ->
                            val rSpec = RouteCatalog.byId(room.routeId)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SteelSlate),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, GunmetalBorder, MaterialTheme.shapes.medium)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Filled.Public, contentDescription = null, tint = EmeraldCargo, modifier = Modifier.size(16.dp))
                                            Text(room.roomName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                        }
                                        Text(
                                            text = "Anfitrión: ${room.hostName} • ${rSpec.originCity} → ${rSpec.destinationCity}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MutedSteel
                                        )
                                        Text(
                                            text = "${room.memberUids.size}/8 camioneros • Código ${room.roomCode}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CobaltBeacon
                                        )
                                    }
                                    Button(
                                        onClick = { onJoinRoom(room.roomCode) },
                                        enabled = !isBusy,
                                        colors = ButtonDefaults.buttonColors(containerColor = CobaltBeacon, contentColor = ObsidianNight),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("ENTRAR", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Active Convoy Lobby
            val currentRoute = RouteCatalog.byId(activeRoom.routeId)
            val isHost = activeRoom.hostId == profile.userId

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SteelSlate),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, EmeraldCargo, MaterialTheme.shapes.large)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = if (activeRoom.privateRoom) Icons.Filled.Lock else Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = AmberDiesel,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(activeRoom.roomName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                                }
                                Text(
                                    text = "Código para amigos: ${activeRoom.roomCode}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = GoldTrophy
                                )
                            }
                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "¡Únete a mi Convoy en Truck Simulator Extreme! Sala: ${activeRoom.roomName} • Código: ${activeRoom.roomCode}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Invitar amigos al Convoy"))
                                },
                                modifier = Modifier.size(48.dp).testTag("btn_share_convoy")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Compartir código de convoy", tint = CobaltBeacon)
                            }
                        }

                        Text(
                            text = "Ruta: ${currentRoute.title} (${currentRoute.originCity} → ${currentRoute.destinationCity})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CobaltBeacon
                        )

                        if (isHost) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RouteCatalog.routes.forEach { r ->
                                    val selected = r.id == activeRoom.routeId
                                    Surface(
                                        onClick = { onUpdateHostRoute(activeRoom, r.id) },
                                        color = if (selected) AmberDiesel.copy(alpha = 0.22f) else CabMetal,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .border(1.dp, if (selected) AmberDiesel else GunmetalBorder, RoundedCornerShape(8.dp))
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                            Text(r.originCity, style = MaterialTheme.typography.labelSmall, color = if (selected) AmberDiesel else Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { onLaunchConvoyDrive(activeRoom) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldCargo, contentColor = ObsidianNight),
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_launch_convoy_drive")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("SALIR AL CONVOY EN RUTA", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onLeaveRoom,
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_leave_convoy")
                        ) {
                            Text("SALIR DE LA SALA", style = MaterialTheme.typography.labelLarge, color = CrimsonBrake)
                        }
                    }
                }
            }

            // Live Player Roster
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SteelSlate),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GunmetalBorder, MaterialTheme.shapes.medium)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CAMIONEROS CONECTADOS (${players.size}/8)", style = MaterialTheme.typography.labelLarge, color = AmberDiesel)
                        players.forEach { player ->
                            val tSpec = TruckCatalog.byId(player.truckId)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(player.paintColorHex, CobaltBeacon))
                                    )
                                    Column {
                                        Text(
                                            text = if (player.userId == profile.userId) "${player.driverName} (Tú)" else player.driverName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                        Text(tSpec.name, style = MaterialTheme.typography.labelSmall, color = MutedSteel)
                                    }
                                }
                                Text(
                                    text = "${player.speedKmh} km/h • ${(player.roadS / 100.0).let { "%.1f".format(it) }} km",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldCargo
                                )
                            }
                        }
                    }
                }
            }

            // Live CB Radio Chat
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SteelSlate),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GunmetalBorder, MaterialTheme.shapes.medium)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("RADIO CB DEL CONVOY", style = MaterialTheme.typography.labelLarge, color = CobaltBeacon)
                        if (messages.isEmpty()) {
                            Text("Sin transmisiones aún. Saluda por la radio CB.", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                        } else {
                            messages.takeLast(8).forEach { msg ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${msg.senderName}:",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = AmberDiesel
                                    )
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { if (it.length <= 160) chatInput = it },
                                label = { Text("Mensaje CB…") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("convoy_chat_input")
                            )
                            Button(
                                onClick = {
                                    if (chatInput.isNotBlank()) {
                                        onSendChat(chatInput)
                                        chatInput = ""
                                    }
                                },
                                enabled = chatInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberDiesel, contentColor = ObsidianNight),
                                modifier = Modifier.size(54.dp).testTag("btn_send_convoy_chat")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar mensaje CB")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CareerAndControlsTab(
    profile: DriverProfile,
    deliveriesState: UiState<List<DeliveryRecord>>,
    isBusy: Boolean,
    onSaveSettings: (SteeringMode, Double, CameraMode, String) -> Unit
) {
    var steeringMode by remember(profile.steeringMode) { mutableStateOf(profile.resolvedSteeringMode()) }
    var sensitivity by remember(profile.steeringSensitivity) { mutableFloatStateOf(profile.steeringSensitivity.toFloat()) }
    var cameraMode by remember(profile.cameraMode) { mutableStateOf(profile.resolvedCameraMode()) }
    var driverName by remember(profile.driverName) { mutableStateOf(profile.driverName) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SteelSlate),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GunmetalBorder, MaterialTheme.shapes.medium)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Build, contentDescription = null, tint = AmberDiesel)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "CALIBRACIÓN DE CONTROLES PRECISOS",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    OutlinedTextField(
                        value = driverName,
                        onValueChange = { if (it.length <= 40) driverName = it },
                        label = { Text("Nombre en Licencia") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Sistema de Dirección", style = MaterialTheme.typography.labelLarge, color = AmberDiesel)
                    SteeringMode.entries.forEach { mode ->
                        val selected = mode == steeringMode
                        Surface(
                            onClick = { steeringMode = mode },
                            color = if (selected) AmberDiesel.copy(alpha = 0.16f) else CabMetal,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (selected) AmberDiesel else GunmetalBorder,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) AmberDiesel else Color.White
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel
                                )
                            }
                        }
                    }

                    Text(
                        text = "Sensibilidad de Dirección: ${"%.2f".format(sensitivity)}x",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Slider(
                        value = sensitivity,
                        onValueChange = { sensitivity = it },
                        valueRange = 0.4f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = AmberDiesel, activeTrackColor = AmberDiesel)
                    )

                    Text("Perspectiva 2D Inicial (4 Cámaras)", style = MaterialTheme.typography.labelLarge, color = CobaltBeacon)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CameraMode.entries.forEach { cam ->
                            val selected = cam == cameraMode
                            OutlinedButton(
                                onClick = { cameraMode = cam },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) CobaltBeacon.copy(alpha = 0.22f) else CabMetal,
                                    contentColor = if (selected) CobaltBeacon else Color.White
                                ),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(cam.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Button(
                        onClick = { onSaveSettings(steeringMode, sensitivity.toDouble(), cameraMode, driverName) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberDiesel, contentColor = ObsidianNight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_save_controls")
                    ) {
                        Text("GUARDAR CALIBRACIÓN", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        item {
            Text(
                text = "BITÁCORA DE ENTREGAS RECIENTES",
                style = MaterialTheme.typography.labelLarge,
                color = MutedSteel
            )
        }

        when (deliveriesState) {
            is UiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AmberDiesel)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(
                        text = deliveriesState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrimsonBrake
                    )
                }
            }
            is UiState.Success -> {
                if (deliveriesState.data.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SteelSlate),
                            modifier = Modifier.fillMaxWidth().border(1.dp, GunmetalBorder, MaterialTheme.shapes.medium)
                        ) {
                            Text(
                                text = "Aún no has completado entregas. Inicia tu primera ruta desde Despacho.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSteel,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(deliveriesState.data, key = { it.id }) { rec ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SteelSlate),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GunmetalBorder, MaterialTheme.shapes.small)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rec.routeName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${rec.cargoName} • ${rec.durationSeconds}s • Daño ${rec.damagePercent}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedSteel
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "+$${rec.earnedCredits}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = GoldTrophy,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (rec.perfectDelivery) {
                                        Text(
                                            text = "SIN DAÑOS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldCargo
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatBadge(text: String) {
    Surface(
        color = CabMetal,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, GunmetalBorder, RoundedCornerShape(8.dp))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFE6EDF3),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SpecBar(label: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedSteel)
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = CabMetal,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
        )
    }
}

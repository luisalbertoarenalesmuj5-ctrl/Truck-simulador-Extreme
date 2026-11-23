package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EmojiEvents
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
import com.example.model.DeliveryRecord
import com.example.model.DriverProfile
import com.example.model.EnvironmentTheme
import com.example.model.RouteCatalog
import com.example.model.RouteSpec
import com.example.model.SteeringMode
import com.example.model.TruckCabStyle
import com.example.model.TruckCatalog
import com.example.model.TruckSpec
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
import com.example.viewmodel.UiState

@Composable
fun HubScreen(
    viewModel: CareerViewModel,
    onStartDrive: (TruckSpec, RouteSpec, SteeringMode, Float, CameraMode) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val deliveriesState by viewModel.deliveriesState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianNight)) {
        val useRail = maxWidth >= 600.dp

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
                        val tabs = listOf(
                            Triple(stringResource(id = R.string.tab_routes), Icons.Filled.Route, Icons.Outlined.Route),
                            Triple(stringResource(id = R.string.tab_garage), Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
                            Triple(stringResource(id = R.string.tab_career), Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents)
                        )
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
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
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
                        val tabs = listOf(
                            Triple(stringResource(id = R.string.tab_routes), Icons.Filled.Route, Icons.Outlined.Route),
                            Triple(stringResource(id = R.string.tab_garage), Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
                            Triple(stringResource(id = R.string.tab_career), Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents)
                        )
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

                            DriverTopBar(
                                profile = profile,
                                activeTruck = activeTruck,
                                onSignOut = { signOutFromApp(credentialManager, scope) }
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 760.dp)
                            ) {
                                when (selectedTab) {
                                    0 -> RoutesDispatchTab(
                                        profile = profile,
                                        activeTruck = activeTruck,
                                        onLaunchRoute = { route ->
                                            onStartDrive(
                                                activeTruck,
                                                route,
                                                profile.resolvedSteeringMode(),
                                                profile.steeringSensitivity.toFloat(),
                                                profile.resolvedCameraMode()
                                            )
                                        }
                                    )
                                    1 -> GarageFleetTab(
                                        profile = profile,
                                        isBusy = isBusy,
                                        onSelectOrPurchase = { truck ->
                                            viewModel.selectOrPurchaseTruck(truck)
                                        }
                                    )
                                    2 -> CareerAndControlsTab(
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
    onLaunchRoute: (RouteSpec) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Active Rig Hero Banner
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = SteelSlate),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GunmetalBorder, MaterialTheme.shapes.large)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(168.dp)) {
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
                                    listOf(ObsidianNight.copy(alpha = 0.92f), ObsidianNight.copy(alpha = 0.35f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "CAMIÓN ACTIVO • ${activeTruck.horsepower} HP",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberDiesel
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = activeTruck.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Control: ${profile.resolvedSteeringMode().label} • Cámara: ${profile.resolvedCameraMode().label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSteel
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MiniStatBadge("${profile.deliveriesCompleted} Entregas")
                            MiniStatBadge("${profile.totalDistanceKm.toInt()} km")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "CONTRATOS DE CARGA DISPONIBLES",
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
        EnvironmentTheme.COASTAL_SUNSET -> R.drawable.img_truck_hero
        EnvironmentTheme.ALPINE_DAY, EnvironmentTheme.STORM_SUMMIT -> R.drawable.img_real_highway_alpine
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = SteelSlate),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GunmetalBorder, MaterialTheme.shapes.medium)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(118.dp)) {
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
                                    SteelSlate.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
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
                    text = "${route.cargoName} • ${route.cargoTons} toneladas • ${route.environment.label} (${route.environment.weatherLabel})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSteel
                )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniStatBadge("${route.displayDistanceKm.toInt()} km")
                MiniStatBadge("Límite ${route.speedLimitKmh} km/h")
                MiniStatBadge("Dificultad: ${route.difficultyLabel}")
            }

            // 2D Highway Route Profile Preview Bar
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
                    colors = ButtonDefaults.buttonColors(containerColor = AmberDiesel, contentColor = ObsidianNight),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_drive_route_${route.id}")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(id = R.string.drive_now), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun GarageFleetTab(
    profile: DriverProfile,
    isBusy: Boolean,
    onSelectOrPurchase: (TruckSpec) -> Unit
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
                Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_real_truck_fleet),
                        contentDescription = "Exhibición real de flota pesada",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(ObsidianNight.copy(alpha = 0.20f), ObsidianNight.copy(alpha = 0.88f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "GARAJE DE TRACTOCAMIONES",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberDiesel
                        )
                        Text(
                            text = "Flota Real de Alto Tonelaje",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "UNIDADES DISPONIBLES",
                style = MaterialTheme.typography.labelLarge,
                color = MutedSteel
            )
        }

        items(TruckCatalog.trucks, key = { it.id }) { truck ->
            val owned = truck.id in profile.unlockedTruckIds
            val equipped = truck.id == profile.selectedTruckId
            val canAfford = profile.credits >= truck.priceCredits
            val swatch = Color(truck.primaryColorRgb[0], truck.primaryColorRgb[1], truck.primaryColorRgb[2])

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
                        MiniStatBadge(if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) "Cabina Europea" else "Capó Americano")
                        MiniStatBadge("${truck.horsepower} HP • ${truck.torqueNm} Nm")
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

                    Text("Perspectiva 2D Inicial", style = MaterialTheme.typography.labelLarge, color = CobaltBeacon)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CameraMode.entries.forEach { cam ->
                            val selected = cam == cameraMode
                            OutlinedButton(
                                onClick = { cameraMode = cam },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) CobaltBeacon.copy(alpha = 0.22f) else CabMetal,
                                    contentColor = if (selected) CobaltBeacon else Color.White
                                ),
                                modifier = Modifier.weight(1f).height(48.dp)
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
                val list = deliveriesState.data
                if (list.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SteelSlate),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GunmetalBorder, MaterialTheme.shapes.medium)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Explore,
                                    contentDescription = null,
                                    tint = AmberDiesel,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Aún no hay entregas registradas",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Elige una ruta en Despacho para completar tu primer viaje y ganar créditos.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel
                                )
                            }
                        }
                    }
                } else {
                    items(list, key = { it.id }) { rec ->
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

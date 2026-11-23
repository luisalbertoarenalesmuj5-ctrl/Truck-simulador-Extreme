package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CameraMode
import com.example.model.CareerCatalog
import com.example.model.CareerCertification
import com.example.model.CareerMilestone
import com.example.model.DeliveryRecord
import com.example.model.DriverProfile
import com.example.model.SteeringMode
import com.example.model.TruckCatalog
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
import com.example.viewmodel.UiState

@Composable
fun ProfileManagementScreen(
    profile: DriverProfile,
    deliveriesState: UiState<List<DeliveryRecord>>,
    isBusy: Boolean,
    snackbarHostState: SnackbarHostState,
    onSaveProfile: (SteeringMode, Double, CameraMode, String) -> Unit,
    onClaimMilestone: (CareerMilestone) -> Unit,
    onCompleteCertification: (CareerCertification) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = ObsidianNight,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = Color(0xF50E1520),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .border(1.dp, GunmetalBorder.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_profile_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.profile_back_cd),
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.profile_screen_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = EmeraldCargo,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Sincronizado en Cloud Firestore",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldCargo
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_profile_sign_out")
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            ProfileManagementContent(
                profile = profile,
                deliveriesState = deliveriesState,
                isBusy = isBusy,
                onSaveProfile = onSaveProfile,
                onClaimMilestone = onClaimMilestone,
                onCompleteCertification = onCompleteCertification,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 780.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileManagementContent(
    profile: DriverProfile,
    deliveriesState: UiState<List<DeliveryRecord>>,
    isBusy: Boolean,
    onSaveProfile: (SteeringMode, Double, CameraMode, String) -> Unit,
    onClaimMilestone: (CareerMilestone) -> Unit,
    onCompleteCertification: (CareerCertification) -> Unit,
    modifier: Modifier = Modifier
) {
    var steeringMode by remember(profile.steeringMode) { mutableStateOf(profile.resolvedSteeringMode()) }
    var sensitivity by remember(profile.steeringSensitivity) { mutableFloatStateOf(profile.steeringSensitivity.toFloat()) }
    var cameraMode by remember(profile.cameraMode) { mutableStateOf(profile.resolvedCameraMode()) }
    var driverName by remember(profile.driverName) { mutableStateOf(profile.driverName) }

    val activeTruck = remember(profile.selectedTruckId) { TruckCatalog.byId(profile.selectedTruckId) }
    val level = profile.effectiveLevel()
    val xpIntoLevel = profile.xpIntoCurrentLevel()
    val xpForNext = profile.xpForNextLevel()
    val xpRemaining = profile.xpRemainingToNextLevel()
    val progressFraction = profile.levelProgressFraction()
    val nameTrimmed = driverName.trim()
    val isNameValid = nameTrimmed.length in 2..40

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
    ) {
        // 1. Driver License Hero Card: Level, XP, Credits & Career Stats
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SteelSlate),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.4.dp, AmberDiesel.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .testTag("profile_hero_card")
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1A2638), SteelSlate)
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.6.dp, AmberDiesel, RoundedCornerShape(16.dp))
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_truck_icon),
                                    contentDescription = "Avatar de licencia del conductor",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = profile.driverName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${profile.driverRankTitle()} • ${activeTruck.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AmberDiesel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            color = AmberDiesel.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.border(1.3.dp, AmberDiesel, RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "NIVEL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberDiesel,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$level",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // Primary 3 Persisted Firestore Career Values: Nivel, Experiencia, Dinero
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricTile(
                            label = "NIVEL ACTUAL",
                            value = "Nv. $level",
                            accentColor = AmberDiesel,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("metric_profile_level")
                        )
                        ProfileMetricTile(
                            label = "EXPERIENCIA",
                            value = "${profile.xp} XP",
                            accentColor = EmeraldCargo,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("metric_profile_xp")
                        )
                        ProfileMetricTile(
                            label = "DINERO",
                            value = "$${profile.credits}",
                            accentColor = GoldTrophy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("metric_profile_credits")
                        )
                    }

                    // XP Progress bar to next level
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Progreso a Nivel ${level + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            Text(
                                text = "$xpIntoLevel / $xpForNext XP (faltan $xpRemaining XP)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedSteel
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(CabMetal)
                                .border(1.dp, GunmetalBorder, CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressFraction.coerceAtLeast(0.03f))
                                    .background(Brush.horizontalGradient(listOf(AmberDiesel, EmeraldCargo)))
                            )
                        }
                    }

                    // Secondary Career Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileSecondaryStat(
                            label = "Distancia",
                            value = "${"%.1f".format(profile.totalDistanceKm)} km",
                            modifier = Modifier.weight(1f)
                        )
                        ProfileSecondaryStat(
                            label = "Entregas",
                            value = "${profile.deliveriesCompleted}",
                            modifier = Modifier.weight(1f)
                        )
                        ProfileSecondaryStat(
                            label = "Contratos",
                            value = "${profile.contractsCompleted}",
                            modifier = Modifier.weight(1f)
                        )
                        ProfileSecondaryStat(
                            label = "Flota",
                            value = "${profile.unlockedTruckIds.size}/${TruckCatalog.trucks.size}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Career Milestones (Earn XP & Money -> Level Up in Firestore)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SteelSlate),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GunmetalBorder, MaterialTheme.shapes.medium)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = GoldTrophy)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "HITOS DE CARRERA Y RECOMPENSAS",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                    Text(
                        text = "Reclama bonos de experiencia y dinero por tus logros. Tu nivel, XP y saldo se guardan en Firestore.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )

                    CareerCatalog.milestones.forEach { milestone ->
                        val claimed = milestone.id in profile.claimedMilestoneIds
                        val unlocked = milestone.isUnlocked(profile)
                        Surface(
                            color = CabMetal,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    when {
                                        claimed -> EmeraldCargo.copy(alpha = 0.5f)
                                        unlocked -> GoldTrophy.copy(alpha = 0.7f)
                                        else -> GunmetalBorder
                                    },
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                claimed -> Icons.Filled.CheckCircle
                                                unlocked -> Icons.Filled.Star
                                                else -> Icons.Filled.Lock
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                claimed -> EmeraldCargo
                                                unlocked -> GoldTrophy
                                                else -> MutedSteel
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = milestone.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = milestone.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedSteel
                                    )
                                    Text(
                                        text = "+${milestone.rewardXp} XP • +$${milestone.rewardCredits} • Req: ${milestone.requirementLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (unlocked) AmberDiesel else MutedSteel
                                    )
                                }

                                Spacer(Modifier.width(10.dp))

                                when {
                                    claimed -> {
                                        Surface(
                                            color = EmeraldCargo.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "COBRADO",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldCargo,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                    unlocked -> {
                                        Button(
                                            onClick = { onClaimMilestone(milestone) },
                                            enabled = !isBusy,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = GoldTrophy,
                                                contentColor = ObsidianNight
                                            ),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("btn_claim_milestone_${milestone.id}")
                                        ) {
                                            Text("RECLAMAR", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                    else -> {
                                        OutlinedButton(
                                            onClick = {},
                                            enabled = false,
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("BLOQUEADO", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Professional Certifications (Spend money to train & level up in Firestore)
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
                        Icon(Icons.Filled.School, contentDescription = null, tint = CobaltBeacon)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "ACADEMIA Y ASCENSO DE LICENCIA",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Invierte créditos de tus entregas para obtener certificaciones oficiales, sumar XP y subir de nivel.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )

                    CareerCatalog.certifications.forEach { cert ->
                        val completed = cert.id in profile.claimedMilestoneIds
                        val canAfford = profile.credits >= cert.costCredits
                        Surface(
                            color = CabMetal,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (completed) EmeraldCargo.copy(alpha = 0.55f) else GunmetalBorder,
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = cert.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = cert.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedSteel
                                    )
                                    Text(
                                        text = "Costo: $${cert.costCredits} • Otorga +${cert.grantXp} XP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (completed) EmeraldCargo else CobaltBeacon
                                    )
                                }

                                Spacer(Modifier.width(10.dp))

                                if (completed) {
                                    Surface(
                                        color = EmeraldCargo.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "APROBADA",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldCargo,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { onCompleteCertification(cert) },
                                        enabled = !isBusy && canAfford,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CobaltBeacon,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("btn_cert_${cert.id}")
                                    ) {
                                        Text(
                                            text = if (canAfford) "CURSAR" else "SIN SALDO",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Driver Profile Editor & Control Calibration (Persisted to Firestore)
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
                        Icon(Icons.Filled.Person, contentDescription = null, tint = AmberDiesel)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "DATOS DEL PERFIL Y CONTROLES",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    OutlinedTextField(
                        value = driverName,
                        onValueChange = { if (it.length <= 40) driverName = it },
                        label = { Text("Nombre en Licencia (2-40 caracteres)") },
                        singleLine = true,
                        isError = !isNameValid,
                        supportingText = {
                            if (!isNameValid) {
                                Text("Ingresa al menos 2 caracteres válidos.")
                            } else {
                                Text("${nameTrimmed.length}/40 caracteres")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_profile_driver_name")
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Build, contentDescription = null, tint = AmberDiesel, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sistema de Dirección", style = MaterialTheme.typography.labelLarge, color = AmberDiesel)
                    }

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
                        onClick = {
                            onSaveProfile(steeringMode, sensitivity.toDouble(), cameraMode, nameTrimmed)
                        },
                        enabled = !isBusy && isNameValid,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberDiesel, contentColor = ObsidianNight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_save_controls")
                    ) {
                        Text("GUARDAR PERFIL EN FIRESTORE", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // 5. Recent Freight Deliveries Log
        item {
            Text(
                text = "BITÁCORA DE ENTREGAS EN FIRESTORE",
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GunmetalBorder, MaterialTheme.shapes.medium)
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalShipping,
                                    contentDescription = null,
                                    tint = AmberDiesel,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Aún no has completado entregas. Inicia una ruta desde Despacho para ganar XP, subir de nivel y acumular dinero.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel
                                )
                            }
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
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
private fun ProfileMetricTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CabMetal,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MutedSteel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileSecondaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF101826),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.border(1.dp, GunmetalBorder, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MutedSteel,
                maxLines = 1
            )
        }
    }
}

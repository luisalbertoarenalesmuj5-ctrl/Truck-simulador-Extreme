package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.CareerRepository
import com.example.data.ConvoyRepository
import com.example.data.authStateFlow
import com.example.model.CameraMode
import com.example.model.DeliveryContract
import com.example.model.RouteSpec
import com.example.model.SteeringMode
import com.example.model.TruckSpec
import com.example.model.TruckVisualConfig
import com.example.ui.AuthScreen
import com.example.ui.DriveScreen
import com.example.ui.HubScreen
import com.example.ui.theme.TruckSimTheme
import com.example.viewmodel.CareerViewModel
import com.example.viewmodel.ConvoyViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private data class ActiveDriveSession(
    val truck: TruckSpec,
    val route: RouteSpec,
    val steeringMode: SteeringMode,
    val sensitivity: Float,
    val cameraMode: CameraMode,
    val visualConfig: TruckVisualConfig,
    val initialDamagePercent: Int,
    val activeConvoyRoomId: String?,
    val activeContract: DeliveryContract? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TruckSimTheme {
                AppRootNavigation()
            }
        }
    }
}

@Composable
fun AppRootNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { })
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: CareerViewModel = viewModel(
                key = "career_$uid",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        CareerViewModel(
                            repository = CareerRepository(db),
                            currentUserId = uid,
                            initialDisplayName = user.displayName
                        )
                    }
                }
            )

            val convoyViewModel: ConvoyViewModel = viewModel(
                key = "convoy_$uid",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        ConvoyViewModel(
                            repository = ConvoyRepository(db),
                            currentUserId = uid
                        )
                    }
                }
            )

            val profileState by viewModel.profileState.collectAsStateWithLifecycle()
            val activeProfile = (profileState as? UiState.Success)?.data
            var activeSession by remember { mutableStateOf<ActiveDriveSession?>(null) }

            val session = activeSession
            if (session == null) {
                HubScreen(
                    viewModel = viewModel,
                    convoyViewModel = convoyViewModel,
                    onStartDrive = { truck, route, steerMode, sens, camMode, visualConfig, dmg, convoyRoomId, contract ->
                        activeSession = ActiveDriveSession(
                            truck = truck,
                            route = route,
                            steeringMode = steerMode,
                            sensitivity = sens,
                            cameraMode = camMode,
                            visualConfig = visualConfig,
                            initialDamagePercent = dmg,
                            activeConvoyRoomId = convoyRoomId,
                            activeContract = contract
                        )
                    }
                )
            } else {
                DriveScreen(
                    truck = session.truck,
                    route = session.route,
                    visualConfig = session.visualConfig,
                    initialDamagePercent = session.initialDamagePercent,
                    initialSteeringMode = session.steeringMode,
                    initialSensitivity = session.sensitivity,
                    initialCameraMode = session.cameraMode,
                    profile = activeProfile,
                    convoyViewModel = convoyViewModel,
                    activeConvoyRoomId = session.activeConvoyRoomId,
                    onRoadCreditAdjustment = { deltaCredits, dmg ->
                        viewModel.adjustCreditsForRoadEvent(deltaCredits, dmg)
                    },
                    onSaveDelivery = { damage, duration ->
                        viewModel.completeDelivery(
                            route = session.route,
                            truckId = session.truck.id,
                            damagePercent = damage,
                            durationSeconds = duration,
                            activeContract = session.activeContract
                        )
                    },
                    onExitToHub = { activeSession = null }
                )
            }
        }
    }
}

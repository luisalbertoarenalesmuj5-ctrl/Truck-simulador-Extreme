package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CareerRepository
import com.example.model.CameraMode
import com.example.model.DeliveryRecord
import com.example.model.DriverProfile
import com.example.model.LiveryStyle
import com.example.model.RouteSpec
import com.example.model.SteeringMode
import com.example.model.TruckSpec
import com.example.model.WorkshopCatalog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class CareerViewModel(
    private val repository: CareerRepository,
    private val currentUserId: String,
    initialDisplayName: String?
) : ViewModel() {

    val profileState: StateFlow<UiState<DriverProfile>> =
        repository.observeDriverProfile(currentUserId)
            .map<DriverProfile?, UiState<DriverProfile>> { profile ->
                if (profile != null) {
                    UiState.Success(profile)
                } else {
                    UiState.Loading
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing driver profile", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "No se pudo cargar el perfil"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val deliveriesState: StateFlow<UiState<List<DeliveryRecord>>> =
        repository.observeDeliveries(currentUserId)
            .map<List<DeliveryRecord>, UiState<List<DeliveryRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing deliveries", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "No se pudo cargar el historial"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            val result = repository.ensureDriverProfileInitialized(initialDisplayName)
            result.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Error al inicializar licencia"
            }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun selectOrPurchaseTruck(truck: TruckSpec) {
        val current = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.selectOrPurchaseTruck(current, truck)
            _isBusy.value = false
            result.onSuccess {
                val unlocked = truck.id in current.unlockedTruckIds
                _actionMessage.value = if (unlocked) {
                    "Camión equipado: ${truck.name}"
                } else {
                    "¡${truck.name} adquirido y equipado!"
                }
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "No se pudo completar la operación"
            }
        }
    }

    fun saveWorkshopCustomization(
        paintColorHex: String,
        liveryStyle: LiveryStyle,
        bullbar: Boolean,
        roofLightbar: Boolean,
        aeroSpoiler: Boolean,
        chromeExhaust: Boolean,
        totalCostCredits: Long
    ) {
        val current = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveWorkshopCustomization(
                currentProfile = current,
                paintColorHex = paintColorHex,
                liveryStyle = liveryStyle,
                bullbarInstalled = bullbar,
                roofLightbarInstalled = roofLightbar,
                aeroSpoilerInstalled = aeroSpoiler,
                chromeExhaustInstalled = chromeExhaust,
                totalCostCredits = totalCostCredits
            )
            _isBusy.value = false
            result.onSuccess {
                _actionMessage.value = if (totalCostCredits > 0L) {
                    "Personalización aplicada (-$$totalCostCredits créditos)"
                } else {
                    "Configuración de taller actualizada"
                }
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "No se pudo aplicar en el taller"
            }
        }
    }

    fun repairTruckInGarage() {
        val current = (profileState.value as? UiState.Success)?.data ?: return
        val dmg = current.truckDamagePercent.coerceIn(0L, 100L)
        if (dmg <= 0L) {
            _actionMessage.value = "El camión está al 100% sin daños."
            return
        }
        val cost = dmg * WorkshopCatalog.REPAIR_COST_PER_PERCENT
        if (current.credits < cost) {
            _actionMessage.value = "Créditos insuficientes para reparar (requiere $$cost)."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.repairTruckOrAdjustCredits(
                currentProfile = current,
                creditsDelta = -cost,
                newDamagePercent = 0L
            )
            _isBusy.value = false
            result.onSuccess {
                _actionMessage.value = "Camión reparado al 0% de daño (-$$cost)"
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "No se pudo reparar el camión"
            }
        }
    }

    fun adjustCreditsForRoadEvent(deltaCredits: Long, damagePercent: Int? = null) {
        val current = (profileState.value as? UiState.Success)?.data ?: return
        val nextDamage = damagePercent?.toLong()?.coerceIn(0L, 100L) ?: current.truckDamagePercent
        viewModelScope.launch {
            repository.repairTruckOrAdjustCredits(
                currentProfile = current,
                creditsDelta = deltaCredits,
                newDamagePercent = nextDamage
            )
        }
    }

    fun updateControlSettings(
        steeringMode: SteeringMode,
        sensitivity: Double,
        cameraMode: CameraMode,
        driverName: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateControlSettings(steeringMode, sensitivity, cameraMode, driverName)
            _isBusy.value = false
            result.onSuccess {
                _actionMessage.value = "Configuración guardada en la nube"
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Error al guardar configuración"
            }
        }
    }

    suspend fun completeDelivery(
        route: RouteSpec,
        truckId: String,
        damagePercent: Int,
        durationSeconds: Long
    ): Result<Long> {
        val current = (profileState.value as? UiState.Success)?.data
            ?: return Result.failure(IllegalStateException("Perfil no disponible"))
        return repository.recordCompletedDelivery(current, route, truckId, damagePercent, durationSeconds)
    }

    private companion object {
        const val TAG = "CareerVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}

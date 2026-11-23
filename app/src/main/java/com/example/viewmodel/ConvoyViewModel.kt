package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ConvoyRepository
import com.example.model.ConvoyMessage
import com.example.model.ConvoyPlayer
import com.example.model.ConvoyRoom
import com.example.model.DriverProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ConvoyViewModel(
    private val repository: ConvoyRepository,
    val currentUserId: String
) : ViewModel() {

    val publicRoomsState: StateFlow<UiState<List<ConvoyRoom>>> =
        repository.observePublicRooms()
            .map<List<ConvoyRoom>, UiState<List<ConvoyRoom>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing public convoy rooms", error)
                emit(UiState.Error(error.localizedMessage ?: "No se pudieron cargar las salas públicas"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _activeRoomId = MutableStateFlow<String?>(null)
    val activeRoomId: StateFlow<String?> = _activeRoomId.asStateFlow()

    val activeRoom: StateFlow<ConvoyRoom?> = _activeRoomId
        .flatMapLatest { roomId ->
            if (roomId == null) flowOf(null)
            else repository.observeRoom(roomId).catch { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "Error observing room $roomId", err)
                emit(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val players: StateFlow<List<ConvoyPlayer>> = _activeRoomId
        .flatMapLatest { roomId ->
            if (roomId == null) flowOf(emptyList())
            else repository.observePlayers(roomId).catch { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "Error observing players for $roomId", err)
                emit(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val messages: StateFlow<List<ConvoyMessage>> = _activeRoomId
        .flatMapLatest { roomId ->
            if (roomId == null) flowOf(emptyList())
            else repository.observeMessages(roomId).catch { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "Error observing messages for $roomId", err)
                emit(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun createRoom(
        roomName: String,
        routeId: String,
        privateRoom: Boolean,
        profile: DriverProfile
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createConvoyRoom(roomName, routeId, privateRoom, profile)
            _isBusy.value = false
            res.onSuccess { roomId ->
                _activeRoomId.value = roomId
                _actionMessage.value = "Sala de Convoy creada: ${roomId.removePrefix("CONVOY_")}"
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "No se pudo crear la sala de convoy"
            }
        }
    }

    fun joinRoom(codeOrId: String, profile: DriverProfile) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.joinConvoyRoom(codeOrId, profile)
            _isBusy.value = false
            res.onSuccess { roomId ->
                _activeRoomId.value = roomId
                _actionMessage.value = "Conectado al Convoy ${roomId.removePrefix("CONVOY_")}"
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "No se pudo unir a la sala"
            }
        }
    }

    fun leaveRoom() {
        val roomId = _activeRoomId.value ?: return
        val currentRoom = activeRoom.value
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.leaveConvoyRoom(roomId, currentRoom)
            _isBusy.value = false
            _activeRoomId.value = null
            res.onSuccess {
                _actionMessage.value = "Has salido del convoy"
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Saliste del convoy"
            }
        }
    }

    fun updateHostRouteOrStatus(routeId: String, status: String) {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            val res = repository.updateRoomHostConfig(roomId, routeId, status)
            res.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "No se pudo actualizar la ruta del convoy"
            }
        }
    }

    fun sendChatMessage(senderName: String, text: String) {
        val roomId = _activeRoomId.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            val res = repository.sendMessage(roomId, senderName, text)
            res.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "No se pudo enviar el mensaje CB"
            }
        }
    }

    fun syncDriveTelemetry(
        profile: DriverProfile,
        roadS: Double,
        laneOffset: Double,
        speedKmh: Int,
        headlightsOn: Boolean,
        hornActive: Boolean,
        online: Boolean = true
    ) {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.syncPlayerTelemetry(
                roomId = roomId,
                profile = profile,
                roadS = roadS,
                laneOffset = laneOffset,
                speedKmh = speedKmh,
                headlightsOn = headlightsOn,
                hornActive = hornActive,
                online = online
            )
        }
    }

    private companion object {
        const val TAG = "ConvoyVM"
    }
}

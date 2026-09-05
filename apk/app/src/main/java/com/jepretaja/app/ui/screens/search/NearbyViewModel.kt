package com.jepretaja.app.ui.screens.search

import androidx.lifecycle.ViewModel
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.repository.CreatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NearbyViewModel @Inject constructor(
    private val repository: CreatorRepository,
) : ViewModel() {
    suspend fun nearby(lat: Double, lng: Double, radiusKm: Double): List<Pair<CreatorModel, Double>> =
        runCatching { repository.nearbyWithDistance(lat, lng, radiusKm) }.getOrDefault(emptyList())
}

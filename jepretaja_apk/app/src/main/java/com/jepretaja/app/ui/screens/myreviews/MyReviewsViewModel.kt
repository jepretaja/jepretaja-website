package com.jepretaja.app.ui.screens.myreviews

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.core.util.asFlow
import com.jepretaja.app.data.model.ReviewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject

@HiltViewModel
class MyReviewsViewModel @Inject constructor(private val db: FirebaseFirestore) : ViewModel() {
    fun myReviews(customerId: String): Flow<List<ReviewModel>> =
        db.collection(FirestorePaths.REVIEWS)
            .whereEqualTo("customerId", customerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .asFlow<ReviewModel>()
            .catch { emit(emptyList()) }
}

package com.fleetcare.obd.data.repository

import com.fleetcare.obd.data.remote.firebase.FirebaseAuthManager
import com.fleetcare.obd.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación concreta del AuthRepository.
 *
 * Delega las operaciones de autenticación al FirebaseAuthManager.
 * Esta capa actúa como adaptador entre el dominio y la fuente de datos.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuthManager: FirebaseAuthManager
) : AuthRepository {

    override val authStateFlow: Flow<FirebaseUser?>
        get() = firebaseAuthManager.authStateFlow

    override val currentUserId: String?
        get() = firebaseAuthManager.currentUserId

    override val isUserAuthenticated: Boolean
        get() = firebaseAuthManager.isUserAuthenticated

    override suspend fun signInAnonymously(): Result<FirebaseUser> {
        return firebaseAuthManager.signInAnonymously()
    }

    override fun signOut() {
        firebaseAuthManager.signOut()
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return firebaseAuthManager.deleteAccount()
    }
}

package com.example.storibrianvianachallenge.main.ui.login


import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor() : ViewModel() {

    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val database = Firebase.database.reference
    private val storage: FirebaseStorage by lazy { Firebase.storage }
    private val _downloadUrlFront = MutableLiveData<String?>()
    val downloadUrlFront: LiveData<String?> get() = _downloadUrlFront

    private val _downloadUrlBack = MutableLiveData<String?>()
    val downloadUrlBack: LiveData<String?> get() = _downloadUrlBack
    private var _state = MutableStateFlow<LoginState>(LoginState.Loading)
    val state: StateFlow<LoginState> get() = _state

    private var _stateForget = MutableStateFlow<LoginState>(LoginState.Loading)
    val stateForget: StateFlow<LoginState> get() = _stateForget

    fun signUpWithEmailAndPassword(
        email: String,
        password: String,
        nombre: String,
        apellido: String,
        uriFront: String?,
        uriBack: String?
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user?.uid ?: ""

                    val userRef = database.child("users").child(userId)
                    userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                _state.value = LoginState.Error("Ya existe un usuario con este ID")
                            } else {
                                val userData = hashMapOf(
                                    "nombre" to nombre,
                                    "apellido" to apellido,
                                    "correo" to email,
                                    "uri_front" to uriFront,
                                    "uri_back" to uriBack
                                )
                                userRef.setValue(userData)
                                _state.value =
                                    LoginState.SuccessLogin("Usuario creado con éxito, Para Activar su cuenta espere el proceso de verificación")
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            _state.value =
                                LoginState.Error("Error al crear el usuario: ${error.message}")
                        }
                    })
                } else {
                    _state.value = LoginState.Error("Ocurrió un problema al crear la cuenta")
                }
            }
    }

    fun signInWithEmailAndPassword(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _state.value = LoginState.SuccessLogin(auth.currentUser?.uid ?: "")
                } else {
                    _state.value = LoginState.Error("Error al iniciar sesión")
                    Log.e("LoginViewModel", "Error: ${task.exception?.message}")
                }
            }
    }

    fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _stateForget.value =
                        LoginState.SuccessLogin("Se ha enviado un correo electrónico para restablecer su contraseña.")
                } else {
                    _stateForget.value =
                        LoginState.Error("Error al enviar el correo electrónico para restablecer la contraseña.")
                }
            }
    }

    fun signOut() {
        auth.signOut()
    }


    //ASIGNAMOS LA URI QUE NOS DA STORAGE Y LA GUARDAMOS DENTRO DEL NODO DE USAURIOS
    suspend fun savePhoto(photoUri: Uri, isFrontCamera: Boolean) {
        val storageRef: StorageReference = storage.reference

        val photoRef = storageRef.child("photos").child("Photo_${System.currentTimeMillis()}.jpg")

        try {
            photoRef.putFile(photoUri).await()
            val url = photoRef.downloadUrl.await().toString()

            if (isFrontCamera) {
                _downloadUrlFront.postValue(url)
            } else {
                _downloadUrlBack.postValue(url)
            }
        } catch (e: Exception) {
            Log.e("LoginViewModel", "Error al guardar la foto en Firebase Storage: ${e.message}", e)
            if (isFrontCamera) {
                _downloadUrlFront.postValue(null)
            } else {
                _downloadUrlBack.postValue(null)
            }
        }
    }
}
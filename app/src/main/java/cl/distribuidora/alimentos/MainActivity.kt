package cl.distribuidora.alimentos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import cl.distribuidora.alimentos.ui.theme.DistribuidoraAlimentosTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    // Cliente para obtener la ubicación del dispositivo
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Coordenadas fijas de la bodega (Santiago centro)
    private val bodegaLat = -33.4372
    private val bodegaLon = -70.6506

    // Autenticación y base de datos de Firebase
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val dbRef: DatabaseReference by lazy { Firebase.database.reference }

    // Sistema para solicitar permisos de ubicación
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Verificar si los permisos fueron concedidos
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (!fine && !coarse) {
            Log.w("MainActivity", "Permisos de ubicación no otorgados")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inicializar el cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Solicitar permisos de ubicación al iniciar
        solicitarPermisosUbicacion()

        // Configurar la interfaz de usuario con Jetpack Compose
        setContent {
            DistribuidoraAlimentosTheme {
                AppContent() // Contenido principal de la pantalla de login
            }
        }
    }

    // Función para solicitar permisos de ubicación
    private fun solicitarPermisosUbicacion() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Si no tiene permisos, solicitarlos
            val permisos = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            requestPermissionLauncher.launch(permisos)
        }
    }

    // Obtener la última ubicación conocida del dispositivo
    @SuppressLint("MissingPermission")
    fun obtenerUltimaUbicacion(onLocation: (Location?) -> Unit) {
        // Verificar permisos antes de obtener ubicación
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onLocation(null)
            return
        }
        // Obtener ubicación y pasar el resultado al callback
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location -> onLocation(location) }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error al obtener ubicación: $e")
                onLocation(null)
            }
    }

    // Calcular distancia usando fórmula Haversine (precisa para distancias cortas)
    fun calcularDistanciaHaversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radioTierra = 6371.0 // Radio de la Tierra en kilómetros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radioTierra * c // Distancia en kilómetros
    }

    // Calcular costo de despacho según reglas de negocio
    fun calcularDespacho(totalCompra: Double, distanciaKm: Double): Double {
        return when {
            totalCompra >= 50000 -> 0.0 // Despacho gratis para compras sobre 50k
            totalCompra in 25000.0..49999.0 -> 150 * distanciaKm // $150 por km
            else -> 300 * distanciaKm // $300 por km para compras menores
        }
    }

    // Interfaz de usuario principal (pantalla de login)
    @Composable
    fun AppContent() {
        // Variables de estado para los campos del formulario
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var mostrarPassword by remember { mutableStateOf(false) }
        var emailError by remember { mutableStateOf("") }
        var passwordError by remember { mutableStateOf("") }
        var mensajeInfo by remember { mutableStateOf("") }

        val context = LocalContext.current

        // Diseño de la pantalla en columna
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Distribuidora de Alimentos", fontSize = 24.sp)

            // Campo de email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = ""; mensajeInfo = "" },
                label = { Text("Email (Gmail)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = emailError.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
            if (emailError.isNotEmpty()) {
                Text(text = emailError, color = MaterialTheme.colorScheme.error)
            }

            // Campo de contraseña con icono para mostrar/ocultar
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; passwordError = ""; mensajeInfo = "" },
                label = { Text("Contraseña") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (mostrarPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { mostrarPassword = !mostrarPassword }) {
                        Icon(
                            imageVector = if (mostrarPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (mostrarPassword) "Ocultar" else "Mostrar"
                        )
                    }
                },
                isError = passwordError.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
            if (passwordError.isNotEmpty()) {
                Text(text = passwordError, color = MaterialTheme.colorScheme.error)
            }

            // Botón de login
            Button(onClick = {
                // Validaciones básicas
                if (!email.contains("@") || !email.trim().endsWith("@gmail.com")) {
                    emailError = "Debe usar una cuenta @gmail.com"
                    return@Button
                }
                if (password.length < 6) {
                    passwordError = "Contraseña mínima 6 caracteres"
                    return@Button
                }

                mensajeInfo = "Iniciando sesión..."
                // Autenticar con Firebase
                auth.signInWithEmailAndPassword(email.trim(), password)
                    .addOnCompleteListener(this@MainActivity) { task ->
                        if (task.isSuccessful) {
                            mensajeInfo = "Login exitoso. Guardando ubicación..."
                            val userId = auth.currentUser?.uid ?: "anon"
                            // Obtener y guardar ubicación del usuario
                            obtenerUltimaUbicacion { location ->
                                val intent = Intent(context, MenuActivity::class.java)
                                if (location != null) {
                                    // Guardar ubicación en Firebase Realtime Database
                                    val ubic = mapOf(
                                        "lat" to location.latitude,
                                        "lon" to location.longitude,
                                        "timestamp" to ServerValue.TIMESTAMP
                                    )
                                    dbRef.child("usuarios").child(userId).child("ultima_ubicacion")
                                        .setValue(ubic)
                                        .addOnSuccessListener { context.startActivity(intent) }
                                        .addOnFailureListener { e ->
                                            Log.e("MainActivity", "Error guardando ubicación: $e")
                                            context.startActivity(intent)
                                        }
                                } else {
                                    context.startActivity(intent)
                                }
                            }
                        } else {
                            mensajeInfo = ""
                            emailError = "Error al iniciar sesión: ${task.exception?.message ?: "credenciales inválidas"}"
                        }
                    }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Iniciar sesión")
            }

            // Mensaje informativo
            if (mensajeInfo.isNotEmpty()) {
                Text(text = mensajeInfo)
            }
        }
    }
}



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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class MenuActivity : ComponentActivity() {

    // Cliente para ubicación y autenticación Firebase
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val auth: FirebaseAuth by lazy { Firebase.auth }

    // Coordenadas de la bodega
    private val bodegaLat = -33.4372
    private val bodegaLon = -70.6506

    // Sistema para solicitar permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        solicitarPermisosUbicacion()
        setContent { MenuContent() } // Pantalla principal del menú
    }

    // Solicitar permisos de ubicación si no los tiene
    private fun solicitarPermisosUbicacion() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val permisos = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            requestPermissionLauncher.launch(permisos)
        }
    }

    // Obtener ubicación actual del dispositivo
    @SuppressLint("MissingPermission")
    fun obtenerUltimaUbicacion(onLocation: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onLocation(null)
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { loc -> onLocation(loc) }
            .addOnFailureListener { e ->
                Log.e("MenuActivity", "Error al obtener ubicación: $e")
                onLocation(null)
            }
    }

    // Calcular distancia entre usuario y bodega
    fun calcularDistanciaHaversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radioTierra = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radioTierra * c
    }

    // Calcular costo de despacho según reglas de negocio
    fun calcularDespacho(totalCompra: Double, distanciaKm: Double): Double {
        return when {
            totalCompra >= 50000 -> 0.0 // Gratis
            totalCompra in 25000.0..49999.0 -> 150 * distanciaKm // $150/km
            else -> 300 * distanciaKm // $300/km
        }
    }

    // Interfaz del menú principal
    @Composable
    fun MenuContent() {
        // Variables de estado para los campos y mensajes
        var montoCompra by remember { mutableStateOf("") }
        var distancia by remember { mutableStateOf("") }
        var temperatura by remember { mutableStateOf("") }
        var mensajeDespacho by remember { mutableStateOf("") }
        var mensajeAlerta by remember { mutableStateOf("") }

        val context = LocalContext.current
        val email = auth.currentUser?.email ?: "Usuario" // Email del usuario logueado

        // Diseño de la pantalla
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Menú - Distribuidora", fontSize = 24.sp)
            Text(text = "Usuario: $email") // Mostrar email del usuario

            // Campo para monto de compra
            OutlinedTextField(
                value = montoCompra,
                onValueChange = { montoCompra = it },
                label = { Text("Monto de compra") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Campo para temperatura del camión
            OutlinedTextField(
                value = temperatura,
                onValueChange = { temperatura = it },
                label = { Text("Temperatura del camión (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Campo de solo lectura para mostrar distancia
            OutlinedTextField(
                value = distancia,
                onValueChange = {},
                label = { Text("Distancia (km)") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Botón para obtener ubicación GPS
            Button(onClick = {
                obtenerUltimaUbicacion { loc ->
                    if (loc != null) {
                        // Calcular distancia a la bodega
                        val d = calcularDistanciaHaversine(loc.latitude, loc.longitude, bodegaLat, bodegaLon)
                        distancia = String.format("%.0f", d) // Formatear a 0 decimales
                        mensajeAlerta = ""
                        mensajeDespacho = ""
                    } else {
                        distancia = ""
                        mensajeAlerta = "No se pudo obtener la ubicación."
                    }
                }
            }) { Text("Obtener Ubicación GPS") }

            // Botón para calcular despacho y verificar temperatura
            Button(onClick = {
                val total = montoCompra.toDoubleOrNull() ?: 0.0
                val km = distancia.toDoubleOrNull() ?: 0.0
                val temp = temperatura.toDoubleOrNull() ?: -9999.0

                // Calcular costo de despacho
                val despacho = calcularDespacho(total, km)
                mensajeDespacho = "Costo despacho: $${despacho.toInt()}"

                // Verificar alerta de temperatura (cadena de frío)
                mensajeAlerta = if (temp > 0.0) {
                    "ALERTA: Temperatura (${temp}°C) supera 0°C!"
                } else {
                    "Temperatura (${temp}°C) dentro del límite."
                }
            }) { Text("Calcular Despacho") }

            // Mostrar resultados
            if (mensajeDespacho.isNotEmpty()) Text(text = mensajeDespacho)
            if (mensajeAlerta.isNotEmpty())
                Text(
                    text = mensajeAlerta,
                    color = if (mensajeAlerta.contains("ALERTA")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )

            Spacer(modifier = Modifier.height(20.dp))

            // Botón para cerrar sesión
            Button(onClick = {
                auth.signOut() // Cerrar sesión en Firebase
                context.startActivity(Intent(context, MainActivity::class.java)) // Volver al login
                (context as? ComponentActivity)?.finish() // Cerrar esta actividad
            }) { Text("Cerrar sesión") }
        }
    }
}

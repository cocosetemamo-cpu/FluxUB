package com.fluxub

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var btnIniciar: Button
    private lateinit var txtEstado: TextView
    private var servicioActivo = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        btnIniciar = findViewById(R.id.btnIniciar)
        txtEstado = findViewById(R.id.txtEstado)
        
        // Verificar si el servicio ya está corriendo
        val intent = Intent(this, FloatingMenuService::class.java)
        val running = intent.let { 
            // Comprobar si el servicio está activo (simplificado)
            false
        }
        
        actualizarUI()
        
        btnIniciar.setOnClickListener {
            if (servicioActivo) {
                // Detener servicio
                stopService(Intent(this, FloatingMenuService::class.java))
                servicioActivo = false
                actualizarUI()
                Toast.makeText(this, "Servicio detenido", Toast.LENGTH_SHORT).show()
            } else {
                // Iniciar servicio
                if (Settings.canDrawOverlays(this)) {
                    startFloatingService()
                } else {
                    solicitarPermisoOverlay()
                }
            }
        }
    }
    
    private fun solicitarPermisoOverlay() {
        AlertDialog.Builder(this)
            .setTitle("Permiso necesario")
            .setMessage("FluxUB necesita permiso para mostrar el círculo flotante sobre otras apps. ¿Quieres concederlo?")
            .setPositiveButton("Sí") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 100)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && Settings.canDrawOverlays(this)) {
            startFloatingService()
        } else {
            Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startFloatingService() {
        val intent = Intent(this, FloatingMenuService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        servicioActivo = true
        actualizarUI()
        Toast.make

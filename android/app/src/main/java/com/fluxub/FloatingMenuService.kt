package com.fluxub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingMenuService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var menuView: View
    private var isMenuOpen = false
    private var isActive = false
    private var wsClient: WebSocketCliente? = null
    private var circleX = 0
    private var circleY = 0
    private var dispositivoSeleccionado: String? = null
    private var dispositivoNombre: String = "Ninguno"
    
    companion object {
        const val CHANNEL_ID = "FluxUBChannel"
        const val NOTIFICATION_ID = 1
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Crear canal de notificación para Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FluxUB Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene el círculo flotante activo"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        
        // Notificación para mantener el servicio vivo
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔥 FluxUB")
            .setContentText("Servicio activo")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        crearCircleButton()
        crearMenu()
    }
    
    // ========== 1. BOTÓN CIRCULAR FLOTANTE ==========
    private fun crearCircleButton() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.layout_circle_button, null)
        
        val circleButton = floatingView.findViewById<ImageView>(R.id.circleButton)
        circleButton.setOnClickListener {
            toggleMenu()
        }
        
        floatingView.setOnTouchListener(CircleTouchListener())
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 600
        
        windowManager.addView(floatingView, params)
    }
    
    // ========== 2. MENÚ DESPLEGABLE ==========
    private fun crearMenu() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        menuView = inflater.inflate(R.layout.layout_floating_menu, null)
        
        val btnToggle = menuView.findViewById<Button>(R.id.btnToggle)
        val btnClose = menuView.findViewById<Button>(R.id.btnClose)
        val txtStatus = menuView.findViewById<TextView>(R.id.txtStatus)
        val txtDevice = menuView.findViewById<TextView>(R.id.txtDevice)
        
        // Estado inicial
        txtDevice.text = "📟 $dispositivoNombre"
        txtStatus.text = "🔴 INACTIVO"
        txtStatus.setTextColor(Color.RED)
        btnToggle.text = "▶️ ACTIVAR"
        btnToggle.setBackgroundColor(Color.parseColor("#4CAF50"))
        
        btnToggle.setOnClickListener {
            isActive = !isActive
            if (isActive) {
                // Conectar WebSocket y sincronizar
                conectarServidor()
                btnToggle.text = "⏹️ DESACTIVAR"
                btnToggle.setBackgroundColor(Color.parseColor("#F44336"))
                txtStatus.text = "🟢 ACTIVO"
                txtStatus.setTextColor(Color.parseColor("#4CAF50"))
                actualizarCircleColor(true)
            } else {
                // Desconectar
                desconectarServidor()
                btnToggle.text = "▶️ ACTIVAR"
                btnToggle.setBackgroundColor(Color.parseColor("#4CAF50"))
                txtStatus.text = "🔴 INACTIVO"
                txtStatus.setTextColor(Color.RED)
                actualizarCircleColor(false)
            }
        }
        
        btnClose.setOnClickListener {
            closeMenu()
        }
        
        menuView.visibility = View.GONE
    }
    
    // ========== 3. TOGGLE MENÚ ==========
    private fun toggleMenu() {
        if (isMenuOpen) {
            closeMenu()
        } else {
            openMenu()
        }
    }
    
    private fun openMenu() {
        if (isMenuOpen) return
        isMenuOpen = true
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.START
        params.x = circleX + 160
        params.y = circleY
        
        try {
            windowManager.addView(menuView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun closeMenu() {
        if (!isMenuOpen) return
        isMenuOpen = false
        try {
            if (menuView.isAttachedToWindow) {
                windowManager.removeView(menuView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // ========== 4. TOUCH LISTENER ==========
    inner class CircleTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false
        
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (v.layoutParams as WindowManager.LayoutParams).x
                    initialY = (v.layoutParams as WindowManager.LayoutParams).y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = v.layoutParams as WindowManager.LayoutParams
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(v, params)
                    circleX = params.x
                    circleY = params.y
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggleMenu()
                    }
                    return true
                }
            }
            return false
        }
    }
    
    // ========== 5. CONEXIÓN CON EL SERVIDOR ==========
    private fun conectarServidor() {
        // Intentar obtener IP del servidor (hardcodeada o por descubrimiento)
        val serverIp = "192.168.1.100" // Cambiar por la IP de tu PC
        val wsUrl = "ws://$serverIp:8765"
        
        wsClient = WebSocketCliente(wsUrl)
        wsClient?.conectar(object : WebSocketCliente.Listener {
            override fun onListaUSB(dispositivos: List<Map<String, String>>) {
                val primerUSB = dispositivos.firstOrNull()
                if (primerUSB != null) {
                    dispositivoSeleccionado = primerUSB["id"]
                    dispositivoNombre = primerUSB["nombre"] ?: "Desconocido"
                    wsClient?.sincronizar(dispositivoSeleccionado ?: "")
                    
                    runOnUiThread {
                        val txtDevice = menuView.findViewById<TextView>(R.id.txtDevice)
                        txtDevice.text = "📟 $dispositivoNombre"
                    }
                }
            }
            
            override fun onEventos(eventos: List<Map<String, Any>>) {
                eventos.forEach { evento ->
                    val codigo = evento["codigo"] as? String ?: ""
                    val estado = evento["estado"] as? Int ?: 0
                    enviarPorBluetooth("$codigo:$estado")
                }
            }
            
            override fun onEstado(mensaje: String) {
                runOnUiThread {
                    val txtStatus = menuView.findViewById<TextView>(R.id.txtStatus)
                    txtStatus.text = mensaje
                }
            }
            
            override fun onDesconectado() {
                runOnUiThread {
                    Toast.makeText(this@FloatingMenuService, "⚠️ Desconectado del servidor", Toast.LENGTH_SHORT).show()
                }
                if (isActive) {
                    // Reintentar después de 5 segundos
                    android.os.Handler(mainLooper).postDelayed({
                        if (isActive) conectarServidor()
                    }, 5000)
                }
            }
        })
    }
    
    private fun desconectarServidor() {
        wsClient?.dessincronizar()
        wsClient?.desconectar()
        wsClient = null
    }
    
    // ========== 6. BLUETOOTH ==========
    private fun enviarPorBluetooth(datos: String) {
        try {
            val btAdapter = BluetoothAdapter.getDefaultAdapter()
            if (btAdapter == null) {
                // Sin Bluetooth, solo log
                return
            }
            
            // Buscar dispositivo emparejado (hardcodeado o por selección)
            val macAddress = "00:11:22:33:44:55" // Cambiar por MAC real
            val dispositivoBT = btAdapter.getRemoteDevice(macAddress)
            val socket = dispositivoBT.createRfcommSocketToServiceRecord(
                java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            )
            socket.connect()
            socket.outputStream.write(datos.toByteArray())
            socket.close()
        } catch (e: Exception) {
            // Sin conexión BT, solo log
        }
    }
    
    // ========== 7. ACTUALIZAR COLOR DEL CÍRCULO ==========
    private fun actualizarCircleColor(activo: Boolean) {
        val circleButton = floatingView.findViewById<ImageView>(R.id.circleButton)
        if (activo) {
            circleButton.setColorFilter(Color.parseColor("#4CAF50"))
        } else {
            circleButton.setColorFilter(Color.parseColor("#F44336"))
        }
    }
    
    private fun runOnUiThread(action: () -> Unit) {
        android.os.Handler(mainLooper).post(action)
    }
    
    // ========== 8. DESTRUIR SERVICIO ==========
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
                windowManager.removeView(floatingView)
            }
        } catch (e: Exception) { /* Ignorar */ }
        
        try {
            if (::menuView.isInitialized && menuView.isAttachedToWindow) {
                windowManager.removeView(menuView)
            }
        } catch (e: Exception) { /* Ignorar */ }
        
        desconectarServidor()
        stopForeground(true)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

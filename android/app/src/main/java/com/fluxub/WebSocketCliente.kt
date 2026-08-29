package com.fluxub

import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketCliente(private val url: String) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    
    interface Listener {
        fun onListaUSB(dispositivos: List<Map<String, String>>)
        fun onEventos(eventos: List<Map<String, Any>>)
        fun onEstado(mensaje: String)
        fun onDesconectado()
    }
    
    fun conectar(listener: Listener) {
        val request = Request.Builder().url(url).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("✅ WebSocket conectado a $url")
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val tipo = json.getString("tipo")
                    
                    when (tipo) {
                        "lista" -> {
                            val dispositivos = mutableListOf<Map<String, String>>()
                            val arr = json.getJSONArray("datos")
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                dispositivos.add(mapOf(
                                    "id" to obj.getString("id"),
                                    "nombre" to obj.getString("nombre"),
                                    "tipo" to obj.optString("tipo", "USB")
                                ))
                            }
                            listener.onListaUSB(dispositivos)
                        }
                        "eventos" -> {
                            val eventos = mutableListOf<Map<String, Any>>()
                            val arr = json.getJSONArray("datos")
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                eventos.add(mapOf(
                                    "codigo" to obj.getString("codigo"),
                                    "estado" to obj.getInt("estado"),
                                    "timestamp" to obj.getDouble("timestamp")
                                ))
                            }
                            listener.onEventos(eventos)
                        }
                        "estado" -> {
                            listener.onEstado(json.getString("mensaje"))
                        }
                        "error" -> {
                            listener.onEstado("❌ Error: ${json.getString("mensaje")}")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("❌ WebSocket error: ${t.message}")
                listener.onDesconectado()
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                println("⚠️ WebSocket cerrado: $reason")
                listener.onDesconectado()
            }
        })
    }
    
    fun sincronizar(id: String) {
        val json = JSONObject().apply {
            put("comando", "sincronizar")
            put("id", id)
        }
        webSocket?.send(json.toString())
    }
    
    fun dessincronizar() {
        val json = JSONObject().apply {
            put("comando", "dessincronizar")
        }
        webSocket?.send(json.toString())
    }
    
    fun desconectar() {
        webSocket?.close(1000, "Cerrando conexión")
        client.dispatcher.executorService.shutdown()
    }
}

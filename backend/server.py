#!/usr/bin/env python3
"""
FluxUB - USB to Bluetooth Bridge
Servidor Web + WebSocket + API
"""

import asyncio
import json
import threading
import time
import socket
import os
import sys
from pathlib import Path
from http.server import HTTPServer, SimpleHTTPRequestHandler
import websockets

# Intentar importar inputs (para captura USB)
try:
    from inputs import get_gamepad
    HAS_INPUTS = True
except ImportError:
    HAS_INPUTS = False
    print("⚠️  inputs no instalado. Usando modo simulación.")
    print("   Instalar con: pip install inputs")

# ========== CONFIGURACIÓN ==========
PUERTO_HTTP = 8080
PUERTO_WS = 8765
SINCRONIZADO = False
DISPOSITIVO_SELECCIONADO = None
EVENTOS_PENDIENTES = []
CLIENTES = set()
USB_DEVICES = []

# ========== DETECCIÓN USB ==========
def listar_usb():
    """Detecta dispositivos USB conectados (simulado o real)"""
    global USB_DEVICES
    
    if HAS_INPUTS:
        try:
            # Intento de detección real
            dispositivos = []
            # Aquí iría la lógica real con pyusb
            # Por ahora usamos simulación
            pass
        except:
            pass
    
    # SIMULACIÓN (para pruebas sin hardware)
    return [
        {"id": "VID_045E:PID_028E", "nombre": "Xbox 360 Controller", "tipo": "gamepad"},
        {"id": "VID_046D:PID_C21D", "nombre": "Logitech Gamepad", "tipo": "gamepad"},
        {"id": "VID_0B05:PID_1819", "nombre": "Teclado USB", "tipo": "keyboard"},
        {"id": "VID_045E:PID_00CB", "nombre": "Mouse USB", "tipo": "mouse"},
        {"id": "VID_0B05:PID_1A12", "nombre": "USB Hub", "tipo": "hub"}
    ]

# ========== CAPTURA USB (HILO) ==========
def capturar_usb():
    """Hilo que captura eventos de dispositivos USB"""
    global EVENTOS_PENDIENTES, SINCRONIZADO, DISPOSITIVO_SELECCIONADO
    
    print("🎮 Iniciando captura USB...")
    
    if not HAS_INPUTS:
        # MODO SIMULACIÓN - Genera eventos de prueba
        simular_eventos()
        return
    
    while True:
        try:
            if SINCRONIZADO and DISPOSITIVO_SELECCIONADO:
                eventos = get_gamepad()
                for ev in eventos:
                    EVENTOS_PENDIENTES.append({
                        "codigo": ev.code,
                        "estado": ev.state,
                        "timestamp": time.time()
                    })
            time.sleep(0.005)  # 5ms
        except Exception as e:
            # Si falla, esperar y reintentar
            time.sleep(1)

def simular_eventos():
    """Genera eventos de prueba para desarrollo sin hardware"""
    global EVENTOS_PENDIENTES, SINCRONIZADO
    
    codigos = ["BTN_A", "BTN_B", "BTN_X", "BTN_Y", "ABS_X", "ABS_Y"]
    estado = 0
    
    print("🔬 MODO SIMULACIÓN: Generando eventos de prueba")
    
    while True:
        if SINCRONIZADO:
            for codigo in codigos:
                estado = 1 if estado == 0 else 0
                EVENTOS_PENDIENTES.append({
                    "codigo": codigo,
                    "estado": estado,
                    "timestamp": time.time()
                })
                time.sleep(0.5)  # 1 evento cada 500ms
        else:
            time.sleep(0.1)

# ========== SERVIDOR WEBSOCKET ==========
async def ws_handler(websocket):
    """Maneja conexiones WebSocket"""
    global SINCRONIZADO, EVENTOS_PENDIENTES, DISPOSITIVO_SELECCIONADO
    
    CLIENTES.add(websocket)
    ip = websocket.remote_address[0]
    print(f"📱 Cliente conectado: {ip}")
    
    try:
        # Enviar lista de USB al conectar
        dispositivos = listar_usb()
        await websocket.send(json.dumps({
            "tipo": "lista",
            "datos": dispositivos
        }))
        
        # Bucle principal de mensajes
        async for mensaje in websocket:
            try:
                data = json.loads(mensaje)
                comando = data.get("comando")
                
                if comando == "sincronizar":
                    dispositivo_id = data.get("id")
                    if dispositivo_id:
                        DISPOSITIVO_SELECCIONADO = dispositivo_id
                        SINCRONIZADO = True
                        await websocket.send(json.dumps({
                            "tipo": "estado",
                            "estado": "sincronizado",
                            "mensaje": f"✅ Sincronizado con {dispositivo_id}"
                        }))
                        print(f"🔗 Sincronizado con: {dispositivo_id}")
                    else:
                        await websocket.send(json.dumps({
                            "tipo": "error",
                            "mensaje": "ID de dispositivo no válido"
                        }))
                
                elif comando == "dessincronizar":
                    SINCRONIZADO = False
                    DISPOSITIVO_SELECCIONADO = None
                    EVENTOS_PENDIENTES = []
                    await websocket.send(json.dumps({
                        "tipo": "estado",
                        "estado": "desincronizado",
                        "mensaje": "⏸️ Desincronizado"
                    }))
                    print("⏹️  Desincronizado")
                
                elif comando == "get_eventos":
                    if EVENTOS_PENDIENTES:
                        eventos = EVENTOS_PENDIENTES.copy()
                        EVENTOS_PENDIENTES = []
                        await websocket.send(json.dumps({
                            "tipo": "eventos",
                            "datos": eventos
                        }))
                
                elif comando == "ping":
                    await websocket.send(json.dumps({
                        "tipo": "pong",
                        "timestamp": time.time()
                    }))
                    
            except json.JSONDecodeError:
                await websocket.send(json.dumps({
                    "tipo": "error",
                    "mensaje": "JSON inválido"
                }))
                
    except websockets.exceptions.ConnectionClosed:
        print(f"📱 Cliente desconectado: {ip}")
    except Exception as e:
        print(f"⚠️ Error con cliente {ip}: {e}")
    finally:
        CLIENTES.remove(websocket)
        if websocket in CLIENTES:
            CLIENTES.remove(websocket)

# ========== SERVIDOR HTTP (INTERFAZ WEB) ==========
class FluxUBHandler(SimpleHTTPRequestHandler):
    """Sirve la interfaz web desde frontend/"""
    
    def do_GET(self):
        # Ruta raíz -> index.html
        if self.path == '/':
            self.path = '/index.html'
        return super().do_GET()
    
    def log_message(self, format, *args):
        # Silenciar logs HTTP
        pass

def iniciar_http():
    """Inicia el servidor HTTP en un hilo"""
    # Cambiar al directorio frontend
    frontend_dir = Path(__file__).parent.parent / 'frontend'
    if frontend_dir.exists():
        os.chdir(frontend_dir)
    else:
        # Si no existe, usar directorio actual
        print("⚠️  Carpeta frontend no encontrada, usando directorio actual")
    
    httpd = HTTPServer(('0.0.0.0', PUERTO_HTTP), FluxUBHandler)
    print(f"🌐 Interfaz web: http://localhost:{PUERTO_HTTP}")
    print(f"🌐 Red local: http://{get_local_ip()}:{PUERTO_HTTP}")
    httpd.serve_forever()

def get_local_ip():
    """Obtiene la IP local de la red"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return "127.0.0.1"

# ========== MAIN ==========
async def main():
    """Punto de entrada principal"""
    print("""
    ╔═══════════════════════════════════════════════════╗
    ║   🔥 FLUXUB v1.0 🔥                              ║
    ║   USB → WiFi → Bluetooth Bridge                  ║
    ║                                                   ║
    ║   🌐 Web UI: http://localhost:8080              ║
    ║   📡 WebSocket: ws://localhost:8765             ║
    ║   🖥️  IP Local: {0}                             ║
    ╚═══════════════════════════════════════════════════╝
    """.format(get_local_ip()))
    
    # Iniciar captura USB en hilo
    threading.Thread(target=capturar_usb, daemon=True).start()
    print("✅ Hilo de captura USB iniciado")
    
    # Iniciar servidor HTTP en hilo
    threading.Thread(target=iniciar_http, daemon=True).start()
    print("✅ Servidor HTTP iniciado")
    
    # Iniciar servidor WebSocket
    print("✅ Servidor WebSocket iniciado")
    async with websockets.serve(ws_handler, "0.0.0.0", PUERTO_WS):
        print("📡 WebSocket listo en ws://0.0.0.0:8765")
        await asyncio.Future()  # Correr para siempre

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n⏹️  Servidor detenido por el usuario")
    except Exception as e:
        print(f"❌ Error: {e}")
        sys.exit(1)

// ========== FLUXUB - Cliente Web ==========

// Configuración
const WS_URL = `ws://${window.location.hostname}:8765`;
let ws = null;
let dispositivoSeleccionado = null;
let sincronizado = false;
let eventCount = 0;
let reconectando = false;

// ========== DOM ==========
const statusIndicator = document.getElementById('statusIndicator');
const statusText = document.getElementById('statusText');
const usbList = document.getElementById('usbList');
const btnSync = document.getElementById('btnSync');
const btnDesync = document.getElementById('btnDesync');
const eventDisplay = document.getElementById('eventDisplay');
const eventCountSpan = document.getElementById('eventCount');

// ========== CONEXIÓN WEBSOCKET ==========
function conectar() {
    if (reconectando) return;
    
    try {
        ws = new WebSocket(WS_URL);
        
        ws.onopen = () => {
            reconectando = false;
            setStatus('online', '🟢 Conectado al servidor');
            btnSync.disabled = true;
            btnDesync.disabled = true;
            console.log('✅ WebSocket conectado');
        };
        
        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                console.log('📨 Recibido:', data);
                
                switch (data.tipo) {
                    case 'lista':
                        mostrarUSB(data.datos);
                        break;
                    case 'estado':
                        setStatus('sync', data.mensaje);
                        sincronizado = data.estado === 'sincronizado';
                        actualizarBotones();
                        break;
                    case 'eventos':
                        mostrarEventos(data.datos);
                        break;
                    case 'pong':
                        // Mantener conexión viva
                        break;
                    case 'error':
                        console.error('❌ Error:', data.mensaje);
                        break;
                    default:
                        console.log('📨 Tipo desconocido:', data.tipo);
                }
            } catch (e) {
                console.error('❌ Error al procesar mensaje:', e);
            }
        };
        
        ws.onerror = (error) => {
            console.error('❌ Error WebSocket:', error);
            setStatus('offline', '❌ Error de conexión');
            btnSync.disabled = true;
            btnDesync.disabled = true;
        };
        
        ws.onclose = () => {
            console.log('⚠️ WebSocket cerrado');
            setStatus('offline', '⛔ Desconectado');
            btnSync.disabled = true;
            btnDesync.disabled = true;
            
            // Reconectar automáticamente
            if (!reconectando) {
                reconectando = true;
                setTimeout(() => {
                    console.log('🔄 Reintentando conexión...');
                    conectar();
                }, 3000);
            }
        };
        
    } catch (e) {
        console.error('❌ Error al conectar:', e);
        setTimeout(conectar, 5000);
    }
}

// ========== UI ==========
function setStatus(tipo, mensaje) {
    statusIndicator.className = 'status-indicator';
    if (tipo === 'online') {
        statusIndicator.classList.add('online');
    } else if (tipo === 'sync') {
        statusIndicator.classList.add('sync');
    } else if (tipo === 'offline') {
        statusIndicator.classList.add('offline');
    }
    statusText.textContent = mensaje;
}

function mostrarUSB(dispositivos) {
    usbList.innerHTML = '';
    
    if (!dispositivos || dispositivos.length === 0) {
        usbList.innerHTML = '<li class="loading">❌ No se detectaron dispositivos USB</li>';
        return;
    }
    
    dispositivos.forEach(dev => {
        const li = document.createElement('li');
        li.dataset.id = dev.id;
        li.innerHTML = `
            <span>
                <span style="margin-right: 8px;">🔌</span>
                ${dev.nombre}
                <span class="device-id">${dev.id}</span>
            </span>
            <span class="device-tipo">${dev.tipo || 'USB'}</span>
        `;
        li.onclick = () => seleccionarUSB(dev.id, li);
        usbList.appendChild(li);
    });
    
    // Seleccionar el primero por defecto
    const primero = usbList.querySelector('li');
    if (primero) {
        primero.classList.add('selected');
        dispositivoSeleccionado = primero.dataset.id;
        btnSync.disabled = false;
    }
}

function seleccionarUSB(id, elemento) {
    document.querySelectorAll('.device-list li').forEach(el => {
        el.classList.remove('selected');
    });
    elemento.classList.add('selected');
    dispositivoSeleccionado = id;
    btnSync.disabled = sincronizado;
}

function actualizarBotones() {
    btnSync.disabled = sincronizado || !dispositivoSeleccionado;
    btnDesync.disabled = !sincronizado;
}

function mostrarEventos(eventos) {
    if (!eventos || eventos.length === 0) return;
    
    // Quitar mensaje vacío
    const emptyMsg = eventDisplay.querySelector('.event-empty');
    if (emptyMsg) emptyMsg.remove();
    
    const fragment = document.createDocumentFragment();
    eventos.forEach(ev => {
        const div = document.createElement('div');
        div.className = 'event-item';
        const time = new Date(ev.timestamp * 1000).toLocaleTimeString();
        div.innerHTML = `
            <span class="event-time">${time}</span>
            <span class="event-code">${ev.codigo}</span>
            <span class="event-state">→ ${ev.estado}</span>
        `;
        fragment.appendChild(div);
        eventCount++;
    });
    
    eventDisplay.prepend(fragment);
    eventCountSpan.textContent = eventCount;
    
    // Limitar a 100 eventos
    while (eventDisplay.children.length > 100) {
        eventDisplay.removeChild(eventDisplay.lastChild);
    }
}

// ========== EVENTOS DE BOTONES ==========
btnSync.addEventListener('click', () => {
    if (ws && ws.readyState === WebSocket.OPEN && dispositivoSeleccionado) {
        ws.send(JSON.stringify({
            comando: 'sincronizar',
            id: dispositivoSeleccionado
        }));
        setStatus('sync', '🔄 Sincronizando...');
        btnSync.disabled = true;
    } else {
        console.warn('⚠️ No se puede sincronizar');
    }
});

btnDesync.addEventListener('click', () => {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
            comando: 'dessincronizar'
        }));
        btnDesync.disabled = true;
        eventCount = 0;
        eventCountSpan.textContent = '0';
    }
});

// ========== INICIAR ==========
document.addEventListener('DOMContentLoaded', () => {
    console.log('🔥 FluxUB v1.0');
    conectar();
});

// ========== PING PARA MANTENER CONEXIÓN ==========
setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
            comando: 'ping'
        }));
    }
}, 30000);

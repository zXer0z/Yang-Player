# Última sesión y minirreproductor

El servicio de reproducción guarda la cola completa, metadatos, índice,
posición, repetición y mezcla. La posición se actualiza cada tres segundos
durante la reproducción y al recibir eventos del reproductor. Una limpieza
temporal de la cola al sustituir canciones no borra la última sesión guardada.

Cuando se crea un servicio nuevo, restaura la cola y la posición pausadas,
sin preparar el decodificador ni iniciar audio automáticamente. Si el servicio
ya sigue reproduciendo, la interfaz se sincroniza con su sesión real al
conectarse, sin sustituirla por la copia guardada ni esperar al cambio de canción.

Los MediaItems comparten un único constructor que conserva la URI original,
carátula, duración, formato y metadatos para Yin. Eso permite reconstruir la
cola desde MediaController, incluso cuando no expone su configuración local.
El minirreproductor toma del servicio la canción y el estado real; al perder la
conexión conserva los metadatos pero deja de anunciar reproducción activa y
reintenta conectarse. Un intento de reproducir antes de la conexión queda pendiente.

El preamplificador local aparece al principio de Hi-Fi: -24 a +12 dB en pasos
de 0,5 dB. Los movimientos actualizan el DSP local sin cambiar de motor; al
soltar se guardan por salida. Con Yin controlando el audio, sus efectos y preamp
siguen ajustándose en Yin para no procesar dos veces el PCM enviado.

Validación de entrega: compilación release/R8 y lint vital. Sin pruebas
automatizadas ni reproducción de audio; la persistencia comienza a guardarse
con esta versión y no puede recuperar una cola que versiones anteriores nunca
escribieron en disco.

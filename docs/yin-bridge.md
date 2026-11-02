# Integración con Yin Control

Yang Player y Yin Control se publican como repositorios independientes:
https://github.com/zXer0z/Yang-Player y https://github.com/zXer0z/Yin-Control.
Cada uno se compila con `./gradlew.bat :app:assembleRelease`. Se conserva el ID
`local.v60.player` de Yin; la configuración pública usa la firma de desarrollo
local y huellas configurables. Consulta el README para emparejar los certificados.

El contrato compartido está en `shared/yin-bridge/java`. La conexión se comprueba
mediante Binder y certificado, no enumerando procesos ni observando si la app
está en segundo plano. `OPEN` incluye formato PCM, volumen, pausa y token de
vida; la respuesta incluye ID de sesión y FD. `STATE`, `STATUS`, `EOS` y `CLOSE`
son comandos de control. La muerte del cliente libera la salida en Yin.

YinAudioSink conserva el transporte/decodificación y MediaSession de Media3,
pero transmite el PCM directamente a Yin cuando la salida externa está lista.
No ejecuta el DSP de Yang sobre ese PCM. El reloj usa las muestras reproducidas
por AudioTrack en Yin; pausa, volumen, seek/flush y EOS se propagan. El PCM se
transporta por socket con escritura no bloqueante y búferes de tamaño limitado.

Hi-Fi muestra Controlado por Yin y oculta los ajustes locales solo después de
confirmarse OPEN. Al fallar el servicio, la sesión se cierra y Yang espera sin
activar audio local mientras Yin siga habilitado. El puente transporta
PCM, no DSD/DoP ni etiquetas ReplayGain en esta primera versión.

Los certificados admitidos se derivan de las firmas de desarrollo existentes
al compilar. Para distribuir otras firmas deben actualizarse juntos los pins y
la firma de cada aplicación; no basta con cambiar los nombres de paquete.
# Transición segura al aplicar efectos

Yin ya no cancela la sesión al guardar un perfil: marca `reconfigure` en STATUS.
Yang sella el envío en un frame PCM completo, sin avanzar ni descartar el siguiente
búfer del decodificador. El PCM aceptado termina con el grafo antiguo; Yin cierra
la entrada de FFmpeg y entrega su cola procesada. Solo tras `drained` se abre una
sesión con los parámetros nuevos. Puede existir silencio de reconstrucción; no
hay una fase de reproducción local sin el preamplificador o los efectos de Yin.

El puente limita a unos 400 ms el PCM pendiente de reproducción y conserva las
escrituras parciales sin partir un frame entre dos grafos. Al reabrir dentro de
un búfer, el reloj incluye el desplazamiento ya consumido de ese búfer. FFmpeg
vacía los paquetes de salida y se añade una cola digital silenciosa no contada
como música para completar el último bloque de AudioTrack.

HELLO informa si el usuario habilitó Yin. Mientras Yin está habilitado o su modo
todavía no se conoce, Yang espera ante un error en vez de abrir su salida local.
La salida local solo se recupera automáticamente después de confirmar que Yin
está desactivado; la espera de error dejó de ser de cinco segundos. Los campos
nuevos son aditivos, pero esta entrega requiere actualizar ambos APK juntos.

Un IR habilitado que desapareció produce un error y silencio, no una omisión
automática del efecto. No se cambiaron el volumen del teléfono, los ajustes de
ganancia física ni los perfiles elegidos por el usuario.

Esto corrige el cambio de sesión y el respaldo sin efectos. Los filtros siguen
reconstruyéndose: todavía no es un DSP con actualización continua de coeficientes,
ni se garantiza una transición de 0,3 s. La entrega se verifica con compilación
release/R8 y revisión de las rutas de error, sin pruebas audibles con auriculares.


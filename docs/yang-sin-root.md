# Yang sin root: límites y posibilidades en el LG V60

Consulta del 1 de noviembre de 2026. Root se utilizó exclusivamente para leer
diagnósticos; Yang no incorpora llamadas a su, hooks ni modificaciones de HAL.

## Qué significa cada cifra

Solicitar PCM a Android no demuestra que llegue así al DAC. Hay tres formatos
distintos: archivo original, flujo abierto por Yang y salida real de Android/HAL.
Float32 y PCM32 son contenedores de trabajo; no garantizan 32 bits efectivos en
el convertidor. Remuestrear un archivo tampoco añade información al original.

| Salida | Yang actual sin root | Techo o posibilidad adicional | Confirmación pendiente |
| --- | --- | --- | --- |
| Altavoz | Solicitud hasta 48 kHz, PCM16 | La política del puerto Speaker y los mezcladores consultados indican 48 kHz/16 bits. Mantener ese perfil. | No extrapolar esto como límite físico universal de todos los altavoces. |
| Bluetooth | Solicitud hasta 96 kHz, PCM24; alternativa PCM16 | LDAC admite contenido 96 kHz/24 bits. El enlace lo negocian Android y el auricular; la aplicación no puede garantizar ni forzar todos sus parámetros con APIs públicas. | Comprobar códec, formato del mezclador y ruta activa a la vez. Que el enlace diga LDAC 24/96 no demuestra una cadena íntegra a 24/96. |
| AUX / Quad DAC interno | Solicitud hasta 192 kHz; PCM16/24/32 o Float32 | La copia de política anterior al módulo BT96 ya declara direct_pcm hasta 192 kHz/16 bits y hasta 384 kHz en contenedores de 24/32 bits, incluido 352,8. Es un candidato de hardware/política, no un máximo conseguido por Yang. | Abrir una ruta realmente directa desde una app sin privilegios y comprobarla. El motor actual limita tanto DSP como salida nativa a 192 kHz. No ofrecer 384 como si ya funcionara. |
| DAC USB externo | Solicitud hasta 192 kHz y contenedores hasta 32 bits | La política USB declara capacidades dinámicas. El límite depende del DAC, descriptores, driver y ruta. Un controlador USB propio podría ampliar lo disponible sin root, con permiso USB del usuario. | No hay DAC conectado: no se puede fijar un máximo del dispositivo ni validar un controlador. |

Estos son límites de esta implementación y evidencia de esta ROM, no una promesa
para todos los teléfonos Android.

## Particularidades del teléfono consultado

- Android 13, LM-V600, kernel instalado `4.19.325-v60-dop-lab3+`.
- Existe el módulo `zero_music_bt96`: añade `zero_bt96_direct` a 96 kHz en
  PCM24/32. Esa ruta no pertenece a una instalación stock sin modificaciones.
- Se leyó su `original-policy.xml`, anterior a la adición de BT96. Coincide en
  los perfiles direct_pcm de alta frecuencia con el archivo histórico
  `MP3_player/diagnostics/stock13-audio-policy.txt`. No se desinstalaron módulos
  ni se reinició el teléfono para simular un sistema stock.
- MMAP anuncia 48 kHz/16 bits. AAudio exclusivo no equivale a una ruta Hi-Res
  de 192/384 kHz; puede rechazarla o terminar en modo compartido.
- Bluetooth QTI declara PCM16 a 44,1/48/88,2/96 kHz en sus puertos A2DP;
  el puerto A2DP de la política primaria declara PCM16/48. No usar el máximo
  teórico del códec para afirmar que la alimentación PCM sea 24/96.
- USB device/headset tienen perfiles dinámicos. USB accessory 16/44,1 es otra
  modalidad: no confundirla con un DAC USB conectado como dispositivo.

## Rutas que podemos aprovechar sin root

1. **AudioTrack:** PCM16/24/32/float según aceptación, selección preferida del
   dispositivo y consulta del dispositivo realmente enrutado. Consultar
   `AudioManager.getDirectPlaybackSupport` en API 33 o
   `AudioTrack.isDirectPlaybackSupported` en API 29–32 permite detectar soporte
   anunciado; no obliga a que una pista cualquiera abra esa ruta.
2. **AAudio:** modo compartido o exclusivo solicitado, verificación del modo y
   formato realmente abiertos y alternativa compatible. Exclusividad es una
   solicitud que Android puede cambiar. La frecuencia del stream no certifica
   por sí sola la frecuencia física de la HAL.
3. **OpenSL ES:** disponible como alternativa de compatibilidad. Yang implementa
   PCM16 hasta 48 kHz. Cambiar de API no aumenta la resolución del dispositivo.
4. **USB sin root:** consultar dispositivos/perfiles; para saltarse el mezclador
   de Android en Android 13, estudiar un controlador USB de usuario con acceso
   autorizado por UsbManager y transferencias isócronas nativas. Todavía no está
   implementado. No se puede anunciar una cifra máxima sin un DAC para probarlo.
5. **Android 14 o posterior:** las APIs públicas de atributos del mezclador USB
   permiten consultar configuraciones y solicitar modo bit-perfect cuando el
   dispositivo lo ofrece. No están disponibles en este V60 con Android 13.

Usar APIs ocultas para introducir flags privados, modificar la política, forzar
mixer/ganancia física o hooks queda fuera del motor sin privilegios de Yang.
Yin mantiene esas capacidades separadas. No se habilita DoP a partir del PCM
decodificado que Yang envía al puente.

## Cambio de salida durante la reproducción

El usuario informa que los backends suenan al abrir la app y pueden quedar mudos
al cambiarlos en caliente. La captura de AudioFlinger conserva una pista de Yang
iniciada, sin frames disponibles, en standby y con numerosos underruns. No prueba
por sí sola cuál de todos los cambios desencadenó el fallo.

La revisión encontró una espera sin límite: antes de reconfigurar, Yang exigía
que la posición consumida alcanzara exactamente todas las muestras entregadas.
Una pista pausada o vaciada parcialmente puede retener el último bloque y nunca
satisfacer esa condición. Se reemplazó por una transición no bloqueante con
presupuesto de 500 ms o dos búferes, el mayor; si no drena, se libera la salida
anterior y se abre la nueva. Un cambio de dispositivo o una pista pausada no
espera un drenaje imposible. El cambio puede descartar la cola residual.

La entrega local→Yin también tiene espera acotada. Yin→local recupera el estado
de reproducción; antes podía conservar el sink local pausado. La alternativa
AudioTrack queda ligada a la configuración fallida, no bloquea para siempre
las selecciones posteriores. OpenSL se inicia después de recibir su primer
bloque. Se añadieron registros de apertura y transición sin guardar canciones,
cuentas ni contenido de audio.

Validación: compilación release R8 y lint vital. No se ejecutaron pruebas de
escucha, cambios automatizados de salida ni señales de prueba. La resolución
funcional del fallo queda pendiente de la comprobación del usuario.

## Fuentes

- [AAudio y modo de compartición](https://developer.android.com/ndk/guides/audio/aaudio/aaudio)
- [Referencia AAudio: formato solicitado y hardware](https://developer.android.com/ndk/reference/group/audio)
- [AudioTrack y reproducción directa](https://developer.android.com/reference/android/media/AudioTrack)
- [AudioManager: consulta de soporte directo](https://developer.android.com/reference/android/media/AudioManager)
- [LDAC, Sony](https://www.sony.co.jp/en/Products/LDAC/info/)
- [USB host y autorización de acceso](https://developer.android.com/develop/connectivity/usb/host)
- [Atributos de mezclador USB desde Android 14](https://source.android.com/docs/core/audio/preferred-mixer-attr)


## Medición Bluetooth autorizada por el usuario

Se probó la canción que el usuario dejó sonando, sin señales sintéticas ni
cambios de volumen, EQ o códec. Dispositivo activo: **QCY MeloBuds N60**,
identificado por Yang y la conexión A2DP activa. Los Edifier emparejados que
aparecen también en el volcado no son la conexión activa de esta medición.
Yin no procesa esta sesión: las pistas corresponden al PID/UID de Yang.

| Motor seleccionado | PCM de la pista de Yang | Salida activa AudioFlinger/HAL | Estado observado |
| --- | --- | --- | --- |
| AudioTrack | 24 bits empaquetados / 96 kHz | MIXER, PCM16 / 48 kHz, A2DP | Activa, 0 underruns en la captura |
| AAudio | 24 bits empaquetados / 96 kHz | MIXER, PCM16 / 48 kHz, A2DP | Activa, 0 underruns en la captura |
| OpenSL ES | PCM16 / 48 kHz | MIXER, PCM16 / 48 kHz, A2DP | Activa, 0 underruns en la captura |
| Yang Hi-Res | 24 bits empaquetados / 96 kHz; AAudio compartido | MIXER, PCM16 / 48 kHz, A2DP | Exclusividad no obtenida; activa, contador de frames avanza |

Android informa LDAC estéreo a 96 kHz, con `mBitsPerSample=32` en su
configuración Bluetooth. Ese campo no demuestra transmisión sin pérdidas ni
32 bits efectivos: existe una conversión previa documentada a PCM16/48 en
la salida activa. No se pudo establecer un bitrate LDAC real a partir de los
contadores disponibles; no afirmar 990 kbps por defecto.

El PCM decodificado de esta canción es 44,1 kHz/Float32. Solicitar 96 kHz en
Yang añade remuestreo, pero no evita el mezclador 48 kHz encontrado. La ruta
root `zero_bt96_direct` no se utilizó. En estas pruebas ningún motor consiguió
una salida HAL directa 24/96 sin privilegios. No representa una búsqueda
exhaustiva de todos los formatos ni mide la salida analógica del auricular.

El usuario confirmó que el cambio de motores volvió a funcionar. La secuencia
AudioTrack → AAudio → OpenSL → Hi-Res también produjo pistas activas en estas
capturas; se restauró AudioTrack / 96 kHz / PCM24 al terminar. La medición AUX
posterior se documenta a continuación.

## Medición AUX autorizada por el usuario

Con auriculares por cable y música sonando, todas las siguientes pistas activas
pertenecen al PID 4886 de Yang; Yin no procesa estas sesiones. Se comparó la
salida abierta con el hilo activo y su dispositivo WIRED_HEADPHONE, excluyendo
los hilos históricos cerrados del volcado.

| Motor / configuración | PCM abierto por Yang | Salida activa AudioFlinger/HAL |
| --- | --- | --- |
| Yang Hi-Res, Float32 / 192 kHz | Float32 / 192 kHz, AAudio compartido | MIXER PCM16 / 48 kHz |
| AudioTrack, Float32 / 192 kHz | Float32 / 192 kHz | MIXER PCM16 / 48 kHz |
| AudioTrack, PCM24 / 192 kHz | PCM24 empaquetado / 192 kHz | MIXER PCM16 / 48 kHz |
| AudioTrack, PCM32 / 192 kHz | PCM32 / 192 kHz | MIXER PCM16 / 48 kHz |
| AAudio, PCM32 / 192 kHz | PCM32 / 192 kHz | MIXER PCM16 / 48 kHz |
| Yang Hi-Res, PCM32 / 192 kHz | PCM32 / 192 kHz, sin exclusividad | MIXER PCM16 / 48 kHz |

Las capturas de esas pistas muestran 0 underruns. No son una prueba prolongada
de estabilidad ni de calidad acústica. La fuente observada se decodifica a
44,1 kHz/Float32: los 192 kHz abiertos son remuestreo de Yang.

Lectura root sin cambiar controles:

- ALSA activo `/proc/asound/card0/pcm9p/sub0/hw_params`: S16_LE, estéreo,
  48000 Hz, periodo 192 frames y búfer 384 frames.
- `TERT_MI2S_RX SampleRate`: selección actual KHZ_48.
- `TERT_MI2S_RX Format`: selección actual S16_LE.
- `Es9018 State`: selección actual Hifi; HEADSET TYPE 1.

ALSA y mixer son datos del sistema, no un identificador de sesión de Yang;
se correlacionan con la única pista musical de Yang activa por cable. No se
interpretaron registros internos del ESS como una medición independiente del
reloj analógico. El icono HiFi y el estado Hifi confirman activación del DAC,
no reproducción de alta frecuencia.

Conclusión: **en las configuraciones probadas, el motor actual de Yang sin
privilegios llega por AUX a una ruta 16/48**, aunque acepta la entrada del
cliente a 192 kHz y hasta 32 bits. Esto no fija un máximo universal sin root,
ni refuta los perfiles direct_pcm de 384 kHz: falta implementar/conseguir y
validar una ruta que evite este mezclador. No se cambiaron políticas, modos de
ganancia, volumen, kernel ni parámetros de hardware para hacer estas pruebas.
Se restauró el perfil AUX inicial: Yang Hi-Res, 192 kHz, Float32, búfer 200 ms.

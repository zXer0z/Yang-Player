# Motor de audio Yang

Media3 conserva la lectura de archivos/streaming, decodificación de los códecs
disponibles en Android, MediaSession, foco, cola y notificaciones. YangAudioSink
reemplaza DefaultAudioSink. No hay una extensión FFmpeg incluida en este build.

El motor PCM propio está en app/src/main/cpp/yang_audio.c. Se compila desde el
código fuente con NDK r27c para arm64-v8a, armeabi-v7a, x86 y x86_64; las librerías
usan alineación ELF de 16 KiB. En Windows, tools/Fetch-Ndk.ps1 prepara el NDK
oficial en .tools/ antes de ejecutar Gradle.

Procesamiento: PCM de 8/16/24/32 bits o float de entrada, downmix mono/estéreo,
remuestreador sinc de 64 coeficientes y 1024 fases con ventana Blackman y filtro
antialias, diez biquads paramétricos, preamplificador, margen automático y
limitador de picos de muestra. Los estados de filtros usan double. Ganancia y
coeficientes se suavizan al cambiar. Se aplica dither triangular al cuantizar
cuando se reduce la precisión o se procesa el audio. El limitador no es un
limitador true-peak con lookahead.

Salidas reales:

- AudioTrack: PCM streaming no bloqueante; negocia frecuencia, contenedor y
  tamaño de búfer antes de configurar el DSP.
- AAudio: escritura no bloqueante, consulta del formato abierto, contador de
  frames, timestamp y xruns. Los formatos I24/I32 requieren Android 12/API 31.
- OpenSL ES: cola AndroidSimpleBufferQueue con almacenamiento propio y callback
  C sin JNI; se limita a 16 bits y hasta 48 kHz. No selecciona un dispositivo
  individual: sigue la ruta multimedia del sistema.
- Yang Hi-Res: política que solicita AAudio exclusivo, con alternativas
  compartida/AudioTrack. No es un cuarto driver del fabricante ni prueba que se
  haya evitado el mezclador/HAL. El estado muestra el backend realmente abierto.

Perfiles persistentes: altavoz hasta 48 kHz/16 bits; Bluetooth hasta 96 kHz/24
bits; jack/AUX y USB hasta 192 kHz/32 bits o float. Son límites de la aplicación
solicitados por el usuario, no máximos físicos universales. El PCM abierto por
la app no verifica el formato interno del DAC ni el códec negociado por Bluetooth.
No se utilizan root, hooks, módulos LSPosed, cambios de HAL ni modificaciones de
firmware. EQ, preamplificador y remuestreo modifican las muestras originales;
no se anuncia bit-perfect. Velocidad/pitch y salto de silencio se mantienen en
1x/desactivados en este sink.

La referencia MP3_player aportó la política de perfiles, negociación de salida
y configuración de EQ. Su decodificación FFmpeg y sus módulos root no se copiaron.
La validación realizada aquí es de compilación/paquete; las pruebas funcionales
en hardware las realiza el usuario según su preferencia.

Corrección de cambios en caliente: la espera para drenar la salida anterior
ahora está limitada a 500 ms o dos búferes, el mayor. Una pista pausada o un
cambio de dispositivo no espera el drenaje. OpenSL inicia tras su primer bloque.
La alternativa AudioTrack se aplica solo a la configuración fallida; cambiar
de configuración permite intentar el nuevo motor. Al volver desde Yin se
recupera explícitamente el estado play/pause de la salida local.

Consulta de límites y diferencias entre formato solicitado y salida efectiva:
[Yang sin root](yang-sin-root.md). La política de este teléfono incluye módulos
anteriores; su presencia no demuestra acceso directo en un sistema stock.

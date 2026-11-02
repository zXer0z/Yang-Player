# Funciones de biblioteca, reproducción e identidad de Yang/Yin

## Funciones incorporadas

- Aleatorio y repetición desactivada / cola / canción. Los tres controles de reproducción (aleatorio, repetición y temporizador) están en el menú de los tres puntos del player. La sesión Media3 conserva los modos. Los botones siguiente/anterior respetan el orden de reproducción y ya no reinician la cola automáticamente al llegar a su fin con repetición desactivada.
- Temporizador de 15, 30, 45, 60 o 90 minutos y pausa al terminar la canción actual. Vive junto al servicio de audio y usa tiempo monotónico. La opción al terminar utiliza pauseAtEndOfMediaItems; elegir otra pista manualmente cancela esa opción. Se puede cancelar desde el mismo menú. Se cancela si termina el servicio de audio, sin restaurar un temporizador viejo al reiniciar la app.
- Información de Wikipedia en álbumes y artistas. Se reutiliza la búsqueda conservadora de Zero (nombre coincidente, contexto musical, artista acreditado para álbumes y descarte de desambiguaciones). Español primero, inglés después. La consulta es asíncrona y no bloquea la biblioteca. Caché positiva de 30 días y negativa de un día, hasta 150 archivos. El extracto se presenta sin tarjeta ni encabezado, con cuatro líneas como máximo. Leer más revela únicamente la primera frase completa, hasta el punto; Leer menos vuelve a contraerla. El botón y la fila compacta de Wikipedia y CC BY-SA 4.0 quedan centrados; el párrafo también queda centrado. El enlace de Wikipedia permite consultar el artículo y sus autores.
- Estadísticas en Tú > Estadísticas: tiempo escuchado, reproducciones, canciones y rankings de canciones/artistas. Los datos anteriores se conservan. El registro de tiempo pasa al servicio para funcionar en segundo plano sin doble contabilización desde el controlador de UI. Pausas y buffering no cuentan. Una reproducción se reconoce a los 30 segundos o a la mitad de una pista corta. Los totales reflejan el historial retenido (hasta 1500 pistas); los rankings reconocen nombres de canciones disponibles en la biblioteca o el catálogo online guardado. No se fabrican periodos diarios de datos anteriores.

## Corrección de favoritos

Se identificó una ClassCastException real (Integer no convertible a Long) en el comparador de favoriteSongs: lastPlayedAt usaba un cero Int al faltar historial. El valor alternativo ahora es 0L para que todas las comparaciones sean Long. El arreglo se compiló e instaló primero antes de continuar con las funciones.

## HiFi

La onda decorativa reemplaza el icono de cabecera duplicado y responde al estado de reproducción; no representa un análisis real del audio. Su geometría está precalculada con drawWithCache y la animación mueve una capa gráfica, en lugar de recrear caminos y trigonometría cada fotograma. Las secciones tienen un fundido breve. Se conserva el carrusel de perfiles de salida.

## Logos

Iconos originales de una familia común inspirada en yin-yang: Yang claro/ámbar; Yin oscuro/azul. Recursos vectoriales adaptativos y monocromáticos para Android, sin cambios en IDs de paquete, certificados ni configuración de audio. Las fuentes SVG, PNG y la comparación están en docs/assets.

## Validación

Compilación release de ambos APK con R8, verificación de firmas e instalación en el LG V60. Consulta pública a Wikipedia para verificar los campos de búsqueda/extractos. Revisión de arranque y presentación. No se ejecutaron pruebas automatizadas ni se eliminaron canciones. Las pruebas funcionales de escucha, repetición y temporizador las realiza el usuario.

Referencias: https://www.mediawiki.org/wiki/Extension:TextExtracts y https://developer.android.com/media/media3/exoplayer/playlists.

## Agrupación de artistas y álbumes

La biblioteca comparte un índice para listados, detalles y colas. Las colaboraciones con créditos reconocibles aparecen en cada artista participante, sin repetir la misma pista dentro de su página. Se normalizan espacios, mayúsculas y Unicode. Los separadores ambiguos requieren evidencia en la biblioteca; los nombres compuestos de bandas se conservan cuando no se puede distinguir una colaboración. Los créditos originales de cada canción no cambian.

Los álbumes usan el título y la etiqueta de artista del álbum; si falta esta última, se usa el artista principal reconocido. Así Bylaw - EP reúne sus cinco canciones bajo Martin Garrix sin mezclar discos del mismo nombre de otros artistas. MediaStore aporta la etiqueta en Android 11 o superior; las carpetas SAF la leen del audio y actualizan la caché anterior. Se conserva también en playlists y sesiones.

Las flechas de volver quedan fuera de las listas, visibles al desplazar. Abrir un álbum o artista utiliza una única ruta de detalle y conserva la posición de la lista al volver. El margen inferior reserva el espacio del dock expandido para evitar cambios de geometría durante su animación. La agrupación del listado se calcula fuera del hilo de interfaz y solo al cambiar la biblioteca.

## Conexión visual con Yin

HiFi reconoce la conexión confirmada por el protocolo, incluso antes de abrir una canción. Una introducción de 2,4 segundos fusiona dos mitades con forma de yin-yang y la misma curva S de los iconos. Cada pieza gira hasta 260 grados y recorre una órbita curva que se cierra en aproximadamente un segundo, con una ligera transición de escala antes de encajar. El símbolo completo muestra YIN · YANG MUSIC; después aparece Yin conectado con un brillo que recorre las letras de izquierda a derecha en un loop de 2,1 segundos mientras Yin permanece conectado y el logo se funde con el espectro. La animación lee su progreso al dibujar, sin recomponer toda la pantalla en cada fotograma. El estado de espera o desconexión conserva su mensaje propio.

Mientras la pantalla permanece visible y la canción reproduce, un visual de ondas usa 24 bandas logarítmicas del PCM decodificado (30 Hz a 20 kHz, limitado por Nyquist). FFT de 2048 muestras con ventana Hann y solapamiento cada 512 muestras, fuera del hilo de audio. El consumidor busca ventanas cada 16 ms (hasta aproximadamente 60 actualizaciones por segundo, según carga). Ataque de 9 ms y caída de 35 ms al dibujar, independientes de la tasa de refresco, para seguir transitorios de música rápida. Un anillo y dos ventanas preasignadas limitan memoria y trabajo: si están ocupadas, se descartan datos visuales. La lectura usa índices absolutos y solo bytes consumidos; no modifica PCM ni buffers ni requiere micrófono. Se suspende al pausar, salir de HiFi o pasar la actividad al fondo.

Representa el contenido de la canción antes de los efectos y no mide el DAC ni la salida procesada por Yin. Puede adelantarse ligeramente al sonido por el búfer de reproducción. La onda pequeña de la pantalla del motor local continúa siendo decorativa.

La tarjeta de canción en HiFi muestra solo frecuencia y formato de la salida de Yin; los diagnósticos PCM DIRECT/HAL/ALSA se consultan en Yin. El brillo del estado se anima en un componente de texto independiente, y se elimina cuando la conexión deja de estar lista.

## Player vinilo con Yin

Cuando el protocolo confirma que Yin está listo, el player sustituye la carátula cuadrada por un disco circular giratorio, con surcos y un centro vectorial yin-yang de aro metálico y orificio. Al pausar conserva su ángulo y reanuda desde ahí. El paginador y sus gestos para cambiar de canción se conservan. Solo la página actual anima el disco y recibe espectro; las vecinas permanecen estáticas. Al desconectar Yin vuelve la carátula rectangular.

Tres cintas de ondas anchas, redondeadas y desfasadas rodean el disco. Se extraen hasta tres familias de tono representativas de la carátula antes del desenfoque ambiental, evitando tonos casi iguales. Las carátulas de un solo tono usan variaciones de ese tono y las neutras usan grises. No hay una capa azul fija. La energía de 32 bandas y los ataques de graves controlan su expansión; una deriva lenta de fase da continuidad al movimiento de las capas. Es un visual reactivo del PCM anterior a los efectos de Yin, no una medición del DAC.

El fondo del player aumenta suavemente su color con los ataques de graves medidos hasta 220 Hz, relativos al nivel reciente de la canción, y decae después de cada golpe. Las barras de volumen y progreso conservan sus gestos y muestran un brillo de izquierda a derecha en sus porciones activas; se suspende al arrastrar cada barra.

HiFi y el player comparten un único consumidor FFT mediante solicitudes de observación para evitar análisis duplicados al abrir el modal sobre HiFi. La actividad en segundo plano suspende el análisis y el dibujo animado. En Android 10 o superior, mallas trianguladas preasignadas evitan teselar curvas rellenas y contornos gruesos a cada fotograma. Android 8/9 conserva un dibujo compatible. El gradiente del fondo reactivo se mantiene y su capa cambia de opacidad al pulso.

Diagnóstico en el LG V60: la muestra inicial del player registró mediana de 23 ms y aproximadamente 55 % de fotogramas fuera de plazo. Tras las mallas, la primera muestra bajó a aproximadamente 17 %. La versión final elimina también la deriva del fondo base mientras Yin está conectado y cierra las fases sin salto al completar la vuelta. En una muestra final de unos 10,2 segundos reproduciendo Melody se renderizaron 614 fotogramas, con 2 fuera de plazo (0,33 %) y mediana de 14 ms: aproximadamente 60 FPS durante esa muestra. Son mediciones de uso real, no una garantía de tasa constante ni un benchmark controlado. No se ejecutaron pruebas automatizadas; el usuario realiza la validación funcional de escucha y gestos.

Referencia técnica: https://developer.android.com/topic/performance/hardware-accel.

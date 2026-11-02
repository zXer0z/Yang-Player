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

# Búsqueda, transición del reproductor, HiFi y cuenta

La búsqueda local usa una sola decoración de BasicTextField con el texto de ayuda y el campo centrados y con la misma altura de línea. El teclado ofrece la acción Buscar.

El movimiento de las carátulas lee el desplazamiento del pager en graphicsLayer, sin recomponer su contenido por cada fotograma. Los títulos se desplazan con el mismo pager, en lugar de arrancar una segunda animación cuando llega el estado de reproducción. Al seleccionar otra pista de la cola ya preparada, se cambia el índice del MediaController sin reconstruir todos los MediaItems. La navegación siguiente/anterior reutiliza ese flujo. Solo se reconstruye la lista cuando el controlador no contiene la cola esperada. Las paletas se guardan en una caché de 24 entradas y el color de acento ya no recompone toda la app durante su animación. El fondo conserva su animación durante el dibujo.

HiFi tiene un carrusel de perfiles: Altavoz, Bluetooth, Jack/AUX y USB. Cada tarjeta identifica la salida activa o un perfil guardado; deslizar selecciona el perfil a editar, sin forzar la conexión física. Se quitaron los chips de rutas duplicados. Sonido agrupa preamp/protecciones/EQ; Salida agrupa backend, frecuencia, PCM y búfer; Detalles conserva la telemetría disponible. Cuando Yin administra el audio se muestra su estado y el acceso a sus controles.

Cuenta separa los estados conectado y sin conectar, presenta el perfil y las ventajas de la conexión, distingue Actualizar perfil de Cerrar sesión y solicita confirmación antes de desconectar. No se cambia la implementación de autenticación.

Prioridades propuestas para una siguiente etapa, aún no implementadas: presets guardables y comparación A/B; ReplayGain de pista/álbum con margen; frecuencia de salida siguiendo el archivo donde la ruta lo permita; medidores de picos/clipping con muestras reales. Los efectos de Yin deben exponer sus controles mediante el puente antes de duplicarlos en Yang.

Validación: compilación release con R8; firma del APK; instalación en el LG V60; revisión visual de la búsqueda y las pantallas. No se ha medido la tasa de fotogramas ni se ha hecho una prueba audible de los cambios de pista.

Referencias de implementación: https://developer.android.com/develop/ui/compose/performance/bestpractices y https://developer.android.com/media/media3/exoplayer/playlists.

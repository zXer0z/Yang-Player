# Completar datos faltantes en Yang

Desde el menú de una canción local, abrir **Completar datos faltantes**.

- **Datos:** buscar en el catálogo público de Apple, revisar la versión y completar título, artista o álbum cuando estén vacíos o marcados como desconocidos. Los títulos deducidos del nombre del archivo se conservan.
- **Carátula:** buscar y previsualizar una imagen del catálogo de Apple. Solo se guarda si la canción no tiene una carátula que Yang pueda leer.
- **Letras:** buscar en LRCLIB, revisar la letra sincronizada o de texto y guardar si no hay letra disponible. También se puede entrar desde **Buscar letras en Internet** en la pantalla de letras vacía.

La búsqueda se inicia al pulsar Buscar; se envía la consulta escrita (canción y artista), sin subir audio ni rutas del dispositivo. Si la duración difiere más de tres segundos o falta, hay que confirmar la versión antes de guardar. No se aplica automáticamente la primera coincidencia.

Los cambios se guardan en el almacenamiento privado de Yang: preferencias para título/artista/álbum, archivos para carátulas y letras. No se escriben etiquetas en el audio original. Permanecen al reabrir la app o reescanear, mientras se conserve la misma URI del archivo. Borrar los datos de Yang o desinstalar la app elimina estos complementos. Esta versión opera canción por canción; no rellena álbumes completos en lote ni sustituye campos que ya están completos.

La biblioteca, las recomendaciones, las playlists y la cola aplican los complementos. Al guardar se actualizan los metadatos de la sesión sin cambiar su URI, formato de audio ni posición. Las carátulas descargadas tienen prioridad para el color del reproductor. Las letras guardadas usan el lector y el reloj de sincronización existentes.

Las peticiones HTTPS tienen límites de tamaño y tiempo, cancelación y validación del dominio en cada redirección. Una respuesta vacía, un error de red o un límite de solicitudes deja los datos existentes intactos.

Fuentes: https://lrclib.net/docs y https://performance-partners.apple.com/search-api.

Validación de desarrollo: compilación release con R8; consulta pública a ambas fuentes para comprobar el esquema de sus respuestas. Las pruebas funcionales con canciones del teléfono quedan pendientes.

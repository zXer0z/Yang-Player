# Borrado de canciones y pantalla de completar datos

El menú de canciones locales ahora conecta todas sus entradas al mismo controlador de borrado. Antes, el callback predeterminado estaba vacío.

La app muestra una confirmación con la canción seleccionada. En Android 11 o posterior, los archivos de MediaStore usan createDeleteRequest: Android pide confirmación y ejecuta la eliminación permanente antes de devolver RESULT_OK. Los documentos con una URI equivalente de MediaStore siguen este mismo flujo. Los documentos restantes usan DocumentsContract.deleteDocument cuando su proveedor admite borrado. Android 10 también contempla RecoverableSecurityException y reintenta después de obtener autorización. Cancelar la confirmación conserva el archivo y las referencias.

Solo después de que Android o el proveedor confirma el borrado se retira la canción de la biblioteca, las playlists, los favoritos, la cola y la sesión guardada. Borrar la última canción también limpia el punto de restauración para que no reaparezca al reiniciar. Las entradas online no ofrecen eliminación de archivo. No se usan root, papelera ni comandos de borrado externos.

Completar datos presenta pantallas separadas: elegir qué completar, buscar coincidencias, revisar la versión y confirmar el guardado. La acción principal permanece visible al pie. Las carátulas se ven en los resultados; las letras tienen una vista previa breve. Se mantienen la validación de duración y la conservación de datos existentes.

Validación: compilación release y firma del APK. No se eliminan canciones del teléfono durante la validación. Falta la prueba funcional manual de borrado y de la nueva interfaz.

Referencia: https://developer.android.com/reference/android/provider/MediaStore#createDeleteRequest(android.content.ContentResolver,%20java.util.Collection%3Candroid.net.Uri%3E)

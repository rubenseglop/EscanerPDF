# Escáner PDF

Aplicación Android gratuita y sin publicidad para digitalizar documentos y gestionarlos en PDF. Los documentos permanecen en el dispositivo.

## Funciones

- Escaneo multipágina (hasta 50 páginas por documento) con detección de bordes, corrección de perspectiva, rotación y filtros mediante Google ML Kit Document Scanner.
- Importación de imágenes desde la galería.
- Historial local de documentos PDF.
- Vista previa integrada con navegación por páginas.
- Renombrar, eliminar, compartir e imprimir (mediante una aplicación compatible con PDF).
- OCR en español y otros idiomas latinos mediante ML Kit: reconoce el texto de todas las páginas y lo guarda junto al PDF.
- Búsqueda por nombre o por el texto reconocido de los documentos.
- OCR manual o automático después de cada escaneo.
- Tema claro/oscuro.
- Sin cuentas, anuncios ni servidores propios.

## Requisitos

- Android 10 (API 29) o superior.
- Google Play Services actualizado. El escáner y los modelos de ML Kit pueden descargar componentes la primera vez que se utilizan.

## Tecnología

- Kotlin y Jetpack Compose.
- Google ML Kit Document Scanner.
- Google ML Kit Text Recognition.
- Android PdfRenderer y FileProvider.

## Datos y privacidad

Los PDF y los archivos de texto generados por OCR se guardan en el almacenamiento privado de la aplicación. Al desinstalar la aplicación, Android elimina esos datos. Comparte o exporta los documentos que quieras conservar fuera de la aplicación.

## Compilación

El proyecto utiliza Gradle y GitHub Actions. Cada versión debe incrementar `versionCode` y actualizar `versionName`. Se conserva el keystore estable del proyecto para permitir instalar las actualizaciones sobre la versión anterior.

## Licencia

MIT.

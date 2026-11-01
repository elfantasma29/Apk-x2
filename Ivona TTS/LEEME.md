# Ivona TTS

Motor de texto a voz para Android basado en el motor antiguo IVONA, que corre en teléfonos de 64 bits.
Paquete: com.ivona.tts. Solo arm64-v8a. Voces en español: Conchita (es-ES) y Penélope (es-US).

## Cómo funciona
- El motor original de IVONA es de 32 bits. La app lleva el emulador `libqemu.so` (el mismo de VocalizerEx2 TTS) y, dentro de `assets/guest.zip`, un servidor `ivona_server` de 32 bits con las bibliotecas mínimas.
- El servidor carga el proxy original de IVONA con un JNI simulado, carga la voz y habla por entrada y salida estándar con las órdenes `SPEAK`, `STOP` y `QUIT`.
- Cada voz es un zip aparte con `lib/` y `data/`. La app lo descarga, lo descomprime en su carpeta privada y lo borra.

## Voces de prueba
Están en la carpeta `voces`, partidas en trozos de 60 MB porque GitHub no admite archivos de más de 100 MB: `ivona_conchita.zip.001` y `.002`, `ivona_penelope.zip.001` y `.002`. La app los baja y los une al instalar. Son temporales, solo para pruebas.
En la app también está "Instalar desde un archivo zip", para no gastar datos. Ahí se usa el zip completo; para unir los trozos: `cat ivona_conchita.zip.0* > ivona_conchita.zip`.

## Compilar
`servidor/compilar.sh` compila el servidor de 32 bits (necesita `arm-linux-gnueabihf-g++`) y `servidor/ivona_server` va dentro de `app/src/main/assets/guest.zip`. La app se compila con Gradle. La firma usa las variables de entorno `IVONA_KS` (ruta del almacén) e `IVONA_KS_PASS` (contraseña); la clave no está en el repositorio.

## Pendiente
- Probar en un teléfono real de 64 bits.
- Velocidad, tono y volumen.
- Enrique y Miguel (no se encontraron sus archivos).

## Avisos
`mensajes.json` es el archivo de avisos que lee la app al abrirse y al entrar en Avisos. Cada mensaje lleva `id` (número creciente), `fecha`, `titulo`, `texto` e `importante`. La app muestra los 5 más recientes; para dejar solo dos o uno basta con quitar los demás del archivo. Un `id` mayor que el último visto se marca como nuevo y muestra un diálogo una sola vez.

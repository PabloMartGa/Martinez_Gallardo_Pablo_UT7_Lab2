package com.isengard.pergaminos_ciudadela_blanca

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

 /*
   Pergamino confidencial -> memoria interna (openFileOutput / openFileInput).
   Mapa de campaña        -> memoria externa privada (getExternalFilesDir).
 */
class MainActivity : AppCompatActivity() {

    // Vistas de la pantalla
    private lateinit var etNombre: EditText
    private lateinit var etContenido: EditText
    private lateinit var rbInterno: RadioButton
    private lateinit var tvEstado: TextView
    private lateinit var tvResultado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enlazamos las vistas del XML con las variables
        etNombre = findViewById(R.id.etNombre)
        etContenido = findViewById(R.id.etContenido)
        rbInterno = findViewById(R.id.rbInterno)
        tvEstado = findViewById(R.id.tvEstado)
        tvResultado = findViewById(R.id.tvResultado)

        val btnGuardar = findViewById<Button>(R.id.btnGuardar)
        val btnLeer = findViewById<Button>(R.id.btnLeer)
        val btnEjemplo = findViewById<Button>(R.id.btnEjemplo)

        btnGuardar.setOnClickListener { guardarPergamino() }
        btnLeer.setOnClickListener { leerPergamino() }
        btnEjemplo.setOnClickListener { cargarEjemplo() }
    }


    // === BOTONES ===

    // Guarda el pergamino en interno o externo según el RadioButton marcado
    private fun guardarPergamino() {
        // Si el nombre no es válido, validarNombre() ya avisa y devuelve null
        val nombre = validarNombre() ?: return

        val texto = etContenido.text.toString()
        if (texto.isBlank()) {
            etContenido.error = "El pergamino está vacío"
            avisar("Escribe algo antes de guardar")
            return
        }

        try {
            val ruta = if (rbInterno.isChecked) {
                guardarInterno(nombre, texto)
            } else {
                guardarExterno(nombre, texto)
            }
            tvEstado.text = "Guardado en: $ruta"
            avisar("Pergamino guardado")

            // Limpiamos los campos para el siguiente pergamino
            limpiarCampos()
        } catch (e: IOException) {
            // Cualquier fallo de escritura
            avisar("No se pudo guardar: ${e.message}")
        }
    }

    // Lee el pergamino con el nombre indicado y lo muestra en pantalla
    private fun leerPergamino() {
        val nombre = validarNombre() ?: return

        try {
            val texto = if (rbInterno.isChecked) {
                leerInterno(nombre)
            } else {
                leerExterno(nombre)
            }
            tvResultado.text = texto
            tvEstado.text = "Leído: $nombre"
            avisar("Pergamino leído")

            // Dejamos el nombre en blanco, el texto leído queda en tvResultado
            limpiarCampos()
        } catch (e: FileNotFoundException) {
            // El pergamino todavía no existe
            tvResultado.text = ""
            etNombre.error = "Ese pergamino no existe"
            avisar("No existe ningún pergamino llamado $nombre")
        } catch (e: IOException) {
            avisar("No se pudo leer: ${e.message}")
        }
    }

    // Rellena los campos con el reporte de ejemplo del enunciado
    private fun cargarEjemplo() {
        etNombre.setText("reporte_oeste")
        etContenido.setText(
            """
            [REPORTE TÁCTICO - SECTOR OESTE]

            Estado del Perímetro: Avistadas patrullas de exploración en las lindes de Ithilien.

            Acción Recomendada: Reforzar la vigilancia en los vados del río Anduin y doblar turnos.

            Firmado: Faramir, Capitán de Gondor.
            """.trimIndent()
        )
    }


    // === ALMACENAMIENTO INTERNO ===

    /*
      Escribe el texto en la memoria interna de la app (carpeta files/).
      ODE_PRIVATE: solo esta app puede leer el fichero y se sobrescribe si ya existe.
      .use { } cierra el flujo solo, aunque haya una excepción.
      Devuelve la ruta del fichero para mostrarla en pantalla.
    */
    private fun guardarInterno(nombre: String, texto: String): String {
        openFileOutput(nombre, Context.MODE_PRIVATE)
            .bufferedWriter(Charsets.UTF_8)
            .use { escritor ->
                escritor.write(texto)
            }
        return File(filesDir, nombre).absolutePath
    }

    /*
      Lee un fichero de la memoria interna.
      Si no existe, openFileInput lanza FileNotFoundException
    */
    private fun leerInterno(nombre: String): String {
        return openFileInput(nombre)
            .bufferedReader(Charsets.UTF_8)
            .use { lector ->
                lector.readText()
            }
    }



    // === ALMACENAMIENTO EXTERNO PRIVADO ===


    // Para escribir la memoria tiene que estar montada con permiso de lectura y escritura
    private fun puedeEscribirExterno(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    // Para leer vale montada normal o montada en solo lectura
    private fun puedeLeerExterno(): Boolean {
        val estado = Environment.getExternalStorageState()
        return estado == Environment.MEDIA_MOUNTED ||
                estado == Environment.MEDIA_MOUNTED_READ_ONLY
    }

    // Devuelve la carpeta Documents propia de la app en el almacenamiento externo
    private fun carpetaDocumentos(): File {
        return getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: throw IOException("No hay carpeta externa disponible")
    }

    // Guarda el mapa en .../Android/data/<paquete>/files/Documents/
    private fun guardarExterno(nombre: String, texto: String): String {
        // 1. Comprobamos el estado de la memoria antes de escribir
        if (!puedeEscribirExterno()) {
            throw IOException("La memoria externa no está disponible para escribir")
        }

        // 2. Si la carpeta Documents todavía no existe, la creamos
        val carpeta = carpetaDocumentos()
        if (!carpeta.exists()) {
            carpeta.mkdirs()
        }

        // 3. Escribimos el fichero y se cierra con .use
        val fichero = File(carpeta, nombre)
        fichero.bufferedWriter(Charsets.UTF_8).use { escritor ->
            escritor.write(texto)
        }
        return fichero.absolutePath
    }

    // Lee un mapa de la carpeta Documents externa
    private fun leerExterno(nombre: String): String {
        // 1. Comprobamos que se puede leer
        if (!puedeLeerExterno()) {
            throw IOException("La memoria externa no está disponible para leer")
        }

        // 2. Comprobamos que el fichero existe antes de abrirlo
        val fichero = File(carpetaDocumentos(), nombre)
        if (!fichero.exists()) {
            throw FileNotFoundException("No existe $nombre")
        }

        // 3. Lo leemos y se cierra con .use
        return fichero.bufferedReader(Charsets.UTF_8).use { lector ->
            lector.readText()
        }
    }



    // === AUXILIARES ===

    /*
      Comprueba el nombre escrito.
      Devuelve el nombre listo para usar (con la extensión .txt) o null si no es válido.
    */
    private fun validarNombre(): String? {
        var nombre = etNombre.text.toString().trim()

        if (nombre.isEmpty()) {
            etNombre.error = "Escribe el nombre del pergamino"
            avisar("El nombre no puede estar vacío")
            return null
        }

        // openFileOutput no admite rutas, solo el nombre del fichero
        if (nombre.contains("/") || nombre.contains("\\")) {
            etNombre.error = "El nombre no puede llevar barras"
            avisar("Nombre no válido")
            return null
        }

        if (!nombre.endsWith(".txt")) {
            nombre += ".txt"
        }
        return nombre
    }

    // Vacía los campos
    private fun limpiarCampos() {
        etNombre.text.clear()
        etContenido.text.clear()
        etNombre.error = null
        etContenido.error = null
    }

    // Muestra un mensaje corto en pantalla
    private fun avisar(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }
}
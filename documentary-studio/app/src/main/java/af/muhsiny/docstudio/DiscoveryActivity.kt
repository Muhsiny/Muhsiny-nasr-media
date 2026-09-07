package af.muhsiny.docstudio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class DiscoveryActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 15, 20)
        window.navigationBarColor = Color.rgb(10, 15, 20)
        setContentView(TextView(this).apply {
            text = "استدیوی مستند\n\nدر حال پیدا کردن موتور شخصی در وای‌فای…"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(10, 15, 20))
        })
        discover()
    }

    private fun discover() {
        io.execute {
            val url = try {
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    sock.soTimeout = 3500
                    val msg = "DOCSTUDIO_DISCOVER".toByteArray(Charsets.UTF_8)
                    sock.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), 8187))
                    val buf = ByteArray(256)
                    val packet = DatagramPacket(buf, buf.size)
                    sock.receive(packet)
                    val body = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val parts = body.split("|")
                    if (parts.size >= 3 && parts[0] == "DOCSTUDIO") {
                        "http://${packet.address.hostAddress}:${parts[2]}"
                    } else null
                }
            } catch (_: Exception) { null }

            if (!url.isNullOrBlank()) {
                getSharedPreferences("docstudio", MODE_PRIVATE).edit().putString("engine", url).apply()
            }
            runOnUiThread {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}

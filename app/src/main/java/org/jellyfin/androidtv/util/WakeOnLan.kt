package org.jellyfin.androidtv.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

/**
 * Wake a sleeping Jellyfin server with a Wake-on-LAN magic packet.
 *
 * The server's MAC address is learned automatically from the ARP table while the server is
 * reachable (works on Android 9 like the Zidoo), or can be entered in the settings.
 */
object WakeOnLan {
	private const val PREFS = "wake_on_lan"
	private val MAC_REGEX = Regex("^([0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}$")

	private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	private fun host(address: String): String? = runCatching { URI(address.trim()).host }.getOrNull()

	@JvmStatic
	fun isValidMac(mac: String?) = mac != null && MAC_REGEX.matches(mac.trim()) && normalize(mac) != "00:00:00:00:00:00"

	private fun normalize(mac: String) = mac.trim().replace('-', ':').lowercase()

	@JvmStatic
	fun getMac(context: Context, address: String): String? = host(address)?.let { prefs(context).getString(it, null) }

	@JvmStatic
	fun setMac(context: Context, address: String, mac: String?) {
		val host = host(address) ?: return
		val editor = prefs(context).edit()
		if (mac.isNullOrBlank()) editor.remove(host) else editor.putString(host, normalize(mac))
		editor.apply()
	}

	/** Look the server up in the ARP table (filled by the request we just made) and remember its MAC. */
	suspend fun learnMac(context: Context, address: String) = withContext(Dispatchers.IO) {
		val host = host(address) ?: return@withContext
		val ip = runCatching { InetAddress.getByName(host).hostAddress }.getOrNull() ?: return@withContext
		val mac = runCatching {
			File("/proc/net/arp").readLines().drop(1)
				.map { it.trim().split(Regex("\\s+")) }
				.firstOrNull { it.size >= 4 && it[0] == ip }
				?.get(3)
		}.getOrNull()
		if (isValidMac(mac)) {
			Timber.d("Learned MAC %s for server %s", mac, host)
			prefs(context).edit().putString(host, normalize(mac!!)).apply()
		}
	}

	suspend fun isReachable(address: String, timeoutMs: Int = 2500): Boolean = withContext(Dispatchers.IO) {
		try {
			val connection = URL(address.trimEnd('/') + "/System/Info/Public").openConnection() as HttpURLConnection
			connection.connectTimeout = timeoutMs
			connection.readTimeout = timeoutMs
			try {
				connection.responseCode in 200..499
			} finally {
				connection.disconnect()
			}
		} catch (err: Exception) {
			false
		}
	}

	suspend fun sendMagicPacket(mac: String, address: String) = withContext(Dispatchers.IO) {
		val macBytes = normalize(mac).split(':').map { it.toInt(16).toByte() }.toByteArray()
		val packet = ByteArray(6 + 16 * 6)
		for (i in 0 until 6) packet[i] = 0xFF.toByte()
		for (i in 0 until 16) System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)

		val targets = mutableSetOf("255.255.255.255")
		// Directed broadcast of the server's /24, routers often drop the global broadcast
		host(address)?.let { host ->
			runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()
				?.split('.')?.takeIf { it.size == 4 }
				?.let { targets += "${it[0]}.${it[1]}.${it[2]}.255" }
		}

		DatagramSocket().use { socket ->
			socket.broadcast = true
			for (target in targets) for (port in intArrayOf(9, 7)) {
				runCatching { socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), port)) }
					.onFailure { Timber.w(it, "Magic packet to %s:%d failed", target, port) }
			}
		}
	}

	/**
	 * Send the magic packet and wait until the server answers.
	 * @return true when the server came online within [timeoutMs]
	 */
	suspend fun wakeAndWait(mac: String, address: String, timeoutMs: Long = 90_000): Boolean {
		val end = System.currentTimeMillis() + timeoutMs
		var nextPacket = 0L
		while (System.currentTimeMillis() < end) {
			if (System.currentTimeMillis() >= nextPacket) {
				sendMagicPacket(mac, address)
				nextPacket = System.currentTimeMillis() + 15_000
			}
			if (isReachable(address, 2000)) return true
			delay(3000)
		}
		return false
	}
}

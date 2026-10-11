package org.jellyfin.androidtv.ui.itemdetail

import android.app.AlertDialog
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.Locale

/**
 * Technical details of an item's media source: container, size, bitrate and every video, audio
 * and subtitle stream. Handy to see why something did or didn't direct play on the Zidoo.
 */
object MediaInfoDialog : KoinComponent {
	private val api by inject<ApiClient>()

	@JvmStatic
	fun show(activity: FragmentActivity, itemId: String?, mediaSourceIndex: Int) {
		val id = itemId?.toUUIDOrNull() ?: return
		activity.lifecycleScope.launch {
			val item = withContext(Dispatchers.IO) {
				runCatching { api.userLibraryApi.getItem(itemId = id).content }
					.onFailure { Timber.w(it, "Unable to load media info") }
					.getOrNull()
			}
			if (activity.isFinishing || activity.isDestroyed) return@launch

			val sources = item?.mediaSources.orEmpty()
			if (sources.isEmpty()) {
				Toast.makeText(activity, R.string.media_info_unavailable, Toast.LENGTH_LONG).show()
				return@launch
			}

			val source = sources.getOrNull(mediaSourceIndex) ?: sources.first()
			val padding = (24 * activity.resources.displayMetrics.density).toInt()
			val text = TextView(activity).apply {
				setPadding(padding, padding / 2, padding, padding / 2)
				setText(describe(source))
				setTextIsSelectable(false)
				isFocusable = true
			}
			AlertDialog.Builder(activity)
				.setTitle(item?.name ?: activity.getString(R.string.media_info))
				.setView(ScrollView(activity).apply { addView(text); isFocusable = true })
				.setPositiveButton(android.R.string.ok, null)
				.show()
		}
	}

	private fun describe(source: MediaSourceInfo): CharSequence {
		val out = SpannableStringBuilder()

		fun header(title: String) {
			if (out.isNotEmpty()) out.append("\n")
			val start = out.length
			out.append(title).append("\n")
			out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}

		fun line(label: String, value: Any?) {
			val text = value?.toString()?.takeIf { it.isNotBlank() } ?: return
			out.append("  ").append(label).append(": ").append(text).append("\n")
		}

		header("File")
		line("Version", source.name)
		line("Container", source.container?.uppercase(Locale.ROOT))
		line("Size", source.size?.let { formatSize(it) })
		line("Bitrate", source.bitrate?.let { formatBitrate(it.toLong()) })
		line("Path", source.path)

		val streams = source.mediaStreams.orEmpty()
		streams.filter { it.type == MediaStreamType.VIDEO }.forEachIndexed { index, stream ->
			header(if (index == 0) "Video" else "Video ${index + 1}")
			line("Codec", listOfNotNull(stream.codec?.uppercase(Locale.ROOT), stream.profile).joinToString(" "))
			line("Resolution", if (stream.width != null && stream.height != null) "${stream.width}x${stream.height}${if (stream.isInterlaced) "i" else "p"}" else null)
			line("Frame rate", (stream.realFrameRate ?: stream.averageFrameRate)?.let { String.format(Locale.ROOT, "%.3f fps", it) })
			line("Bit depth", stream.bitDepth?.let { "$it bit" })
			line("Dynamic range", listOfNotNull(stream.videoRange?.toString(), stream.videoRangeType?.toString()).distinct().joinToString(" / "))
			line("Bitrate", stream.bitRate?.let { formatBitrate(it.toLong()) })
		}

		val audio = streams.filter { it.type == MediaStreamType.AUDIO }
		if (audio.isNotEmpty()) {
			header("Audio")
			audio.forEach { out.append("  • ").append(describeAudio(it)).append("\n") }
		}

		val subtitles = streams.filter { it.type == MediaStreamType.SUBTITLE }
		if (subtitles.isNotEmpty()) {
			header("Subtitles")
			subtitles.forEach { out.append("  • ").append(describeSubtitle(it)).append("\n") }
		}

		return out
	}

	private fun describeAudio(stream: MediaStream) = listOfNotNull(
		stream.language?.uppercase(Locale.ROOT),
		stream.codec?.uppercase(Locale.ROOT),
		stream.profile,
		stream.channelLayout ?: stream.channels?.let { "${it}ch" },
		stream.bitRate?.let { formatBitrate(it.toLong()) },
		stream.title,
		"default".takeIf { stream.isDefault },
	).joinToString(" · ")

	private fun describeSubtitle(stream: MediaStream) = listOfNotNull(
		stream.language?.uppercase(Locale.ROOT),
		stream.codec?.uppercase(Locale.ROOT),
		stream.title,
		"forced".takeIf { stream.isForced },
		"default".takeIf { stream.isDefault },
		"external".takeIf { stream.isExternal },
	).joinToString(" · ")

	private fun formatSize(bytes: Long): String {
		val gb = bytes / (1024.0 * 1024 * 1024)
		return if (gb >= 1) String.format(Locale.ROOT, "%.2f GB", gb)
		else String.format(Locale.ROOT, "%.0f MB", bytes / (1024.0 * 1024))
	}

	private fun formatBitrate(bitsPerSecond: Long): String =
		if (bitsPerSecond >= 1_000_000) String.format(Locale.ROOT, "%.1f Mbps", bitsPerSecond / 1_000_000.0)
		else "${bitsPerSecond / 1000} kbps"
}

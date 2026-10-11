package org.jellyfin.androidtv

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.bumptech.glide.Glide
import com.vanniktech.blurhash.BlurHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.acra.ACRA
import org.jellyfin.androidtv.data.eventhandling.SocketHandler
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.telemetry.CrashLog
import org.jellyfin.androidtv.telemetry.TelemetryService
import org.jellyfin.androidtv.util.AutoBitrate
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

@Suppress("unused")
class JellyfinApplication : Application() {

	companion object {
		lateinit var appContext: Context
	}

	override fun onCreate() {
		super.onCreate()

		appContext = applicationContext

		// Remember the last crash locally (wraps the ACRA handler)
		CrashLog.install(this)

		// Don't run in ACRA service
		if (ACRA.isACRASenderServiceProcess()) return

		val notificationsRepository by inject<NotificationsRepository>()
		notificationsRepository.addDefaultNotifications()
	}


	/**
	 * Called from the StartupActivity when the user session is started.
	 */
	suspend fun onSessionStart() {
		val workManager by inject<WorkManager>()
		val autoBitrate by inject<AutoBitrate>()
		val socketListener by inject<SocketHandler>()

		// Everything here runs in the background: withContext() used to wait for the worker
		// rescheduling and the WebSocket connection before the home screen could open.
		val scope = ProcessLifecycleOwner.get().lifecycleScope

		// Update background worker
		scope.launch(Dispatchers.IO) {
			// Cancel all current workers
			workManager.cancelAllWork().await()

			// Recreate periodic workers
			workManager.enqueueUniquePeriodicWork(
				LeanbackChannelWorker.PERIODIC_UPDATE_REQUEST_NAME,
				ExistingPeriodicWorkPolicy.UPDATE,
				PeriodicWorkRequestBuilder<LeanbackChannelWorker>(1, TimeUnit.HOURS)
					.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
					.build()
			).await()
		}

		// Update WebSockets
		scope.launch(Dispatchers.IO) { socketListener.updateSession() }

		// Detect auto bitrate
		// running in a different scope to prevent slow startups
		scope.launch {
//			JellyfinGlideModule.clearDiskCache()
			autoBitrate.detect()
		}
	}

	override fun onLowMemory() {
		super.onLowMemory()

		BlurHash.clearCache()
		Glide.with(this).onLowMemory()
	}

	override fun onTrimMemory(level: Int) {
		super.onTrimMemory(level)

		Glide.with(this).onTrimMemory(level)
	}

	override fun attachBaseContext(base: Context?) {
		super.attachBaseContext(base)

		TelemetryService.init(this)
	}
}

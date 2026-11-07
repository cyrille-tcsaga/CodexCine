package dev.codexsoft.codexcine

import android.app.Application
import com.bumptech.glide.Glide
import dev.codexsoft.codexcine.di.AppContainer
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class CodexCineApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        publishPlayNext()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Glide.get(this).trimMemory(level)
    }

    /**
     * Mirrors the titles in progress on the system home screen. Progress is saved every 10 s while
     * playing: the row only follows it to the minute, so the TV provider is not written to all the time.
     */
    private fun publishPlayNext() {
        val container = container
        container.persistenceScope.launch {
            container.watchProgressRepository.items
                .distinctUntilChangedBy { items ->
                    items.map { it.key to (it.positionSeconds / SECONDS_PER_MINUTE).toInt() }
                }
                .collect(container.watchNextPublisher::publishContinue)
        }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}

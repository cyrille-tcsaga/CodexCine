package dev.codexsoft.codexcine

import android.app.Application
import com.bumptech.glide.Glide
import dev.codexsoft.codexcine.di.AppContainer

class CodexCineApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Glide.get(this).trimMemory(level)
    }
}

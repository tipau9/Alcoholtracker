package de.tipau.promille

import android.app.Application
import de.tipau.promille.di.AppContainer
import de.tipau.promille.repository.DrinkTemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application subclass that initialises the dependency container and seeds
 * the drink catalog on first launch. Declared in AndroidManifest.xml via
 * android:name=".PromilleApplication".
 */
class PromilleApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Seed the drink catalog from assets/drink_catalog.json on first launch.
        // INSERT OR IGNORE makes re-runs safe.
        applicationScope.launch {
            container.drinkTemplateRepository.seedCatalog(this@PromilleApplication)
        }
    }
}

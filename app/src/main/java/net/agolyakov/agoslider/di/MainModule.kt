package net.agolyakov.agoslider.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.agolyakov.agoslider.BuildConfig
import net.agolyakov.agoslider.data.local.AgoSliderPreferences
import net.agolyakov.agoslider.data.local.LanguagePreferences
import net.agolyakov.agoslider.data.repository.FirmwareRepository
import net.agolyakov.agoslider.data.repository.GithubRepository
import net.agolyakov.agoslider.domain.repository.PreferencesRepository
import net.agolyakov.agoslider.domain.usecase.LoadDeviceWithNameUseCase
import net.agolyakov.agoslider.service.bluetooth.BluetoothAdapterProvider
import net.agolyakov.agoslider.service.bluetooth.BluetoothService
import net.agolyakov.agoslider.data.remote.api.GithubApiService
import net.agolyakov.agoslider.data.remote.interceptors.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MainModule {
    private const val owner = "golyakoff"
    private const val repo = "Ago_Slider_ESP32"

    // Region: Context Providers
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    // Region: Bluetooth Dependencies
    @Provides
    @Singleton
    fun provideBluetoothAdapterProvider(
        @ApplicationContext context: Context
    ): BluetoothAdapterProvider = BluetoothAdapterProvider(context)

    @Provides
    @Singleton
    fun provideBluetoothService(
        bluetoothAdapterProvider: BluetoothAdapterProvider
    ): BluetoothService = BluetoothService(bluetoothAdapterProvider)

    // Region: Preferences
    @Provides
    @Singleton
    fun provideAgoSliderPreferences(
        @ApplicationContext context: Context
    ): AgoSliderPreferences = AgoSliderPreferences(context)

    @Provides
    @Singleton
    fun providePreferencesRepository(
        preferences: AgoSliderPreferences
    ): PreferencesRepository = preferences

    @Provides
    @Singleton
    fun provideLanguagePreferences(
        @ApplicationContext context: Context
    ): LanguagePreferences = LanguagePreferences(context)

    // Region: Network
    @Provides
    @Singleton
    fun provideGitHubToken(): String {
        // Optional: empty unless github.token is set in local.properties
        return BuildConfig.GITHUB_TOKEN
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(token: String): AuthInterceptor {
        return AuthInterceptor(token)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideGitHubApiService(retrofit: Retrofit): GithubApiService =
        retrofit.create(GithubApiService::class.java)

    // Region: Repositories
    @Provides
    @Singleton
    fun provideGitHubRepository(
        githubApiService: GithubApiService
    ): GithubRepository = GithubRepository(
        githubApiService = githubApiService,
        owner = owner,
        repo = repo
    )

    @Provides
    @Singleton
    fun provideFirmwareRepository(
        bluetoothService: BluetoothService,
        githubRepository: GithubRepository,
        okHttpClient: OkHttpClient,
        @ApplicationContext context: Context
    ): FirmwareRepository = FirmwareRepository(
        bluetoothService,
        githubRepository,
        okHttpClient,
        context
    )

    // Region: Use Cases
    @Provides
    @Singleton
    fun provideLoadDeviceWithNameUseCase(
        preferencesRepository: PreferencesRepository
    ): LoadDeviceWithNameUseCase = LoadDeviceWithNameUseCase(preferencesRepository)
}
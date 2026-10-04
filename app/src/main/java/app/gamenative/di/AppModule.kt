package app.gamenative.di

import android.content.Context
import app.gamenative.utils.AndroidAppIconFetcher
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AndroidAppIconFetcher.Factory(context))
            }
            .build()
    }
}

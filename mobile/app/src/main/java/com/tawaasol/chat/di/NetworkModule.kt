package com.tawaasol.chat.di

import com.tawaasol.chat.api.JokeApi
import com.tawaasol.chat.data.DefaultJokeRepository
import com.tawaasol.chat.data.JokeRepositoryInterface
import com.tawaasol.chat.datastore.DataStoreManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindRepository(repo: DefaultJokeRepository): JokeRepositoryInterface
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://icanhazdadjoke.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideJokeApi(retrofit: Retrofit): JokeApi = retrofit.create(JokeApi::class.java)
}

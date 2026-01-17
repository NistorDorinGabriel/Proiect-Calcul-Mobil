package com.example.utilitiestracker.data.remote

import retrofit2.http.GET
import retrofit2.http.Url

interface TariffsApi {
    @GET
    suspend fun getTariffs(@Url url: String): TariffsResponse
}

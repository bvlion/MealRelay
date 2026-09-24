package net.ambitious.android.mealrelay.notification.api

import net.ambitious.android.mealrelay.notification.FirebaseInstallationRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Url

interface FirebaseInstallationApi {
  @PUT
  suspend fun register(
    @Url endpoint: String,
    @Header("Authorization") authorization: String,
    @Body request: FirebaseInstallationRequest,
  ): Response<Unit>
}

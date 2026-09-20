package net.ambitious.android.mealrelay

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

class MealRelayBackendClientTest {
  private data class TextBody(val text: String)

  private interface TextApi {
    @POST
    suspend fun send(@Url endpoint: String, @Body body: TextBody): DeviceToken
  }

  @Test
  fun authorizationAndAuthenticatedRequestsShareTypedHttpsTransport() {
    val certificate = HeldCertificate.Builder()
      .commonName("localhost")
      .addSubjectAlternativeName("localhost")
      .build()
    val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
    val clientCertificates = HandshakeCertificates.Builder()
      .addTrustedCertificate(certificate.certificate)
      .build()
    MockWebServer().use { server ->
      server.useHttps(serverCertificates.sslSocketFactory(), false)
      server.start()
      server.enqueue(MockResponse().setBody("""{"token":"issued-token"}"""))
      server.enqueue(MockResponse().setBody("""{"token":"accepted"}"""))
      val client = MealRelayBackendClient(
        readToken = { "device-token" },
        endpoint = server.url("/auth").toString(),
        httpClient = OkHttpClient.Builder()
          .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
          .build(),
      )

      val authorization = runBlocking {
        client.authorization.exchange(server.url("/auth").toString(), AuthorizationCode("google-code"))
      }
      assertEquals("issued-token", authorization.token)
      val authRequest = server.takeRequest()
      assertEquals("/auth", authRequest.path)
      assertEquals("""{"code":"google-code"}""", authRequest.body.readUtf8())
      assertNull(authRequest.getHeader("Authorization"))

      val response = runBlocking {
        client.authenticatedService(TextApi::class.java)
          .send(server.url("/text").toString(), TextBody("food"))
      }
      assertEquals("accepted", response.token)
      val textRequest = server.takeRequest()
      assertEquals("/text", textRequest.path)
      assertEquals("""{"text":"food"}""", textRequest.body.readUtf8())
      assertEquals("Bearer device-token", textRequest.getHeader("Authorization"))
    }
  }
}

package net.ambitious.android.mealrelay.notification.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseInstallationTransportTest {
  @Test
  fun permanentHttpResponsesAreNotRetryable() {
    val exception = registrationFailureFor(400)

    assertEquals(400, exception.statusCode)
    assertFalse(exception.isRetryable)
  }

  @Test
  fun temporaryHttpResponsesAreRetryable() {
    val exception = registrationFailureFor(503)

    assertEquals(503, exception.statusCode)
    assertTrue(exception.isRetryable)
  }

  private fun registrationFailureFor(statusCode: Int): FirebaseInstallationRegistrationException {
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
      server.enqueue(MockResponse().setResponseCode(statusCode))
      val transport = FirebaseInstallationTransport(
        server.url("/installations").toString(),
        OkHttpClient.Builder()
          .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
          .build(),
      )

      try {
        runBlocking {
          transport.register(
            "device-token",
            "firebase-installation-id",
          )
        }
      } catch (exception: FirebaseInstallationRegistrationException) {
        return exception
      }
      error("Expected Firebase Installation registration to fail")
    }
  }
}

package net.ambitious.android.mealrelay

import kotlinx.coroutines.runBlocking
import net.ambitious.android.mealrelay.authorization.model.AuthorizationCode
import net.ambitious.android.mealrelay.authorization.network.MealRelayAuthorizationTransport
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionTransport
import net.ambitious.android.mealrelay.submission.network.ImageMealSubmissionTransport
import net.ambitious.android.mealrelay.submission.network.model.ImageMealSubmissionImage
import net.ambitious.android.mealrelay.submission.network.model.ImageMealSubmissionRequest
import net.ambitious.android.mealrelay.submission.network.model.TextMealSubmissionRequest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealRelayNetworkTransportTest {
  @Test
  fun authorizationTransportUsesOnlyTheAuthorizationEndpoint() {
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
      val transport = MealRelayAuthorizationTransport(
        server.url("/auth").toString(),
        OkHttpClient.Builder()
          .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
          .build(),
      )

      val authorization = runBlocking { transport.exchange(AuthorizationCode("google-code")) }

      assertEquals("issued-token", authorization.token)
      server.takeRequest().also { request ->
        assertEquals("/auth", request.path)
        assertEquals("""{"code":"google-code"}""", request.body.readUtf8())
        assertNull(request.getHeader("Authorization"))
      }
    }
  }

  @Test
  fun textTransportUsesOnlyTheTextEndpoint() {
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
      server.enqueue(MockResponse().setResponseCode(204))
      val transport = TextMealSubmissionTransport(
        readToken = { "device-token" },
        endpoint = server.url("/text").toString(),
        httpClient = OkHttpClient.Builder()
          .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
          .build(),
      )

      runBlocking {
        transport.submit(
          TextMealSubmissionRequest(
            text = "food",
            inputAt = "2026-09-22T08:00:00+09:00",
            mealId = "meal-1",
          ),
        )
      }

      server.takeRequest().also { request ->
        assertEquals("/text", request.path)
        assertEquals(
          """{"text":"food","inputAt":"2026-09-22T08:00:00+09:00","mealId":"meal-1"}""",
          request.body.readUtf8(),
        )
        assertEquals("Bearer device-token", request.getHeader("Authorization"))
      }
    }
  }

  @Test
  fun imageTransportSendsAllMealImagesAndCaptureTimeToTheImageEndpoint() {
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
      server.enqueue(MockResponse().setResponseCode(201))
      val transport = ImageMealSubmissionTransport(
        readToken = { "device-token" },
        endpoint = server.url("/image").toString(),
        httpClient = OkHttpClient.Builder()
          .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
          .build(),
      )

      runBlocking {
        transport.submit(
          ImageMealSubmissionRequest(
            images = (0..4).map { index ->
              ImageMealSubmissionImage(byteArrayOf(index.toByte()), "image/jpeg")
            },
            capturedAt = "2026-09-22T08:00:00Z",
            mealId = "meal-1",
          ),
        )
      }

      server.takeRequest().also { request ->
        assertEquals("/image", request.path)
        assertEquals("Bearer device-token", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        (0..4).forEach { index ->
          assertEquals(true, body.contains("name=\"image\"; filename=\"image-$index\""))
          assertEquals(true, body.contains("\r\n\r\n${index.toChar()}\r\n"))
        }
        assertEquals(5, Regex("name=\\\"image\\\"; filename=\\\"image-").findAll(body).count())
        assertEquals(true, body.contains("name=\"capturedAt\""))
        assertEquals(true, body.contains("2026-09-22T08:00:00Z"))
        assertEquals(true, body.contains("name=\"mealId\""))
        assertEquals(true, body.contains("meal-1"))
      }
    }
  }
}

package net.ambitious.android.mealrelay

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.IOException
import java.security.GeneralSecurityException

class MealRelayTokenStore(context: Context) {
  private val preferences = context.getSharedPreferences("mealrelay_auth", Context.MODE_PRIVATE)
  private val encryption by lazy {
    AeadConfig.register()
    AndroidKeysetManager.Builder()
      .withSharedPref(context, "token_keyset", "mealrelay_auth")
      .withMasterKeyUri("android-keystore://mealrelay_device_token")
      .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
      .build()
      .keysetHandle
      .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
  }

  fun read(): String? {
    val encrypted = preferences.getString("token", null) ?: return null
    return try {
      val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
      String(encryption.decrypt(bytes, byteArrayOf()), Charsets.UTF_8)
    } catch (_: GeneralSecurityException) {
      preferences.edit().remove("token").commit()
      null
    } catch (_: IOException) {
      preferences.edit().remove("token").commit()
      null
    } catch (_: IllegalArgumentException) {
      preferences.edit().remove("token").commit()
      null
    }
  }

  fun write(token: String) {
    require(token.isNotBlank())
    val encrypted = encryption.encrypt(token.toByteArray(Charsets.UTF_8), byteArrayOf())
    check(preferences.edit().putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
  }
}

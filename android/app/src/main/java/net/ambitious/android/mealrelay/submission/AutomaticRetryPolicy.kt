package net.ambitious.android.mealrelay.submission

fun isRetryableBackendStatus(statusCode: Int): Boolean =
  statusCode == 408 || statusCode == 429 || statusCode in 500..599

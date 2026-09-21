package net.ambitious.android.mealrelay.submission

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun isRetryableBackendStatus(statusCode: Int): Boolean =
  statusCode == 408 || statusCode == 429 || statusCode in 500..599

fun isRetryableNetworkException(error: IOException): Boolean =
  error is ConnectException ||
    error is NoRouteToHostException ||
    error is SocketException ||
    error is SocketTimeoutException ||
    error is UnknownHostException

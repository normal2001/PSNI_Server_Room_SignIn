package com.example.psnisignin

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Slack Web API functions used for best-effort visitor notifications. */
object SlackIntegration {
    private const val API_BASE = "https://slack.com/api/"
    private val executor = Executors.newSingleThreadExecutor()

    data class DeliveryReceipt(
        val destinationId: String,
        val messageTimestamp: String
    )

    /** Queue a channel notification; failures never affect the completed sign-in. */
    fun notifySuccessfulSignInAsync(
        firstName: String,
        lastName: String,
        signInDateTime: String,
        reason: String?,
        logger: AppLogger
    ) {
        if (!AppConfig.SLACK_NOTIFICATIONS_ENABLED) {
            logger.audit("slack-notification-skipped", "reason=disabled")
            return
        }
        executor.execute {
            val channel = AppConfig.SLACK_CHANNEL
            try {
                val message = buildString {
                    append("Visitor $firstName $lastName signed in to the server room at $signInDateTime.")
                    if (!reason.isNullOrBlank()) append(" Reason: $reason.")
                }
                logger.audit("slack-notification-attempt", "target=channel; channel=$channel")
                val receipt = sendNotificationToChannel(channel, message)
                logger.audit(
                    "slack-notification-success",
                    "target=channel; channel=$channel; destination=${receipt.destinationId}; " +
                        "message_ts=${receipt.messageTimestamp}"
                )
            } catch (e: Exception) {
                logger.error("SLACK_NOTIFICATION_FAILED", "Could not notify Slack channel '$channel'", e)
            }
        }
    }

    /** Send one notification to the configured channel name or channel ID. */
    @Throws(IOException::class)
    fun sendNotificationToChannel(channelNameOrId: String, message: String): DeliveryReceipt {
        requireConfiguredToken()
        val channel = channelNameOrId.trim().removePrefix("#")
        if (channel.isBlank()) throw IllegalStateException("SLACK_CHANNEL is not configured")
        val response = post("chat.postMessage", JSONObject().put("channel", channel).put("text", message))
        return DeliveryReceipt(
            response.optString("channel", channel),
            response.optString("ts", "not_returned")
        )
    }

    private fun post(apiMethod: String, body: JSONObject): JSONObject =
        execute(apiMethod, "$API_BASE$apiMethod", "POST", body.toString())

    private fun execute(apiMethod: String, url: String, httpMethod: String, body: String?): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = httpMethod
            connection.setRequestProperty("Authorization", "Bearer ${AppConfig.SLACK_BOT_TOKEN}")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                throw IOException("Slack API $apiMethod ($httpMethod) returned HTTP $responseCode; response=$responseText")
            }

            val response = try {
                JSONObject(responseText)
            } catch (e: Exception) {
                throw IOException("Slack API $apiMethod ($httpMethod) returned invalid JSON; response=$responseText", e)
            }
            if (!response.optBoolean("ok")) {
                val details = listOfNotNull(
                    "error=${response.optString("error", "unknown_error")}",
                    response.optString("needed").takeIf { it.isNotBlank() }?.let { "needed=$it" },
                    response.optString("provided").takeIf { it.isNotBlank() }?.let { "provided=$it" },
                    connection.getHeaderField("x-oauth-scopes")?.takeIf { it.isNotBlank() }?.let { "token_scopes=$it" },
                    connection.getHeaderField("x-accepted-oauth-scopes")?.takeIf { it.isNotBlank() }?.let { "accepted_scopes=$it" }
                ).joinToString("; ")
                throw IOException("Slack API $apiMethod ($httpMethod) failed: $details; response=$responseText")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun requireConfiguredToken() {
        if (AppConfig.SLACK_BOT_TOKEN.isBlank()) {
            throw IllegalStateException("SLACK_BOT_TOKEN is not configured")
        }
    }

}

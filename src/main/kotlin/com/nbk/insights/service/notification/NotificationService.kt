package com.nbk.insights.service.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification

@Service
class NotificationService {

    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    fun sendNotification(token: String, title: String, body: String): String? {
        return try {
            val message = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("Successfully sent message: {}", response)
            response
        } catch (e: Exception) {
            logger.error("Error sending message: {}", e.message, e)
            null
        }
    }

    fun sendNotificationToTopic(topic: String, title: String, body: String): String? {
        return try {
            val message = Message.builder()
                .setTopic(topic)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("Successfully sent message to topic: {}", response)
            response
        } catch (e: Exception) {
            logger.error("Error sending message to topic: {}", e.message, e)
            null
        }
    }
}
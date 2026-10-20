package com.nice.library_push.firebase

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nice.library_push.api.PushLibrary

/** FCM can invalidate backend configuration; campaign payload rules remain a host decision. */
class PushFirebaseService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushLibrary.onNewFirebaseToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        PushLibrary.onFirebaseData(message.data)
    }
}

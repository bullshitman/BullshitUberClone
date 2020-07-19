package com.bullshitman.bullshituberclone.services

import com.bullshitman.bullshituberclone.Common
import com.bullshitman.bullshituberclone.utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseCloudMessagingService: FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (FirebaseAuth.getInstance().currentUser != null) {
            UserUtils.updateToken(this, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        if (remoteMessage.data != null) {
            Common.showNotification(this, Random.nextInt(),
                remoteMessage.data[Common.NOTIF_TITLE],
                remoteMessage.data[Common.NOTIF_BODY],
                null)
        }
    }
}
package com.connecthid.intellij.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

fun Project.showNotification(title: String, content: String, type: NotificationType= NotificationType.INFORMATION) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("notification.groupId.connecthid") // defined in plugin.xml
        .createNotification(
            title, content,
            type
        )
        .notify(this)
}
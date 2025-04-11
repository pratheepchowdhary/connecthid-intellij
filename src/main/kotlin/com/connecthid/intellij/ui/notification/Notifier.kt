package com.connecthid.intellij.ui.notification

import com.connecthid.intellij.PluginBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import org.jetbrains.annotations.Nls

object Notifier {
    private val group = NotificationGroupManager.getInstance().getNotificationGroup(PluginBundle.message("notification.groupId"))
    fun showInfo(@Nls title: String, @Nls content: String) {
        val notification = group.createNotification(title, content, NotificationType.INFORMATION)
        notification.notify(null)
    }
    fun showError(@Nls title: String, @Nls content: String) {
        val notification = group.createNotification(title, content, NotificationType.INFORMATION)
        notification.notify(null)
    }
}
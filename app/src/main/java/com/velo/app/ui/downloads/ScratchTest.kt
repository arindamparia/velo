import androidx.media3.ui.PlayerNotificationManager
fun test(builder: PlayerNotificationManager.Builder, manager: PlayerNotificationManager) {
    // See what compiles
    manager.setUseRewindAction(true)
    manager.setUseFastForwardAction(true)
    manager.setUseFastForwardActionInCompactView(true)
    manager.setUseRewindActionInCompactView(true)
}

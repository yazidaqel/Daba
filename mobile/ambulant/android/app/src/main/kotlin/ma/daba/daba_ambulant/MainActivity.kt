package ma.daba.daba_ambulant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    /** Canaux de notification (Android 8+), nommés dans la langue choisie dans l'app (lib/push.dart). */
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "daba/notifications").setMethodCallHandler { appel, resultat ->
            if (appel.method != "canaux") return@setMethodCallHandler resultat.notImplemented()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val gestionnaire = getSystemService(NotificationManager::class.java)
                for (canal in appel.arguments as List<*>) {
                    val c = canal as Map<*, *>
                    // Recréer un canal existant ne change que son nom : importance et visibilité restent celles
                    // de la création (ou de l'utilisateur). Écran verrouillé : contenu masqué (noms de hanouts,
                    // code PIN de l'app, CNDP).
                    gestionnaire.createNotificationChannel(
                        NotificationChannel(c["id"] as String, c["nom"] as String, (c["importance"] as Number).toInt())
                            .apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
                }
            }
            resultat.success(null)
        }
    }
}

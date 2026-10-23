package dev.hiddensettings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** One launchable Settings screen. [intents] are tried in order; the first one that resolves wins. */
data class Entry(val title: String, val subtitle: String, val intents: List<Intent>)

data class Section(val title: String, val entries: List<Entry>)

private const val TV_SETTINGS = "com.android.tv.settings"

private fun action(action: String) = Intent(action)

private fun tvActivity(cls: String) =
    Intent().setComponent(ComponentName(TV_SETTINGS, if (cls.startsWith(".")) TV_SETTINGS + cls else cls))

private fun entry(title: String, subtitle: String, vararg intents: Intent) = Entry(title, subtitle, intents.toList())

/*
 * Screens found by reading the Settings app's activity table on a Google TV
 * (Android 14). Each entry lists the specific component first and a public
 * action as a fallback, so it still works on TVs whose Settings app is laid out differently.
 */
private val curated = listOf(
    Section(
        "Special app access",
        listOf(
            entry(
                "Install unknown apps", "Choose which apps may install other apps",
                tvActivity(".device.apps.specialaccess.ExternalSourcesActivity"),
                action("android.settings.MANAGE_UNKNOWN_APP_SOURCES"),
            ),
            entry(
                "All files access", "Apps that may read and write all storage",
                tvActivity(".device.apps.specialaccess.AllFilesAccessActivity"),
                action("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"),
            ),
            entry(
                "Display over other apps", "Apps that may draw on top of other apps",
                tvActivity(".device.apps.specialaccess.SystemAlertActivity"),
                action("android.settings.action.MANAGE_OVERLAY_PERMISSION"),
            ),
            entry(
                "Usage access", "Apps that may see which apps you use",
                tvActivity(".device.apps.specialaccess.AppUsageAccessActivity"),
                action("android.settings.USAGE_ACCESS_SETTINGS"),
            ),
            entry(
                "Picture-in-picture", "Apps allowed to play in a small window",
                tvActivity(".device.apps.specialaccess.PictureInPictureActivity"),
                action("android.settings.PICTURE_IN_PICTURE_SETTINGS"),
            ),
            entry(
                "Alarms & reminders", "Apps that may schedule exact alarms",
                tvActivity(".device.apps.specialaccess.AlarmsAndRemindersActivity"),
            ),
            entry(
                "Media management", "Apps that may change or delete media files",
                tvActivity(".device.apps.specialaccess.MediaManagementAppsActivity"),
            ),
            entry(
                "Notification access", "Apps that may read notifications",
                tvActivity(".privacy.NotificationAccessActivity"),
                action("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
            ),
        ),
    ),
    Section(
        "Apps",
        listOf(
            entry(
                "All apps", "Every installed app, including system apps",
                tvActivity(".device.apps.AllAppsActivity"),
                action("android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS"),
            ),
            // The bare APPLICATION_SETTINGS action matches several apps here and shows a chooser.
            entry("App list", "Installed apps, as in Settings > Apps", tvActivity(".device.apps.AppsActivity")),
            entry("Default apps", "Home screen and other defaults", action("android.settings.MANAGE_DEFAULT_APPS_SETTINGS")),
            // No Storage entry: INTERNAL_STORAGE_SETTINGS opens the "Storage & reset" screen on Google TV.
        ),
    ),
    Section(
        "System",
        listOf(
            entry(
                "Developer options", "Opens even when not shown in Settings",
                tvActivity(".system.development.DevelopmentActivity"),
                action("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"),
            ),
            entry("Device status", "IP and MAC addresses, uptime, serial", tvActivity(".about.StatusActivity")),
            entry("About", "Device info and build", action("android.settings.DEVICE_INFO_SETTINGS")),
            entry("Device name", "Rename the TV", action("android.settings.DEVICE_NAME")),
            entry("Security & restrictions", "Unknown sources and restricted profiles", action("android.settings.SECURITY_SETTINGS")),
            entry("Keyboards", "Installed keyboards and input methods", action("android.settings.INPUT_METHOD_SETTINGS")),
            entry("Text-to-speech", "Speech engine and rate", action("android.settings.TTS_SETTINGS")),
            entry("Date & time", "Time zone and clock format", action("android.settings.DATE_SETTINGS")),
            entry("Language", "System language", action("android.settings.LOCALE_SETTINGS")),
            entry("Location", "Location access and scanning", action("android.settings.LOCATION_SOURCE_SETTINGS")),
            entry("Microphone access", "Turn the microphone on or off", action("android.settings.MANAGE_MICROPHONE_PRIVACY")),
            entry("Accessibility", "Captions, high-contrast text, services", action("android.settings.ACCESSIBILITY_SETTINGS")),
            entry("Accounts & sync", "Signed-in accounts", action("android.settings.SYNC_SETTINGS")),
        ),
    ),
    Section(
        "Network",
        listOf(
            entry("Network & internet", "Wi-Fi and Ethernet", action("android.settings.WIFI_SETTINGS")),
            entry(
                "Add Wi-Fi network", "Join a hidden network by name",
                tvActivity(".connectivity.AddWifiNetworkActivity"),
                action("com.android.settings.wifi.action.ADD_WIFI_NETWORK"),
            ),
        ),
    ),
    Section(
        "Display & device",
        listOf(
            entry("Display & sound", "Resolution, HDR, audio output", tvActivity(".device.displaysound.DisplaySoundActivity")),
            entry(
                "Vendor settings", "Chip maker's extra options",
                action("com.google.android.tv.settings.vendor"),
                tvActivity(".vendor.VendorActivity"),
            ),
            entry("Energy modes", "Standby power use", action("com.google.android.tv.settings.energymodes")),
            entry("Ambient mode", "Screensaver options", action("com.google.android.tv.settings.ambient")),
        ),
    ),
)

/** Activities that reset, format or need arguments to make sense; left out of the full list. */
private val unsafeOrUseless = Regex(
    "Reset|Format|Migrate|Unmount|Fallback|Stub|Admin|ProfileOwner|Bugreport|Restricted|Dialog|Pairing|" +
        "RequestPermission|Setup|FlowStart|Trampoline|Confirmation|MoveApp|NewStorage|AppManagement|Suggestion",
)

fun resolve(context: Context, entry: Entry): Intent? =
    entry.intents.firstOrNull { it.resolveActivity(context.packageManager) != null }

/** The curated sections (only screens this TV has), then every other screen Settings exposes. */
fun loadSections(context: Context): List<Section> {
    val sections = curated
        .map { s -> s.copy(entries = s.entries.filter { resolve(context, it) != null }) }
        .filter { it.entries.isNotEmpty() }
    val shown = sections.flatMap { s -> s.entries.mapNotNull { resolve(context, it)?.resolveActivity(context.packageManager)?.className } }.toSet()
    val all = allSettingsActivities(context).filter { it.intents.first().component?.className !in shown }
    return if (all.isEmpty()) sections else sections + Section("Every other screen", all)
}

private fun allSettingsActivities(context: Context): List<Entry> {
    val info = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(TV_SETTINGS, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(TV_SETTINGS, PackageManager.GET_ACTIVITIES)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        return emptyList()
    }
    return info.activities.orEmpty()
        .filter { it.exported && it.enabled && it.permission == null && !unsafeOrUseless.containsMatchIn(it.name) }
        .map { a ->
            val short = a.name.substringAfterLast('.')
            entry(humanize(short), a.name.removePrefix("$TV_SETTINGS."), tvActivity(a.name))
        }
        .sortedBy { it.title }
}

/** "ExternalSourcesActivity" -> "External Sources". */
private fun humanize(className: String): String =
    className.removeSuffix("Activity")
        .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        .ifBlank { className }

package com.example.vinyl.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylTheme

private val CardBg = Color(0xFF1C1C1C)
private val SignOutBg = Color(0xFF2A1616)
private val SignOutFg = Color(0xFFE57373)
private val GroupRadius = 22.dp
private val PagePad = 20.dp

private val AccentColors = listOf(
    Color(0xFF77EDE5),
    Color(0xFFE57373),
    Color(0xFFF4C27A),
    Color(0xFF8FA8FF),
)

private enum class SettingsPage { List, Account, Preferences, Appearance, About, Help }

private fun themeLabel(mode: Int) = when (mode) {
    0 -> "System"
    1 -> "Day"
    else -> "Night"
}

@Composable
fun SettingsScreen(
    locationValue: String? = null,
    onOpenLocation: () -> Unit = {},
    onBack: () -> Unit,
    displayName: String = "Music Explorer",
    handle: String = "@18402937",
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.List) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }
    var themeMode by rememberSaveable { mutableStateOf(2) }
    var accentIndex by rememberSaveable { mutableStateOf(0) }
    var selectedGenres by rememberSaveable { mutableStateOf(setOf("Indie", "Electronic")) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var signedOutNote by rememberSaveable { mutableStateOf(false) }

    val onPageBack = {
        if (page == SettingsPage.List) onBack() else page = SettingsPage.List
    }
    BackHandler(onBack = onPageBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.Background)
            .statusBarsPadding(),
    ) {
        SettingsTopBar(
            title = when (page) {
                SettingsPage.List -> "Settings"
                SettingsPage.Account -> "Profile"
                SettingsPage.Preferences -> "Music Preferences"
                SettingsPage.Appearance -> "Appearance"
                SettingsPage.About -> "About"
                SettingsPage.Help -> "Help & Support"
            },
            onBack = onPageBack,
        )
        when (page) {
            SettingsPage.List -> SettingsHome(
                displayName = displayName,
                handle = handle,
                locationValue = locationValue,
                notificationsEnabled = notificationsEnabled,
                onNotificationsChange = { notificationsEnabled = it },
                themeLabel = themeLabel(themeMode),
                onOpenProfile = { page = SettingsPage.Account },
                onOpenPreferences = { page = SettingsPage.Preferences },
                onOpenAppearance = { page = SettingsPage.Appearance },
                onOpenLocation = onOpenLocation,
                onOpenAbout = { page = SettingsPage.About },
                onOpenHelp = { page = SettingsPage.Help },
                onSignOut = { signedOutNote = true },
            )
            SettingsPage.Account -> AccountPage(displayName, handle) { showDeleteConfirm = true }
            SettingsPage.Preferences -> PreferencesPage(selectedGenres) { genre ->
                selectedGenres = if (genre in selectedGenres) selectedGenres - genre else selectedGenres + genre
            }
            SettingsPage.Appearance -> AppearancePage(
                themeMode = themeMode,
                onThemeModeChange = { themeMode = it },
                accentIndex = accentIndex,
                onAccentChange = { accentIndex = it },
            )
            SettingsPage.About -> AboutPage()
            SettingsPage.Help -> HelpPage()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = CardBg,
            titleContentColor = VinylPalette.TextPrimary,
            textContentColor = VinylPalette.TextMuted,
            title = { Text("Delete account?") },
            text = { Text("This will remove your letters and collection from this app.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Close", color = SignOutFg) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = VinylPalette.TextMuted) }
            },
        )
    }
    if (signedOutNote) {
        AlertDialog(
            onDismissRequest = { signedOutNote = false },
            containerColor = CardBg,
            titleContentColor = VinylPalette.TextPrimary,
            textContentColor = VinylPalette.TextMuted,
            title = { Text("Sign out") },
            text = { Text("You’ll need to sign in again to send or receive letters.") },
            confirmButton = {
                TextButton(onClick = { signedOutNote = false }) { Text("OK", color = VinylPalette.TealAccent) }
            },
        )
    }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VinylPalette.TextPrimary)
        }
        Text(title, color = VinylPalette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsHome(
    displayName: String,
    handle: String,
    locationValue: String?,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    themeLabel: String,
    onOpenProfile: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHelp: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = PagePad).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProfileHeader(displayName, handle, onOpenProfile)
        SettingsGroup {
            IconNavRow(Icons.Outlined.MusicNote, "Music Preferences", onClick = onOpenPreferences)
            GroupDivider()
            IconNavRow(Icons.Outlined.DarkMode, "Appearance", subtitle = themeLabel, onClick = onOpenAppearance)
        }
        SettingsGroup {
            IconNavRow(Icons.Outlined.LocationOn, "Location", locationValue ?: "Not set", onClick = onOpenLocation)
            GroupDivider()
            SwitchRow(Icons.Outlined.Notifications, "Notifications", notificationsEnabled, onNotificationsChange)
        }
        SettingsGroup {
            IconNavRow(Icons.Outlined.Info, "About", onClick = onOpenAbout)
            GroupDivider()
            IconNavRow(Icons.Outlined.HelpOutline, "Help & Support", onClick = onOpenHelp)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(GroupRadius)).background(SignOutBg)
                .clickable(onClick = onSignOut).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = SignOutFg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text("Sign Out", color = SignOutFg, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ProfileHeader(displayName: String, handle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(GroupRadius)).clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VinylAvatar()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(displayName, color = VinylPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(handle, color = VinylPalette.TextMuted, fontSize = 14.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = VinylPalette.TextMuted)
    }
}

@Composable
private fun VinylAvatar() {
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF141414)).border(1.dp, Color(0xFF2A2A2A), CircleShape))
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF0A0A0A)))
        Box(Modifier.size(16.dp).clip(CircleShape).background(VinylPalette.TealAccent))
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(GroupRadius)).background(CardBg)) { content() }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 52.dp, end = 16.dp), 1.dp, Color.White.copy(alpha = 0.06f))
}

@Composable
private fun IconNavRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = VinylPalette.TextMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = VinylPalette.TextPrimary, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = VinylPalette.TextMuted, fontSize = 14.sp)
        }
        if (showChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = VinylPalette.TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = VinylPalette.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = VinylPalette.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VinylPalette.TealAccent,
                checkedBorderColor = VinylPalette.TealAccent,
                uncheckedThumbColor = Color(0xFFD0D0D0),
                uncheckedTrackColor = Color(0xFF3A3A3A),
                uncheckedBorderColor = Color(0xFF3A3A3A),
            ),
        )
    }
}

@Composable
private fun AppearancePage(
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    accentIndex: Int,
    onAccentChange: (Int) -> Unit,
) {
    val options = listOf(
        Triple(0, Icons.Outlined.BrightnessAuto, "System"),
        Triple(1, Icons.Outlined.LightMode, "Day"),
        Triple(2, Icons.Outlined.DarkMode, "Night"),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 32.dp),
    ) {
        Text("Theme", color = VinylPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { (id, icon, label) ->
                val on = themeMode == id
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(108.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) Color(0xFF242424) else Color(0xFF141414))
                        .border(
                            width = 1.dp,
                            color = if (on) VinylPalette.TealAccent else Color(0xFF2E2E2E),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { onThemeModeChange(id) }
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, null, tint = if (on) VinylPalette.TealAccent else Color(0xFF7A7A7A), modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(label, color = if (on) VinylPalette.TextPrimary else Color(0xFF8A8A8A), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Accent", color = VinylPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentColors.forEachIndexed { i, color ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(color)
                        .border(if (accentIndex == i) 2.dp else 0.dp, VinylPalette.TextPrimary, CircleShape)
                        .clickable { onAccentChange(i) },
                )
            }
        }
    }
}

@Composable
private fun AccountPage(displayName: String, handle: String, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PagePad),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        VinylAvatar()
        Spacer(Modifier.height(12.dp))
        Text("Edit", color = VinylPalette.TealAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { })
        Spacer(Modifier.height(16.dp))
        Text(displayName, color = VinylPalette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(handle, color = VinylPalette.TextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        SettingsGroup {
            IconNavRow(title = "Name", subtitle = displayName, showChevron = false, onClick = {})
            GroupDivider()
            IconNavRow(title = "ID", subtitle = handle, showChevron = false, onClick = {})
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(GroupRadius)).background(SignOutBg)
                .clickable(onClick = onDelete).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Delete, null, tint = SignOutFg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text("Delete account", color = SignOutFg, fontSize = 16.sp)
        }
    }
}

private val PreviewGenres = listOf(
    "Pop", "Rock", "Hip Hop", "R&B", "Electronic", "Indie",
    "Jazz", "Classical", "Folk", "Metal", "Country", "Ambient",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferencesPage(selected: Set<String>, onToggle: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 32.dp),
    ) {
        Text("Default genres", color = VinylPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Select the genres you want to hear more of. You can change these anytime.",
            color = VinylPalette.TextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(GroupRadius)).background(CardBg).padding(16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PreviewGenres.forEach { genre ->
                    val on = genre in selected
                    Text(
                        genre,
                        color = if (on) VinylPalette.Background else VinylPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (on) VinylPalette.TealAccent else Color(0xFF2A2A2A))
                            .clickable { onToggle(genre) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutPage() {
    Column(Modifier.padding(horizontal = PagePad), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard("App", "Vinyl")
        InfoCard("Version", "0.1")
    }
}

@Composable
private fun HelpPage() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoCard(
            "Is my real name visible?",
            "No. Letters show an alias only. The name on your Google account is used to sign in, and is not attached to anything you send or receive.",
        )
        InfoCard(
            "Why do you ask for a city?",
            "So a letter can feel like it came from somewhere in the world, without anyone seeing your street or exact pin. We only keep the city. Precise location is not stored.",
        )
        InfoCard(
            "Who can see what I send?",
            "A stranger may receive your letter. They will not see your name, your ID, or how to find you.",
        )
        InfoCard(
            "Need more help?",
            "For account or delivery issues, email support@vinyl.app. We typically reply within one business day.",
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(GroupRadius)).background(CardBg).padding(18.dp)) {
        Text(title, color = VinylPalette.TextPrimary, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(body, color = VinylPalette.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    VinylTheme {
        SettingsScreen(locationValue = "Melbourne", onOpenLocation = {}, onBack = {})
    }
}

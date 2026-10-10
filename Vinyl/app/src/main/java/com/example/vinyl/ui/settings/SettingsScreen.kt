package com.example.vinyl.ui.settings

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.vinyl.notification.DailyReminder
import com.example.vinyl.notification.ReminderPrefs
import com.example.vinyl.ui.notification.notificationsAllowed
import com.example.vinyl.ui.notification.rememberNotificationPermissionRequest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.Accent
import com.example.vinyl.ui.theme.ThemeState
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylTheme
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextOverflow

private val CardBg = Color(0xFF1C1C1C)
private val SignOutBg = Color(0xFF2A1616)
private val SignOutFg = Color(0xFFE57373)
private val GroupRadius = 22.dp
private val PagePad = 20.dp

private enum class SettingsPage { List, Account, IconMaker, Preferences, About, Help }

@Composable
fun SettingsScreen(
    locationValue: String? = null,
    onOpenLocation: () -> Unit = {},
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    /** The server-generated alias ("Happy Giraffe"). Blank until the profile read returns. */
    displayName: String = "",
    modifier: Modifier = Modifier,
) {

    var page by rememberSaveable { mutableStateOf(SettingsPage.List) }
    // The daily reminder: on only if the user wants it AND Android allows notifications. Re-read
    // on every resume, since either can change in system settings while we're away.
    val context = LocalContext.current
    val reminderPrefs = remember { ReminderPrefs(context.applicationContext) }
    var notificationsEnabled by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        notificationsEnabled = reminderPrefs.enabled && context.notificationsAllowed()
        onPauseOrDispose {}
    }
    val notificationPermission = rememberNotificationPermissionRequest { granted ->
        DailyReminder.setEnabled(context, granted)
        notificationsEnabled = granted
    }
    var signedOutNote by rememberSaveable { mutableStateOf(false) }
    val avatarAppearance = rememberAvatarAppearance()
    var avatarIcon by rememberSaveable { mutableStateOf(avatarAppearance.iconIndex) }
    var avatarGradient by rememberSaveable { mutableStateOf(avatarAppearance.gradientIndex) }
    var avatarImageUrl by rememberSaveable { mutableStateOf(avatarAppearance.imageUrl) }

    val onPageBack = {
        page = when (page) {
            SettingsPage.List -> {
                onBack()
                page
            }
            SettingsPage.IconMaker -> SettingsPage.Account
            else -> SettingsPage.List
        }
    }
    BackHandler(onBack = onPageBack)

    androidx.compose.runtime.CompositionLocalProvider(LocalAvatarImageUrl provides avatarImageUrl) {
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
                    SettingsPage.IconMaker -> "Icon"
                    SettingsPage.Preferences -> "Music Preferences"
                    SettingsPage.About -> "About"
                    SettingsPage.Help -> "Help & Support"
                },
                onBack = onPageBack,
            )
            when (page) {
                SettingsPage.List -> SettingsHome(
                    displayName = displayName,
                    iconIndex = avatarIcon,
                    gradientIndex = avatarGradient,
                    locationValue = locationValue,
                    notificationsEnabled = notificationsEnabled,
                    onNotificationsChange = { on ->
                        if (on) {
                            // Asks Android if needed, or opens system settings once it won't ask.
                            notificationPermission.request()
                        } else {
                            DailyReminder.setEnabled(context, false)
                            notificationsEnabled = false
                        }
                    },
                    accent = ThemeState.accent,
                    onAccentChange = { ThemeState.setAccent(context, it) },
                    onOpenProfile = { page = SettingsPage.Account },
                    onOpenPreferences = { page = SettingsPage.Preferences },
                    onOpenLocation = onOpenLocation,
                    onOpenAbout = { page = SettingsPage.About },
                    onOpenHelp = { page = SettingsPage.Help },
                    onSignOut = { signedOutNote = true },
                )
                SettingsPage.Account -> AccountPage(
                    displayName = displayName,
                    iconIndex = avatarIcon,
                    gradientIndex = avatarGradient,
                    onEditIcon = { page = SettingsPage.IconMaker },
                )
                SettingsPage.IconMaker -> AvatarMakerBody(
                    iconIndex = avatarIcon,
                    gradientIndex = avatarGradient,
                    onIconChange = {
                        avatarIcon = it
                        avatarImageUrl = null
                        avatarAppearance.save(avatarIcon, avatarGradient)
                    },
                    onGradientChange = {
                        avatarGradient = it
                        avatarAppearance.save(avatarIcon, avatarGradient, avatarImageUrl)
                    },
                )
                SettingsPage.Preferences -> PreferencesPage()
                SettingsPage.About -> AboutPage()
                SettingsPage.Help -> HelpPage()
            }
        }

    }

    if (signedOutNote) {
        AlertDialog(
            onDismissRequest = { signedOutNote = false },
            containerColor = CardBg,
            titleContentColor = VinylPalette.TextPrimary,
            textContentColor = VinylPalette.TextMuted,
            title = { Text("Sign out") },
            text = { Text("You’ll need to sign in again to send or receive music cards.") },
            confirmButton = {
                TextButton(onClick = {
                    signedOutNote = false
                    onSignOut()
                }) { Text("Sign out", color = SignOutFg) }
            },
            dismissButton = {
                TextButton(onClick = { signedOutNote = false }) { Text("Cancel", color = VinylPalette.TextMuted) }
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
    iconIndex: Int,
    gradientIndex: Int,
    locationValue: String?,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    accent: Accent,
    onAccentChange: (Accent) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPreferences: () -> Unit,
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
        ProfileHeader(displayName, iconIndex, gradientIndex, onOpenProfile)
        SettingsGroup {
            IconNavRow(Icons.Outlined.MusicNote, "Music Preferences", onClick = onOpenPreferences)
            GroupDivider()
            AccentRow(accent = accent, onAccentChange = onAccentChange)
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
                .clickable(role = Role.Button, onClick = onSignOut).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = SignOutFg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text("Sign out", color = SignOutFg, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ProfileHeader(
    displayName: String,
    iconIndex: Int,
    gradientIndex: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(GroupRadius)).clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarPreview(iconIndex, gradientIndex, size = 64.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(displayName, color = VinylPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
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
    // The whole row is the toggle, so TalkBack reads the label and the state together.
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = VinylPalette.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = VinylPalette.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
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

/**
 * "Accent colour" with the current colour as a swatch on the right. Tapping the swatch opens the
 * list of accents under it; picking one applies it app-wide straight away.
 */
@Composable
private fun AccentRow(accent: Accent, onAccentChange: (Accent) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    // Vertical padding 4dp less than IconNavRow's, so the 48dp swatch target gives the same
    // row height as the Location row.
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Palette, null, tint = VinylPalette.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Accent colour", color = VinylPalette.TextPrimary, fontSize = 16.sp)
            Text(accent.label, color = VinylPalette.TextMuted, fontSize = 14.sp)
        }
        Box {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .clickable(role = Role.Button) { menuOpen = true }
                    .semantics { contentDescription = "Accent colour, ${accent.label}. Double tap to change" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(accent.color)
                        .border(1.dp, VinylPalette.Cream, CircleShape),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = CardBg,
                shape = RoundedCornerShape(16.dp),
            ) {
                Accent.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, color = VinylPalette.TextPrimary, fontSize = 16.sp) },
                        leadingIcon = {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(option.color))
                        },
                        trailingIcon = if (option == accent) {
                            { Icon(Icons.Rounded.Check, null, tint = VinylPalette.TextPrimary) }
                        } else {
                            null
                        },
                        onClick = {
                            menuOpen = false
                            onAccentChange(option)
                        },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { selected = option == accent },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountPage(
    displayName: String,
    iconIndex: Int,
    gradientIndex: Int,
    onEditIcon: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PagePad),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        AvatarPreview(iconIndex, gradientIndex, size = 88.dp, onClick = onEditIcon)
        // 48dp touch target around the link; the spacer below shrinks to keep the name in place.
        Box(
            Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onEditIcon),
            contentAlignment = Alignment.Center,
        ) {
            Text("Edit", color = VinylPalette.TealAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(2.dp))
        Text(displayName, color = VinylPalette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        SettingsGroup {
            IconNavRow(title = "Name", subtitle = displayName, showChevron = false, onClick = {})
        }
    }
}

@Composable
private fun PreferencesPage(viewModel: PreferencesViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 32.dp),
    ) {
        Text("Default genres", color = VinylPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Used when you don't pick genres for the day. Leave all unselected to match without a genre preference.",
            color = VinylPalette.TextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(20.dp))

        when {
            state.isLoading -> Text("Loading genres…", color = VinylPalette.TextMuted, fontSize = 14.sp)
            state.options.isEmpty() -> {
                Text(
                    state.error ?: "No genres available right now.",
                    color = Color(0xFFE08787),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                TextButton(onClick = viewModel::load, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Try again", color = VinylPalette.TealAccent)
                }
            }
            else -> {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(GroupRadius)).background(CardBg).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.options.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { genre ->
                                PreferenceGenreCell(
                                    label = genre.label,
                                    selected = genre.slug in state.selected,
                                    onToggle = { viewModel.toggle(genre.slug) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Keeps a short last row aligned to the same column widths.
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFE08787), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PreferenceGenreCell(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) VinylPalette.TealAccent.copy(alpha = 0.16f) else Color(0xFF2A2A2A))
            .border(1.dp, if (selected) VinylPalette.TealAccent else Color.Transparent, shape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = VinylPalette.TealAccent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = if (selected) VinylPalette.TealAccent else VinylPalette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
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
            "No. Music cards are anonymous and never show a name. Your Google account is only used to sign in, and is not attached to anything you send or receive.",
        )
        InfoCard(
            "Why do you ask for a city?",
            "So a music card can feel like it came from somewhere in the world, without anyone seeing your street or exact pin. We only keep the city. Precise location is not stored.",
        )
        InfoCard(
            "Who can see what I send?",
            "A stranger may receive your music card. They will not see your name or how to find you.",
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
        SettingsScreen(locationValue = "Melbourne", onOpenLocation = {}, onSignOut = {}, onBack = {})
    }
}

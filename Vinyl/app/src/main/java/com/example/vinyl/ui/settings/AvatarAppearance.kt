package com.example.vinyl.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.vinyl.data.Supabase
import io.github.jan.supabase.auth.auth

val LocalAvatarImageUrl = compositionLocalOf<String?> { null }

/** Local appearance only: the database keeps its existing avatar slug contract. */
class AvatarAppearance(context: Context, accountId: String) {
    private val preferences = context.getSharedPreferences("avatar_appearance", Context.MODE_PRIVATE)
    private val prefix = "$accountId."
    val iconIndex: Int get() = preferences.getInt(prefix + "icon", 0)
    val gradientIndex: Int get() = preferences.getInt(prefix + "gradient", 2)

    val imageUrl: String? get() = preferences.getString(prefix + "url", null)

    fun save(iconIndex: Int, gradientIndex: Int, imageUrl: String? = null) {
        preferences.edit().putInt(prefix + "icon", iconIndex).putInt(prefix + "gradient", gradientIndex).putString(prefix + "url", imageUrl).apply()
    }
}

@Composable
fun rememberAvatarAppearance(): AvatarAppearance {
    val context = LocalContext.current.applicationContext
    val accountId = Supabase.client.auth.currentUserOrNull()?.id ?: "preview"
    return remember(context, accountId) { AvatarAppearance(context, accountId) }
}

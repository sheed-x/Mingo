package app.mingo.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as Lucide

/** The app's icon set: Lucide line icons (ISC license). */
object MingoIcons {
    val Home = Lucide.drawable.lucide_ic_house
    val Downloads = Lucide.drawable.lucide_ic_arrow_down_to_line
    val Library = Lucide.drawable.lucide_ic_library
    val Settings = Lucide.drawable.lucide_ic_settings
    val Link = Lucide.drawable.lucide_ic_link
    val Paste = Lucide.drawable.lucide_ic_clipboard_paste
    val Close = Lucide.drawable.lucide_ic_x
    val Video = Lucide.drawable.lucide_ic_clapperboard
    val Audio = Lucide.drawable.lucide_ic_audio_lines
    val Music = Lucide.drawable.lucide_ic_music
    val Sparkles = Lucide.drawable.lucide_ic_sparkles
    val Check = Lucide.drawable.lucide_ic_check
    val CheckCircle = Lucide.drawable.lucide_ic_circle_check
    val Alert = Lucide.drawable.lucide_ic_circle_alert
    val Stop = Lucide.drawable.lucide_ic_circle_stop
    val Retry = Lucide.drawable.lucide_ic_rotate_ccw
    val Trash = Lucide.drawable.lucide_ic_trash_2
    val Open = Lucide.drawable.lucide_ic_folder_open
    val Share = Lucide.drawable.lucide_ic_share_2
    val Palette = Lucide.drawable.lucide_ic_palette
    val Contrast = Lucide.drawable.lucide_ic_contrast
    val Moon = Lucide.drawable.lucide_ic_moon
    val SunMoon = Lucide.drawable.lucide_ic_sun_moon
    val Info = Lucide.drawable.lucide_ic_info
    val Cpu = Lucide.drawable.lucide_ic_cpu
    val Refresh = Lucide.drawable.lucide_ic_refresh_cw
    val Image = Lucide.drawable.lucide_ic_image
    val Captions = Lucide.drawable.lucide_ic_captions
    val Layers = Lucide.drawable.lucide_ic_layers
    val Shield = Lucide.drawable.lucide_ic_shield_check
    val Wand = Lucide.drawable.lucide_ic_wand_sparkles
    val Gauge = Lucide.drawable.lucide_ic_gauge
    val Monitor = Lucide.drawable.lucide_ic_monitor_play
    val Clock = Lucide.drawable.lucide_ic_clock
    val User = Lucide.drawable.lucide_ic_user
    val Zap = Lucide.drawable.lucide_ic_zap
    val Folder = Lucide.drawable.lucide_ic_folder_down
    val ChevronRight = Lucide.drawable.lucide_ic_chevron_right
    val Hourglass = Lucide.drawable.lucide_ic_hourglass
    val Headphones = Lucide.drawable.lucide_ic_headphones
    val Languages = Lucide.drawable.lucide_ic_languages
    val Back = Lucide.drawable.lucide_ic_arrow_left
    val Mail = Lucide.drawable.lucide_ic_mail
    val Phone = Lucide.drawable.lucide_ic_smartphone
    val Github = Lucide.drawable.lucide_ic_github
    val WifiOff = Lucide.drawable.lucide_ic_wifi_off
    val CloudOff = Lucide.drawable.lucide_ic_cloud_off
    val LinkOff = Lucide.drawable.lucide_ic_link_2_off
    val Lock = Lucide.drawable.lucide_ic_lock
    val VideoOff = Lucide.drawable.lucide_ic_video_off
    val MapOff = Lucide.drawable.lucide_ic_map_pin_off
    val Timer = Lucide.drawable.lucide_ic_timer
    val Radio = Lucide.drawable.lucide_ic_radio
    val ServerCrash = Lucide.drawable.lucide_ic_server_crash
    val ChevronDown = Lucide.drawable.lucide_ic_chevron_down
    val Search = Lucide.drawable.lucide_ic_search
    val Sort = Lucide.drawable.lucide_ic_arrow_down_wide_narrow
    val More = Lucide.drawable.lucide_ic_ellipsis_vertical
    val Copy = Lucide.drawable.lucide_ic_copy
    val External = Lucide.drawable.lucide_ic_external_link
    val Play = Lucide.drawable.lucide_ic_play
    val Pause = Lucide.drawable.lucide_ic_pause
    val Grid = Lucide.drawable.lucide_ic_layout_grid
    val GridSmall = Lucide.drawable.lucide_ic_grid_3x3
    val ListView = Lucide.drawable.lucide_ic_list
    val Download = Lucide.drawable.lucide_ic_download
    val Drive = Lucide.drawable.lucide_ic_hard_drive
    val Calendar = Lucide.drawable.lucide_ic_calendar

    // Platform glyphs for the "Works with" grid. Lucide has no TikTok, Reddit, SoundCloud or
    // Vimeo marks, so those use the closest generic symbol.
    val YouTube = Lucide.drawable.lucide_ic_youtube
    val TikTok = Lucide.drawable.lucide_ic_music_2
    val Instagram = Lucide.drawable.lucide_ic_instagram
    val Facebook = Lucide.drawable.lucide_ic_facebook
    val X = Lucide.drawable.lucide_ic_twitter
    val Snapchat = Lucide.drawable.lucide_ic_ghost
    val Reddit = Lucide.drawable.lucide_ic_messages_square
    val Twitch = Lucide.drawable.lucide_ic_twitch
    val SoundCloud = Lucide.drawable.lucide_ic_cloud
    val Vimeo = Lucide.drawable.lucide_ic_square_play
    val Pinterest = Lucide.drawable.lucide_ic_pin
    val Dailymotion = Lucide.drawable.lucide_ic_tv
}

/** Icons that point somewhere and must be flipped for right-to-left languages. */
private val directional = setOf(MingoIcons.ChevronRight, MingoIcons.Back, MingoIcons.Retry, MingoIcons.External)

@Composable
fun MingoIcon(
    @DrawableRes icon: Int,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    val mirror = icon in directional && LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .then(if (mirror) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
        tint = tint,
    )
}

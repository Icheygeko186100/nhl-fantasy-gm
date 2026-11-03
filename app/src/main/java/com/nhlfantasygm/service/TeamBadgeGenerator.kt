package com.nhlfantasygm.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

object TeamBadgeGenerator {

    private val badgeCache = ConcurrentHashMap<String, Bitmap>()

    private val NHL_TEAM_COLORS = mapOf(
        "ANA" to (0xFFF47A38L to 0xFFB9975BL), // Ducks Orange & Gold
        "BOS" to (0xFFFFB81CL to 0xFF000000L), // Bruins Gold & Black
        "BUF" to (0xFF003087L to 0xFFFFB81CL), // Sabres Navy & Gold
        "CGY" to (0xFFC8102EL to 0xFFF1BE48L), // Flames Red & Gold
        "CAR" to (0xFFCC0000L to 0xFF000000L), // Hurricanes Red & Black
        "CHI" to (0xFFCF0A2CL to 0xFF000000L), // Blackhawks Red
        "COL" to (0xFF6F263DL to 0xFF236192L), // Avalanche Burgundy & Blue
        "CBJ" to (0xFF002654L to 0xFFCE1126L), // Blue Jackets Navy & Red
        "DAL" to (0xFF006847L to 0xFF8F8F8CL), // Stars Victory Green & Silver
        "DET" to (0xFFCE1126L to 0xFFFFFFFFL), // Red Wings Red & White
        "EDM" to (0xFF041E42L to 0xFFFF4C00L), // Oilers Royal Blue & Orange
        "FLA" to (0xFF041E42L to 0xFFC8102EL), // Panthers Navy & Red
        "LAK" to (0xFF111111L to 0xFFA2AAADL), // Kings Black & Silver
        "MIN" to (0xFF154734L to 0xFFA6192EL), // Wild Forest Green & Wheat
        "MTL" to (0xFFAF1E2DL to 0xFF192168L), // Canadiens Red & Blue
        "NSH" to (0xFFFFB81CL to 0xFF041E42L), // Predators Gold & Navy
        "NJD" to (0xFFCE1126L to 0xFF000000L), // Devils Red & Black
        "NYI" to (0xFF00539BL to 0xFFF47920L), // Islanders Royal Blue & Orange
        "NYR" to (0xFF0038A8L to 0xFFCE1126L), // Rangers Blue & Red
        "OTT" to (0xFFC52032L to 0xFF000000L), // Senators Red & Black
        "PHI" to (0xFFF74902L to 0xFF000000L), // Flyers Orange & Black
        "PIT" to (0xFF000000L to 0xFFFCB514L), // Penguins Black & Gold
        "SJS" to (0xFF006D75L to 0xFFEA7200L), // Sharks Teal & Orange
        "SEA" to (0xFF001628L to 0xFF99D9D9L), // Kraken Deep Sea Blue & Ice Blue
        "STL" to (0xFF002F87L to 0xFFFCB514L), // Blues Royal Blue & Gold
        "TBL" to (0xFF002868L to 0xFFFFFFFFL), // Lightning Blue & White
        "TOR" to (0xFF00205BL to 0xFFFFFFFFL), // Maple Leafs Blue & White
        "UTA" to (0xFF69B3E7L to 0xFF000000L), // Utah Mountain Blue & Black
        "VAN" to (0xFF00205BL to 0xFF00843DL), // Canucks Blue & Green
        "VGK" to (0xFFB4975AL to 0xFF333F48L), // Golden Knights Steel Grey & Gold
        "WSH" to (0xFF041E42L to 0xFFC8102EL), // Capitals Navy & Red
        "WPG" to (0xFF041E42L to 0xFF004C97L)  // Jets Navy & Polar Night Blue
    )

    fun getBadgeBitmap(abbrev: String, sizePx: Int = 120): Bitmap {
        val cleanAbbrev = abbrev.trim().uppercase().take(4)
        val cacheKey = "$cleanAbbrev-$sizePx"
        return badgeCache.getOrPut(cacheKey) {
            createBadge(cleanAbbrev, sizePx)
        }
    }

    private fun createBadge(abbrev: String, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colors = NHL_TEAM_COLORS[abbrev] ?: (0xFF1E293BL to 0xFF38BDF8L)
        val primaryColor = (colors.first.toLong() or 0xFF000000L).toInt()
        val secondaryColor = (colors.second.toLong() or 0xFF000000L).toInt()

        val cornerRadius = size * 0.24f
        val rect = RectF(2f, 2f, size.toFloat() - 2f, size.toFloat() - 2f)

        // Background rounded square
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // Subtle accent border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            style = Paint.Style.STROKE
            strokeWidth = size * 0.05f
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // Centered Abbreviation Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * (if (abbrev.length >= 4) 0.32f else 0.38f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textBounds = Rect()
        textPaint.getTextBounds(abbrev, 0, abbrev.length, textBounds)
        val y = (size / 2f) + (textBounds.height() / 2f) - 1f
        canvas.drawText(abbrev, size / 2f, y, textPaint)

        return bitmap
    }
}

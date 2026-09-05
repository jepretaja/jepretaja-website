package com.jepretaja.app.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.jepretaja.app.core.util.CaptionParser

/**
 * Caption dengan hashtag dan sebutan yang bisa diketuk.
 *
 * Sebelumnya caption hanya teks mati: "#wedding" terbaca sebagai tagar oleh
 * manusia tapi tidak menuju ke mana-mana, dan menyebut nama creator lain sama
 * sekali tidak menghubungkan apa pun.
 *
 * [mentions] berisi pasangan nama -> creatorId yang tersimpan di dokumen post.
 * Hanya nama dalam daftar itu yang dijadikan tautan, supaya tidak ada teks
 * bergaya tautan yang ternyata mati saat diketuk.
 */
@Composable
fun CaptionText(
    caption: String,
    modifier: Modifier = Modifier,
    mentions: Map<String, String> = emptyMap(),
    onTagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
    color: Color = Color.White,
    linkColor: Color = Color(0xFF8AB4FF),
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
) {
    val teks = buildAnnotatedString {
        append(caption)
        CaptionParser.hashtagRanges(caption).forEach { (range, tag) ->
            addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.W600), range.first, range.last + 1)
            addStringAnnotation("TAG", tag, range.first, range.last + 1)
        }
        CaptionParser.mentionRanges(caption, mentions.keys).forEach { (range, nama) ->
            val creatorId = mentions[nama] ?: return@forEach
            // +1 karena batas akhir anotasi bersifat eksklusif, sementara
            // IntRange di sini inklusif — tanpa itu huruf terakhir nama tidak
            // ikut berwarna dan tidak ikut bisa diketuk.
            addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.W600), range.first, range.last + 1)
            addStringAnnotation("MENTION", creatorId, range.first, range.last + 1)
        }
    }

    ClickableText(
        text = teks,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        onClick = { offset ->
            teks.getStringAnnotations("TAG", offset, offset).firstOrNull()?.let { onTagClick(it.item); return@ClickableText }
            teks.getStringAnnotations("MENTION", offset, offset).firstOrNull()?.let { onMentionClick(it.item) }
        },
    )
}

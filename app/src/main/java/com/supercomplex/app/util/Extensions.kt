package com.supercomplex.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.supercomplex.app.data.BlockDetail
import com.supercomplex.app.data.BlockType

/** dp -> px */
fun Int.dp(context: Context): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics).toInt()

/** sp -> px */
fun Int.sp(context: Context): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this.toFloat(), context.resources.displayMetrics)

fun View.visible(v: Boolean = true) {
    visibility = if (v) View.VISIBLE else View.GONE
}

fun toast(context: Context, msg: String, long: Boolean = false) {
    Toast.makeText(context, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun openUrl(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    try {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    } catch (_: Throwable) { toast(context, "No browser available") }
}

fun blockColorForType(type: BlockType): Int = when (type) {
    BlockType.IMAGE -> Color.parseColor("#FFE5B4")
    BlockType.TEXT -> Color.parseColor("#E8E2D5")
    BlockType.LINK -> Color.parseColor("#F4D7E2")
    BlockType.MEDIA -> Color.parseColor("#D7E4F4")
    BlockType.ATTACHMENT -> Color.parseColor("#E4F4D7")
    BlockType.CHANNEL -> Color.parseColor("#F0E4F4")
    else -> Color.parseColor("#EFEFEF")
}

fun hexSubTypeColor(b: BlockDetail): Int? {
    val sub = b.subType ?: return null
    if (sub.typename == "HexColor" && !sub.value.isNullOrBlank()) {
        return try { Color.parseColor(sub.value) } catch (_: Throwable) { null }
    }
    return null
}

fun screenWidthDp(context: Context): Int {
    val dm = context.resources.displayMetrics
    return (dm.widthPixels / dm.density).toInt()
}

fun spanForWidth(context: Context, minCellDp: Int = 130): Int {
    val w = screenWidthDp(context)
    return (w / minCellDp).coerceAtLeast(2).coerceAtMost(6)
}

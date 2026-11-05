package com.apex.app.models

import androidx.annotation.StringRes
import com.apex.app.R

/**
 * Which link a meal share sends. Stored as text; mirrors iOS `MealShareLinkStyle` and
 * its "mealShareLinkStyle" key. No stored value means the user hasn't chosen yet, so the
 * first share asks once.
 */
enum class MealShareLinkStyle(val storageValue: String) {
    /** `apex.app/m/<id>`: a preview page first; the link expires after 7 days. The default. */
    SHORT("short"),
    /** `apex.app/add-meal?d=…`: opens APEX directly and carries the meals itself. */
    DIRECT("direct");

    @get:StringRes
    val titleRes: Int get() = if (this == DIRECT) R.string.share_link_direct else R.string.share_link_short

    @get:StringRes
    val detailRes: Int get() = if (this == DIRECT) R.string.share_link_direct_detail else R.string.share_link_short_detail

    companion object {
        val Default = SHORT

        fun fromStorage(value: String?): MealShareLinkStyle? = entries.firstOrNull { it.storageValue == value }
    }
}

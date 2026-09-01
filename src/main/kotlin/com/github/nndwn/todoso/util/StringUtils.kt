package com.github.nndwn.todoso.util

import java.util.*

fun String.toTitleCase(): String {
  return this.lowercase().replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
  }
}

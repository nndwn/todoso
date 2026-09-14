package com.github.nndwn.todoso.utils

import java.util.Locale

fun String.toTitleCase(): String {
  return this.lowercase().replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
  }
}

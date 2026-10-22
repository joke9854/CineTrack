package com.cinetrack.data.remote

/** Full TMDB image URL for a path such as "/abc.jpg" at a size such as "w500"; null when there is no path. */
fun tmdbImageUrl(path: String?, size: String): String? = path?.let { "https://image.tmdb.org/t/p/$size$it" }

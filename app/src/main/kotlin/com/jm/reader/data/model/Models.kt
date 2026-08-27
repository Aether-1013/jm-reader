package com.jm.reader.data.model

import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------
// JSON helpers (org.json based; the API responses are dynamic / loosely typed)
// ---------------------------------------------------------------------------

fun JSONObject.str(key: String, def: String = ""): String =
    if (has(key) && !isNull(key)) optString(key, def) else def

fun JSONObject.strOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key, null) else null

fun JSONObject.int(key: String, def: Int = 0): Int =
    if (has(key) && !isNull(key)) optInt(key, def) else def

fun JSONObject.long(key: String, def: Long = 0L): Long =
    if (has(key) && !isNull(key)) optLong(key, def) else def

fun JSONObject.bool(key: String, def: Boolean = false): Boolean =
    if (has(key) && !isNull(key)) optBoolean(key, def) else def

fun JSONObject.obj(key: String): JSONObject? =
    if (has(key) && !isNull(key)) optJSONObject(key) else null

fun JSONObject.objOrEmpty(key: String): JSONObject =
    obj(key) ?: JSONObject()

/** Reads a field that may be a string, a JSON array of strings, or absent. */
fun JSONObject.strList(key: String): List<String> {
    if (!has(key) || isNull(key)) return emptyList()
    return when (val v = opt(key)) {
        is JSONArray -> (0 until v.length()).map { v.optString(it) }.filter { it.isNotBlank() }
        is JSONObject -> emptyList()
        else -> {
            val s = v?.toString() ?: ""
            if (s.isNotBlank()) listOf(s) else emptyList()
        }
    }
}

/** Reads a field that may be a JSONObject, a JSON array, or absent (returns the list or empty). */
fun JSONObject.objList(key: String): List<JSONObject> {
    if (!has(key) || isNull(key)) return emptyList()
    val v = opt(key)
    if (v is JSONArray) {
        return (0 until v.length()).mapNotNull { v.optJSONObject(it) }
    }
    return emptyList()
}

/** Parses a raw body string to a JSONObject, or null if it is not a JSON object. */
fun String.toJsonObjectOrNull(): JSONObject? =
    runCatching { JSONObject(this) }.getOrNull()

// ---------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------

data class CategoryRef(
    val id: String? = null,
    val title: String? = null,
) {
    companion object {
        fun fromJson(o: JSONObject?): CategoryRef? {
            if (o == null) return null
            return CategoryRef(
                id = o.strOrNull("id"),
                title = o.strOrNull("title"),
            )
        }
    }
}

/** A comic row returned by list endpoints (latest / search / week / daily / categories / favorites). */
data class ComicListItem(
    val id: String = "",
    val name: String = "",
    val author: String? = null,
    val image: String = "",
    val category: CategoryRef? = null,
    val categorySub: CategoryRef? = null,
    val liked: Boolean = false,
    val isFavorite: Boolean = false,
    val updateAt: Long = 0L,
) {
    companion object {
        fun fromJson(o: JSONObject): ComicListItem = ComicListItem(
            id = o.str("id"),
            name = o.str("name"),
            author = o.strList("author").firstOrNull() ?: o.strOrNull("author"),
            image = o.str("image"),
            category = CategoryRef.fromJson(o.obj("category")),
            categorySub = CategoryRef.fromJson(o.obj("category_sub")),
            liked = o.bool("liked"),
            isFavorite = o.bool("is_favorite"),
            updateAt = o.long("update_at"),
        )
    }
}

/** A chapter (episode) inside a comic's `series` field. */
data class SeriesItem(
    val id: String = "",
    val sort: Int = 0,
    val name: String = "",
    val totalPage: Int = 0,
) {
    companion object {
        fun fromJson(o: JSONObject): SeriesItem = SeriesItem(
            id = o.str("id"),
            sort = o.int("sort"),
            name = o.str("name"),
            totalPage = o.int("total_page"),
        )
    }
}

/** Full comic detail from the `album` endpoint. */
data class ComicDetail(
    val id: String = "",
    val name: String = "",
    val authors: List<String> = emptyList(),
    val description: String = "",
    val addtime: Long = 0L,
    val totalViews: Long = 0L,
    val totalPhotos: Long = 0L,
    val likes: Long = 0L,
    val commentTotal: Long = 0L,
    val tags: List<String> = emptyList(),
    val series: List<SeriesItem> = emptyList(),
    val seriesId: String? = null,
    val works: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val relatedList: List<ComicListItem> = emptyList(),
    val liked: Boolean = false,
    val isFavorite: Boolean = false,
    val price: String? = null,
    val purchased: Boolean = false,
) {
    /** Paid content is content that has a price but has not been unlocked (purchased is "" or absent). */
    val isPaid: Boolean get() = !purchased && !price.isNullOrBlank()

    companion object {
        fun fromJson(o: JSONObject): ComicDetail = ComicDetail(
            id = o.str("id"),
            name = o.str("name"),
            authors = o.strList("author"),
            description = o.str("description"),
            addtime = o.long("addtime"),
            totalViews = o.long("total_views"),
            totalPhotos = o.long("total_photos"),
            likes = o.long("likes"),
            commentTotal = o.long("comment_total"),
            tags = o.strList("tags"),
            series = o.objList("series").map { SeriesItem.fromJson(it) },
            seriesId = o.strOrNull("series_id"),
            works = o.strList("works"),
            actors = o.strList("actors"),
            relatedList = o.objList("related_list").map { ComicListItem.fromJson(it) },
            liked = o.bool("liked"),
            isFavorite = o.bool("is_favorite"),
            price = o.strOrNull("price"),
            // `purchased` is a string in the API: "" or absent means not purchased.
            purchased = !o.strOrNull("purchased").isNullOrBlank(),
        )
    }
}

/** One page in a chapter from the `comic_read` endpoint. */
data class ReadPage(
    val page: Int = 0,
    val image: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): ReadPage = ReadPage(
            page = o.int("page"),
            image = o.str("image"),
        )
    }
}

/** Chapter read data from the `comic_read` endpoint. */
data class ReadData(
    val id: String = "",
    val name: String = "",
    val scrambleId: Long = 0L,
    val totalPage: Int = 0,
    val images: List<ReadPage> = emptyList(),
    val seriesId: String? = null,
) {
    companion object {
        fun fromJson(o: JSONObject): ReadData = ReadData(
            id = o.str("id"),
            name = o.str("name"),
            scrambleId = o.long("scramble_id"),
            totalPage = o.int("total_page"),
            images = o.objList("images").map { ReadPage.fromJson(it) },
            seriesId = o.strOrNull("series_id"),
        )
    }
}

/** Member / user data (returned by login and used as memberInfo). */
data class Member(
    val uid: String = "",
    val s: String = "",
    val username: String = "",
    val email: String = "",
    val photo: String = "",
    val nickName: String = "",
    val gender: String = "",
    val coin: Long = 0L,
    val level: String = "",
    val levelName: String = "",
    val adFree: Boolean = false,
    val charge: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): Member = Member(
            uid = o.str("uid"),
            s = o.str("s"),
            username = o.str("username"),
            email = o.str("email"),
            photo = o.str("photo"),
            nickName = o.str("nick_name"),
            gender = o.str("gender"),
            coin = o.long("coin"),
            level = o.str("level"),
            levelName = o.str("level_name"),
            adFree = o.bool("ad_free"),
            charge = o.str("charge"),
        )
    }
}

/** A tag (category tag / hot tag / tags_favorite item). */
data class TagItem(
    val id: String = "",
    val title: String = "",
    val count: Long = 0L,
) {
    companion object {
        fun fromJson(o: JSONObject): TagItem = TagItem(
            id = o.str("id").ifBlank { o.str("tag") },
            title = o.str("title").ifBlank { o.str("tag") },
            count = o.long("count"),
        )
    }
}

/** A category (from the `categories` endpoint). `slug` is used as the `c` filter param. */
data class Category(
    val slug: String = "",
    val title: String = "",
    val subCategories: List<Category> = emptyList(),
) {
    companion object {
        fun fromJson(o: JSONObject): Category = Category(
            slug = o.str("slug").ifBlank { o.str("id") },
            title = o.str("name").ifBlank { o.str("title") },
            subCategories = o.objList("sub_categories").map { fromJson(it) },
        )
    }
}

/** A novel list item (from `novels` endpoint). */
data class NovelItem(
    val id: String = "",
    val name: String = "",
    val author: String? = null,
    val image: String = "",
    val updateAt: Long = 0L,
) {
    companion object {
        fun fromJson(o: JSONObject): NovelItem = NovelItem(
            id = o.str("id"),
            name = o.str("name"),
            author = o.strList("author").firstOrNull() ?: o.strOrNull("author"),
            image = o.str("image"),
            updateAt = o.long("update_at"),
        )
    }
}

/** A movie / video list item (from `videos` endpoint). */
data class MovieItem(
    val id: String = "",
    val title: String = "",
    val photo: String = "",
    val tags: List<String> = emptyList(),
) {
    val image: String get() = photo

    companion object {
        fun fromJson(o: JSONObject): MovieItem = MovieItem(
            id = o.str("id"),
            title = o.str("title"),
            photo = o.str("photo"),
            tags = o.strList("tags"),
        )
    }
}

/** A game list item (from `allgames` endpoint). */
data class GameItem(
    val id: String = "",
    val name: String = "",
    val image: String = "",
    val link: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): GameItem = GameItem(
            id = o.str("gid").ifBlank { o.str("id") },
            name = o.str("title").ifBlank { o.str("name") },
            image = o.str("photo").ifBlank { o.str("image") },
            link = o.str("link"),
        )
    }
}

/** A blog list item (from `blogs` endpoint). */
data class BlogItem(
    val id: String = "",
    val title: String = "",
    val image: String = "",
    val updateAt: Long = 0L,
) {
    companion object {
        fun fromJson(o: JSONObject): BlogItem = BlogItem(
            id = o.str("id"),
            title = o.str("title"),
            image = o.str("image").ifBlank { o.str("photo") },
            updateAt = o.long("update_at"),
        )
    }
}

/** A forum post (from `forum` endpoint). */
data class ForumItem(
    val cid: String = "",
    val nickname: String = "",
    val content: String = "",
    val addtime: String = "",
    val replyCount: Int = 0,
) {
    companion object {
        fun fromJson(o: JSONObject): ForumItem = ForumItem(
            cid = o.str("CID").ifBlank { o.str("id") },
            nickname = o.str("nickname"),
            content = o.str("content"),
            addtime = o.str("addtime"),
            replyCount = o.objList("replys").size,
        )
    }
}

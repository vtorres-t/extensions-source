package eu.kanade.tachiyomi.extension.es.emperorscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

@Serializable
data class InertiaPage<T>(
    val component: String,
    val props: T,
    val version: String? = null, // Captura de manera nativa el token dinámico del sitio web
)

@Serializable
data class ListingProps(
    val series: List<MangaDto>,
    val pagination: PaginationDto,
)

@Serializable
data class PaginationDto(val next: String?)

@Serializable
data class MangaDto(
    val url: String,
    val title: String,
    val cover: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = this@MangaDto.url
        title = this@MangaDto.title
        thumbnail_url = cover
    }
}

@Serializable
data class SeriesProps(
    val series: SeriesDto,
    val chapters: List<ChapterDto>,
    @SerialName("chapters_pagination") val chaptersPagination: ChaptersPaginationDto,
)

@Serializable
data class SeriesDto(
    val url: String,
    val title: String,
    val type: String?,
    val status: String?,
    @SerialName("cover_canonical") val cover: String?,
    @SerialName("alt_titles") val altTitles: List<String>,
    val synopsis: String?,
    val genres: List<NamedDto>,
    val authors: List<NamedDto>,
    val artists: List<NamedDto>,
) {
    fun applyTo(manga: SManga) = manga.apply {
        title = this@SeriesDto.title
        thumbnail_url = cover
        author = authors.joinToString { it.name }.ifEmpty { null }
        artist = artists.joinToString { it.name }.ifEmpty { null }
        description = buildString {
            synopsis?.let(::append)
            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Nombres alternativos: ", altTitles.joinToString())
            }
        }.ifEmpty { null }
        genre = (listOfNotNull(type?.replaceFirstChar(Char::uppercase)) + genres.map { it.name }).joinToString().ifEmpty { null }
        status = when (this@SeriesDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
data class NamedDto(val slug: String, val name: String)

@Serializable
data class ChaptersPaginationDto(val last: Int)

@Serializable
data class ChapterListDto(val items: List<ChapterDto>)

@Serializable
data class ChapterDto(
    val slug: String,
    val label: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val locked: Boolean,
) {
    fun toSChapter(seriesUrl: String) = SChapter.create().apply {
        url = "$seriesUrl$slug/"
        name = label
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
data class ReaderProps(
    val chapter: ReaderChapterDto,
    val series: ReaderSeriesDto,
    val pages: List<PageDto>,
    val dims: List<JsonElement>,
    val reader: JsonElement,
)

@Serializable
data class ReaderChapterDto(val id: Int)

@Serializable
data class ReaderSeriesDto(val slug: String)

@Serializable
data class PageDto(
    val n: Int,
    val src: String? = null,
    val srcset: String,
) {
    /** Full-size image, else the widest srcset entry */
    val imageUrl get() = src ?: srcset.split(", ").last().substringBefore(' ')
}

@Serializable
data class ReaderInput(
    val entry: String,
    val reader: JsonElement,
    val chapterId: Int,
    val seriesSlug: String,
)

@Serializable
data class ReaderResult(
    val pages: List<PageDto>? = null,
    val error: String? = null,
)

@Serializable
data class HomeProps(val genres: List<NamedDto>)

@Serializable
data class InertiaResponseDto(
    val props: ReaderPropsDto,
)

@Serializable
data class ReaderPropsDto(
    val pages: List<PageDto>,
)

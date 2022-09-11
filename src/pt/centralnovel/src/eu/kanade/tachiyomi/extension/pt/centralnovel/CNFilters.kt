package eu.kanade.tachiyomi.extension.pt.centralnovel

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

object CNFilters {

    open class QueryPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray()
    ) {

        fun toQueryPart() = vals[state].second
    }

    open class CheckBoxFilterList(name: String, values: List<CheckBox>) : Filter.Group<Filter.CheckBox>(name, values)
    private class CheckBoxVal(name: String, state: Boolean = false) : Filter.CheckBox(name, state)

    private inline fun <reified R> FilterList.getFirst(): R {
        return this.filterIsInstance<R>().first()
    }

    private inline fun <reified R> FilterList.asQueryPart(): String {
        return this.filterIsInstance<R>().joinToString("") {
            (it as QueryPartFilter).toQueryPart()
        }
    }

    class OrderFilter : QueryPartFilter("Ordenar por", CNFiltersData.orders)
    class StatusFilter : QueryPartFilter("Status", CNFiltersData.status)

    class GenresFilter : CheckBoxFilterList(
        "Gêneros",
        CNFiltersData.genres.map { CheckBoxVal(it.first, false) }
    )

    class TypesFilter : CheckBoxFilterList(
        "Tipo",
        CNFiltersData.types.map { CheckBoxVal(it.first, false) }
    )

    val filterList = FilterList(
        OrderFilter(),
        StatusFilter(),
        TypesFilter(),
        GenresFilter()
    )

    data class FilterSearchParams(
        val order: String = "",
        val status: String = "",
        val types: List<String> = emptyList<String>(),
        val genres: List<String> = emptyList<String>()
    )

    private inline fun <reified R> FilterList.parseCheckbox(
        array: Array<Pair<String, String>>
    ): List<String> {
        val items = (this.getFirst<R>() as CheckBoxFilterList)
            .state
            .mapNotNull { item ->
                if (item.state) {
                    array.find { it.first == item.name }!!.second
                } else { null }
            }.toList()
        return items
    }

    internal fun getSearchParameters(filters: FilterList): FilterSearchParams {
        return FilterSearchParams(
            filters.asQueryPart<OrderFilter>(),
            filters.asQueryPart<StatusFilter>(),
            filters.parseCheckbox<TypesFilter>(CNFiltersData.types),
            filters.parseCheckbox<GenresFilter>(CNFiltersData.genres)
        )
    }

    private object CNFiltersData {
        val every = Pair("Qualquer um", "")

        val types = arrayOf(
            every,
            Pair("Light Novel", "light-novel"),
            Pair("Novel Chinesa", "novel-chinesa"),
            Pair("Novel Coreana", "novel-coreana"),
            Pair("Novel Japonesa", "novel-japonesa"),
            Pair("Novel Ocidental", "novel-ocidental"),
            Pair("Webnovel", "webnovel")
        )

        val status = arrayOf(
            every,
            Pair("Em andamento", "em andamento"),
            Pair("Hiato", "hiato"),
            Pair("Completo", "completo")
        )

        val orders = arrayOf(
            Pair("Padrão", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Últ. Att", "update"),
            Pair("Últ. Add", "latest"),
            Pair("Populares", "popular")
        )

        val genres = arrayOf(
            every,
            Pair("Ação", "acao"),
            Pair("Adulto", "adulto"),
            Pair("Adventure", "adventure"),
            Pair("Artes Marciais", "artes-marciais"),
            Pair("Aventura", "aventura"),
            Pair("Comédia", "comedia"),
            Pair("Comedy", "comedy"),
            Pair("Cotidiano", "cotidiano"),
            Pair("Cultivo", "cultivo"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Escolar", "escolar"),
            Pair("Esportes", "esportes"),
            Pair("Fantasia", "fantasia"),
            Pair("Ficção Científica", "ficcao-cientifica"),
            Pair("Harém", "harem"),
            Pair("Isekai", "isekai"),
            Pair("Magia", "magia"),
            Pair("Mecha", "mecha"),
            Pair("Medieval", "medieval"),
            Pair("Mistério", "misterio"),
            Pair("Mitologia", "mitologia"),
            Pair("Monstros", "monstros"),
            Pair("Pet", "pet"),
            Pair("Protagonista Feminina", "protagonista-feminina"),
            Pair("Protagonista Maligno", "protagonista-maligno"),
            Pair("Psicológico", "psicologico"),
            Pair("Reencarnação", "reencarnacao"),
            Pair("Romance", "romance"),
            Pair("Seinen", "seinen"),
            Pair("Shounen", "shounen"),
            Pair("Sistema", "sistema"),
            Pair("Sistema de Jogo", "sistema-de-jogo"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Sobrenatural", "sobrenatural"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragédia", "tragedia"),
            Pair("Vida Escolar", "vida-escolar"),
            Pair("VRMMO", "vrmmo"),
            Pair("Xianxia", "xianxia"),
            Pair("Xuanhuan", "xuanhuan")
        )
    }
}

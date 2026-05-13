package com.example.shoppingassistant.domain.catalog

import java.util.Locale

internal data class CatalogL0PackageContract(
    val segment: CategorySegment,
) {
    val l0Code: String
        get() = segment.name

    val browseRootCode: String
        get() = "B.$l0Code"
}

internal object CatalogL0Registry {
    val requiredPackages: List<CatalogL0PackageContract> = listOf(
        CatalogL0PackageContract(CategorySegment.TECH),
        CatalogL0PackageContract(CategorySegment.APPL),
        CatalogL0PackageContract(CategorySegment.HOME),
        CatalogL0PackageContract(CategorySegment.FASH),
        CatalogL0PackageContract(CategorySegment.BEAUTY),
        CatalogL0PackageContract(CategorySegment.KIDS),
        CatalogL0PackageContract(CategorySegment.FOOD),
        CatalogL0PackageContract(CategorySegment.PETS),
        CatalogL0PackageContract(CategorySegment.SPORT),
        CatalogL0PackageContract(CategorySegment.AUTO),
    )

    val requiredPackageCodes: List<String> = requiredPackages.map { it.l0Code }

    val requiredBrowseRootCodes: Set<String> = requiredPackages
        .mapTo(linkedSetOf()) { it.browseRootCode }

    private val segmentByCode: Map<String, CategorySegment> = requiredPackages.associate { pkg ->
        pkg.l0Code.uppercase(Locale.ROOT) to pkg.segment
    }

    fun segmentForL0Code(l0Code: String): CategorySegment? =
        segmentByCode[l0Code.trim().uppercase(Locale.ROOT)]
}

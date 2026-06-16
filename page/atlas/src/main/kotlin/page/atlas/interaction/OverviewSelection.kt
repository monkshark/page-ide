package page.atlas.interaction

data class OverviewSelection(
    val kind: Kind = Kind.NONE,
    val moduleId: String? = null,
    val pathTarget: String? = null,
    val drillPath: List<String> = emptyList(),
) {
    enum class Kind { NONE, MODULE, PATH }

    fun selectModule(id: String): OverviewSelection =
        copy(kind = Kind.MODULE, moduleId = id, pathTarget = null)

    fun clear(): OverviewSelection =
        copy(kind = Kind.NONE, moduleId = null, pathTarget = null)

    fun tracePath(target: String): OverviewSelection =
        if (moduleId == null || target == moduleId) this
        else copy(kind = Kind.PATH, pathTarget = target)

    fun clearPath(): OverviewSelection =
        if (moduleId == null) clear() else copy(kind = Kind.MODULE, pathTarget = null)

    fun drillInto(id: String): OverviewSelection =
        OverviewSelection(drillPath = drillPath + id)

    fun drillUpTo(depth: Int): OverviewSelection =
        OverviewSelection(drillPath = drillPath.take(depth.coerceIn(0, drillPath.size)))

    fun drillUp(): OverviewSelection =
        if (drillPath.isEmpty()) this else drillUpTo(drillPath.size - 1)

    companion object {
        val NONE = OverviewSelection()
    }
}

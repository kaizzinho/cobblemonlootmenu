package dev.cobblemonlootmenu.compat

object CompatDiagnostics {
    data class Integration(
        var installed: Boolean = false,
        var enabled: Boolean = false,
        var registered: Boolean = false
    )

    data class Rct(
        var victoriesObserved: Long = 0,
        var trainerVictoriesObserved: Long = 0,
        var capturedStacks: Long = 0,
        var emptyWatches: Long = 0,
        var activeWatches: Int = 0,
        var lastTrainerName: String = "-",
        var lastCaptureSize: Int = 0
    )

    data class WildBosses(
        var awardsObserved: Long = 0,
        var stashesCreated: Long = 0,
        var mergedAwards: Long = 0,
        var activeStashes: Int = 0,
        var lastBossName: String = "-"
    )

    val wildBossesIntegration = Integration()
    val rctIntegration = Integration()
    val rct = Rct()
    val wildBosses = WildBosses()
}

package dev.suprex.subwaycoders.stats

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.Alarm
import com.intellij.util.messages.Topic
import java.time.LocalDate

fun interface WatchTimeListener {
    fun watchTimeChanged()

    companion object {
        @Topic.AppLevel
        val TOPIC = Topic(WatchTimeListener::class.java)
    }
}

@State(name = "SubwayCodersWatchTime", storages = [Storage("subwayCodersStats.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
class WatchTimeTracker : PersistentStateComponent<WatchTimeTracker.StatsState>, Disposable {

    class StatsState {
        var day: String = ""
        var todayMs: Long = 0
        var totalMs: Long = 0
    }

    private var state = StatsState()
    private val watching = HashSet<String>()
    private var since = 0L
    private val ticker = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    override fun getState(): StatsState {
        flush()
        return state
    }

    override fun loadState(loaded: StatsState) {
        state = loaded
    }

    override fun dispose() = Unit

    fun setWatching(key: String, active: Boolean) {
        val wasActive = watching.isNotEmpty()
        if (active) watching += key else watching -= key
        if (wasActive == watching.isNotEmpty()) return
        if (wasActive) {
            flush()
            ticker.cancelAllRequests()
        } else {
            since = System.currentTimeMillis()
            tick()
        }
        notifyChanged()
    }

    fun todayMs(): Long = (if (state.day == today()) state.todayMs else 0) + runningMs()

    fun totalMs(): Long = state.totalMs + runningMs()

    private fun runningMs(): Long = if (watching.isEmpty()) 0 else System.currentTimeMillis() - since

    private fun flush() {
        if (watching.isEmpty()) return
        val now = System.currentTimeMillis()
        val today = today()
        if (state.day != today) {
            state.day = today
            state.todayMs = 0
        }
        state.todayMs += now - since
        state.totalMs += now - since
        since = now
    }

    private fun tick() {
        ticker.addRequest({
            notifyChanged()
            tick()
        }, TICK_MS)
    }

    private fun notifyChanged() =
        ApplicationManager.getApplication().messageBus.syncPublisher(WatchTimeListener.TOPIC).watchTimeChanged()

    private fun today() = LocalDate.now().toString()

    companion object {
        private const val TICK_MS = 30_000

        val instance: WatchTimeTracker get() = service()

        fun format(ms: Long): String {
            val minutes = ms / 60_000
            return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
        }
    }
}

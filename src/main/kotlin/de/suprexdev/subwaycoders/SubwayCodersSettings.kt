package de.suprexdev.subwaycoders

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.XMap

@State(name = "SubwayCodersSettings", storages = [Storage("subwayCoders.xml")])
@Service(Service.Level.APP)
class SubwayCodersSettings : PersistentStateComponent<SubwayCodersSettings.State> {

    class WindowConfig {
        var categoryName: String = ""
        var customUrl: String = ""
        var muted: Boolean = true
        var controlsHidden: Boolean = false
    }

    class State {
        @XMap
        var windows: MutableMap<String, WindowConfig> = HashMap()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
    }

    fun configFor(windowId: String, defaultCategory: String, defaultMuted: Boolean): WindowConfig =
        state.windows.getOrPut(windowId) {
            WindowConfig().apply {
                categoryName = defaultCategory
                muted = defaultMuted
            }
        }

    companion object {
        val instance: SubwayCodersSettings get() = service()
    }
}

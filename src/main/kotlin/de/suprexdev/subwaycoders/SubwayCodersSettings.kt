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
        var controlsHidden: Boolean = false
        var doomscrollEnabled: Boolean = false
    }

    class State {
        @XMap
        var windows: MutableMap<String, WindowConfig> = HashMap()

        // Global (not per-window): snap back to the terminal when Claude Code needs attention.
        var claudeFocusEnabled: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
    }

    var claudeFocusEnabled: Boolean
        get() = state.claudeFocusEnabled
        set(value) {
            state.claudeFocusEnabled = value
        }

    fun configFor(windowId: String, defaultCategory: String): WindowConfig =
        state.windows.getOrPut(windowId) {
            WindowConfig().apply {
                categoryName = defaultCategory
            }
        }

    companion object {
        val instance: SubwayCodersSettings get() = service()
    }
}

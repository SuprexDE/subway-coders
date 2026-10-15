package dev.suprex.subwaycoders.stats

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import java.awt.Component

class WatchTimeWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WatchTimeWidget.ID

    override fun getDisplayName(): String = "Subway Coders Watch Time"

    override fun createWidget(project: Project): StatusBarWidget = WatchTimeWidget()
}

class WatchTimeWidget : StatusBarWidget, StatusBarWidget.TextPresentation {

    private val tracker get() = WatchTimeTracker.instance

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(WatchTimeListener.TOPIC, WatchTimeListener { statusBar.updateWidget(ID) })
    }

    override fun getText(): String = "Brainrot today: ${WatchTimeTracker.format(tracker.todayMs())}"

    override fun getTooltipText(): String =
        "Subway Coders watch time: ${WatchTimeTracker.format(tracker.todayMs())} today, " +
            "${WatchTimeTracker.format(tracker.totalMs())} in total"

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun dispose() = Unit

    companion object {
        const val ID = "SubwayCodersWatchTime"
    }
}

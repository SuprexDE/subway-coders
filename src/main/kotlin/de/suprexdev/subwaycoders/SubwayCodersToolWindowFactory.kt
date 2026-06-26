package de.suprexdev.subwaycoders

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class SubwayCodersToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = VideoConfigService.instance
        service.ensureUserFile()

        val categories = service.config.categories.map { it.name }
        val preferred = DEFAULT_CATEGORY[toolWindow.id]
        val defaultCategory = preferred?.takeIf { it in categories } ?: categories.firstOrNull().orEmpty()

        val panel = SubwayCodersPanel(project, toolWindow.id, defaultCategory, defaultMuted = !service.config.sound)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    private companion object {
        val DEFAULT_CATEGORY = mapOf(
            "Subway Coders Left" to "Minecraft Parkour",
            "Subway Coders Right" to "Subway Surfers",
            "Subway Coders Top" to "Temple Run",
            "Subway Coders Bottom" to "Minecraft Story",
        )
    }
}

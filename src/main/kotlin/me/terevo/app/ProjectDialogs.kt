package me.terevo.app

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import me.terevo.domain.port.ProjectLocation

class ProjectDialogs(
    private val owner: Frame?,
) {
    fun chooseCreate(): ProjectLocation? = choose(FileDialog.SAVE, "Создать проект")?.withExtension()

    fun chooseOpen(): ProjectLocation? = choose(FileDialog.LOAD, "Открыть проект")

    private fun choose(mode: Int, title: String): ProjectLocation? {
        val dialog = FileDialog(owner, title, mode)
        dialog.file = "*.${ProjectLocation.EXTENSION}"
        dialog.isVisible = true
        val fileName = dialog.file ?: return null
        return ProjectLocation(File(dialog.directory, fileName).absolutePath)
    }

    private fun ProjectLocation.withExtension(): ProjectLocation =
        if (path.endsWith(".${ProjectLocation.EXTENSION}", ignoreCase = true)) this
        else ProjectLocation("$path.${ProjectLocation.EXTENSION}")
}

package me.terevo.testing

import me.terevo.domain.Outcome
import me.terevo.domain.command.AddPerson
import me.terevo.domain.command.CommandBus
import me.terevo.domain.port.ProjectLocation
import me.terevo.persistence.SqliteProjectService

fun main(args: Array<String>) {
    val location = ProjectLocation(args[0])
    val project = when (val created = SqliteProjectService().create(location)) {
        is Outcome.Ok -> created.value
        is Outcome.Err -> {
            println("FAILED ${created.error}")
            System.out.flush()
            return
        }
    }
    val bus = CommandBus(repository = project.repository)
    var index = 0
    while (true) {
        val written = bus.execute(AddPerson(person(surname = "Человек$index")))
        if (written is Outcome.Err) {
            println("FAILED ${written.error}")
            System.out.flush()
            return
        }
        println(index)
        System.out.flush()
        index++
    }
}

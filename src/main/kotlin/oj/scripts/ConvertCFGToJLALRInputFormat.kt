package oj.scripts

import oj.models.CFG
import java.io.File

fun main(args: Array<String>) {
    val cfgName = "joos"
    val cfg : CFG = CFG.deserialize("cfg/$cfgName.cfg")

    var str = ""

    val write = { line: Any ->
        str += "$line\n"
    }

    write(cfg.terminals.size)
    cfg.terminals.forEach({ write(it) })

    write(cfg.nonTerminals.size)
    cfg.nonTerminals.forEach({ write(it) })

    write("S")
    write(cfg.rules.values.map{ it.size }.reduce({a, b -> a + b}))
    cfg.rules.forEach({ nonTerminal, expansions ->
        expansions.forEach({ rhs ->
            write("$nonTerminal ${rhs.joinToString(" ")}".trim())
        })
    })

    File("gen/joos-jlalr-input-format.cfg").bufferedWriter().use { it.write(str) }
}
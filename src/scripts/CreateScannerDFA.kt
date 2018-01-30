package scripts

import models.NFA
import models.NFAType
import scanner.SCANNER_DFA

fun main(args: Array<String>) {
    var baseDfas = NFAType.values().map {
        NFA.deserialize("gen/${it.fileName}.dfa", scanner.ALPHABET, it)
    }
    baseDfas.reduce{ nfa, dfa -> nfa.or(dfa) }.toDFA().serialize("gen/$SCANNER_DFA.dfa")
}
package scripts

import models.NFA
import scanner.BASE_DFA_NAMES
import scanner.SCANNER_DFA

fun main(args: Array<String>) {
    var baseDfas = BASE_DFA_NAMES.keys.map {
        NFA.deserialize("gen/$it.dfa", scanner.ALPHABET, it)
    }
    baseDfas.reduce{ nfa, dfa -> nfa.or(dfa) }.toDFA().serialize("gen/$SCANNER_DFA.dfa")
}
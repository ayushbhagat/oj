package oj.scripts

import oj.models.NFA
import oj.scanner.BASE_DFA_NAMES
import oj.scanner.SCANNER_DFA

fun main(args: Array<String>) {
    var baseDfas = BASE_DFA_NAMES.keys.map {
        NFA.deserialize("gen/$it.dfa", oj.scanner.ALPHABET, it)
    }
    baseDfas.reduce{ nfa, dfa -> nfa.or(dfa) }.toDFA().serialize("gen/$SCANNER_DFA.dfa")
}

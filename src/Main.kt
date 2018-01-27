import models.NFA

fun main(args: Array<String>) {
    val commentsDFA = NFA.new("dfa/comments.dfa")
    val identifiersDFA = NFA.new("dfa/identifiers.dfa")
    val characterDFA = NFA.new("dfa/character.dfa")
    val stringDFA = NFA.new("dfa/string.dfa")
    val integerDFA = NFA.new("dfa/integer.dfa")
    val operatorsDFA = NFA.new("dfa/operators.dfa")
    println("Comments DFA: " + commentsDFA.toString())
    println("Identifiers DFA: " + identifiersDFA.toString())
    println("Character DFA: " + characterDFA.toString())
    println("String DFA: " + stringDFA.toString())
    println("Integer DFA: " + integerDFA.toString())
    println("Operators DFA: " + operatorsDFA.toString())
}
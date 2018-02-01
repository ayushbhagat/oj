package models

import scanner.ScannerError
import java.io.File

data class NFA(
        val states: MutableSet<String>,
        val startState: String,
        val finalStates: MutableSet<String>,
        val transitionFn: MutableMap<String, MutableMap<String, MutableSet<String>>>,
        val alphabet: Set<String>,
        val name: String = "",
        val isDfa: Boolean = false) {
    companion object {
        /**
         * Returns the NFA created from the file at the provided file path and the type.
         * @param filePath The file path that contains the serialization of an NFA. Assumes that
         *      the file name has a ".dfa" extension.
         * @param alphabet The alphabet over which the NFA will run.
         * @param name The name of the NFA.
         * @return The deserialized NFA.
         */
        fun deserialize(filePath: String, alphabet: Set<String>, name: String): NFA {
            // Read the DFA file.
            var lineList: MutableList<String> = mutableListOf()
            val inputStream = File(filePath).inputStream()
            inputStream.bufferedReader().useLines { lines -> lines.forEach { lineList.add(it) }}

            // Add states on the fly.
            val states: MutableSet<String> = mutableSetOf()

            // First line is the start state.
            val fileName = filePath.substring(
                    filePath.lastIndexOf("/") + 1,
                    filePath.lastIndexOf(".dfa"))
            val startState = fileName + lineList[0]
            states.add(startState)

            // Second line contains a list of states.
            val finalStates: MutableSet<String> = mutableSetOf()
            lineList[1].split(" ").forEach { finalStates.add(fileName + it) }

            // Add the transitions.
            val transitionFn: MutableMap<String, MutableMap<String, MutableSet<String>>> =
                    mutableMapOf()
            lineList.drop(2).forEach {
                // Instead of using split, separate it by indexOf space so that the regex in the
                // transition can contain space.
                val firstSpaceIndex = it.indexOf(" ")
                val fromState = fileName + it.substring(0, firstSpaceIndex)
                val secondSpaceIndex = firstSpaceIndex + it.substring(firstSpaceIndex + 1)
                        .indexOf(" ") + 1
                val toState = fileName + it.substring(firstSpaceIndex + 1, secondSpaceIndex)
                val transition = it.substring(secondSpaceIndex + 1)
                val transitionsTo =
                        if (transitionFn.contains(fromState)) transitionFn[fromState]
                        else mutableMapOf()
                val toStateSet =
                        if (transitionsTo?.contains(transition) == true) transitionsTo[transition]
                        else mutableSetOf()
                toStateSet?.add(toState)
                transitionsTo?.put(transition, toStateSet.orEmpty().toMutableSet())
                transitionFn[fromState] = transitionsTo.orEmpty().toMutableMap()
                states.add(fromState)
                states.add(toState)
            }

            return NFA(states, startState, finalStates, transitionFn, alphabet, name)
        }
    }

    /**
     * Serialize the NFA to the provided file path.
     * @param filePath The file path to write the serialization of NFA to.
     * @return The serialized string.
     */
    fun serialize(filePath: String): String {
        var serialization = "$startState\n"
        serialization += "${finalStates.joinToString(" ")}\n"
        serialization += ""
        var transitionsString = ""
        transitionFn.keys.forEach { fromState ->
            transitionFn[fromState]?.keys?.forEach { transition ->
                transitionFn[fromState]?.get(transition)?.forEach { toState ->
                    transitionsString += "$fromState $toState $transition\n"
                }
            }
        }
        serialization += transitionsString
        File(filePath).bufferedWriter().use { it.write(serialization) }
        return serialization
    }

    /**
     * Returns a new NFA that is the OR of this NFA and the other that is passed.
     * @param other The NFA to OR this NFA with.
     * @return The NFA is the result of the OR.
     */
    fun or(other: NFA): NFA {
        val newStartState = "${startState}_${other.startState}"
        val newStates = (states.union(other.states)).toMutableSet()
        newStates.add(newStartState)
        val newFinalStates = (finalStates.union(other.finalStates)).toMutableSet()
        val newTransitionFn: MutableMap<String, MutableMap<String, MutableSet<String>>> =
                HashMap(transitionFn)
        newTransitionFn.putAll(other.transitionFn)
        newTransitionFn[newStartState] =
                mutableMapOf("" to mutableSetOf(startState, other.startState))
        return NFA(newStates, newStartState, newFinalStates, newTransitionFn, alphabet)
    }

    /**
     * Uses subset construction to convert the NFA to a DFA and returns it.
     * @return The corresponding DFA.
     */
    fun toDFA(): NFA {
        var newStates: MutableSet<String> = mutableSetOf()
        var newStartState: String
        val newFinalStates: MutableSet<String> = mutableSetOf()
        val newTransitionFn: MutableMap<String, MutableMap<String, MutableSet<String>>> =
                mutableMapOf()
        val startStateEpsilonClosure = getEpsilonClosure(setOf(startState))
        newStartState = startStateEpsilonClosure.joinToString("_")
        newStates.add(newStartState)
        if (!startStateEpsilonClosure.intersect(finalStates).isEmpty()) {
            newFinalStates.add(newStartState)
        }
        // List of sets, where each set is a state.
        val workList = mutableListOf(startStateEpsilonClosure)
        while (!workList.isEmpty()) {
            val currentStates = workList[workList.size - 1]
            val currentStatesString = currentStates.joinToString("_")
            workList.remove(currentStates)
            for (a in alphabet) {
                var newState: MutableSet<String> = mutableSetOf()
                for (fromState in currentStates) {
                    for ((transition, toStates) in transitionFn[fromState].orEmpty()) {
                        if (transition == a) {
                            newState.addAll(toStates)
                        }
                    }
                }
                if (!newState.isEmpty()) {
                    newState = getEpsilonClosure(newState).toMutableSet()
                    val newStateString = newState.joinToString("_")
                    // Create a state out of the current states (this is the from state), transition
                    // is a, and create a state out of the new state (this is the to state).
                    val transitionsTo =
                            if (newTransitionFn.contains(currentStatesString))
                                newTransitionFn[currentStatesString]
                            else mutableMapOf()
                    transitionsTo?.put(a, mutableSetOf(newStateString))
                    newTransitionFn[currentStatesString] = transitionsTo.orEmpty().toMutableMap()
                    if (!newStates.contains(newStateString)) {
                        newStates.add(newStateString)
                        workList.add(newState)
                        if (!newState.intersect(finalStates).isEmpty()) {
                            newFinalStates.add(newStateString)
                        }
                    }
                }
            }
        }
        return NFA(newStates, newStartState, newFinalStates, newTransitionFn, alphabet, "", true)
    }

    /**
     * Consume the character and move the DFA forward. If the NFA is not a DFA, don't do anything.
     * If reached an error state (i.e. cannot move from the current state on the given character),
     * return an empty string.
     * @param currentState The current state the DFA is in.
     * @param transition The character in the alphabet the DFA is supposed to read.
     */
    fun getNextState(currentState: String, transition: String): String {
        if (!isDfa || !states.contains(currentState)) {
            throw ScannerError()
        }
        return transitionFn[currentState]?.get(transition)?.first().orEmpty()
    }

    /**
     * Returns whether or not the given state is a final state.
     * @param state The state to check.
     * @return True if the state is a final state, and false otherwise.
     */
    fun isFinalState(state: String): Boolean {
        return finalStates.contains(state)
    }

    /**
     * Returns the epsilon closure of a set of states.
     * @param states The states of which epsilon closure to find.
     * @return The epsilon closure of the given states.
     */
    private fun getEpsilonClosure(states: Set<String>): Set<String> {
        val workList = ArrayList(states)
        val resultSet = HashSet(states)
        while (!workList.isEmpty()) {
            val fromState = workList[workList.size - 1]
            workList.remove(fromState)
            for (toState in transitionFn[fromState]?.get("").orEmpty()) {
                if (!resultSet.contains(toState)) {
                    resultSet.add(toState)
                    workList.add(toState)
                }
            }
        }
        return resultSet
    }
}
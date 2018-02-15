package oj.models

import java.io.File

class NFAError(reason: String): Exception(reason)

data class NFA(
        val states: Set<State>,
        val startState: State,
        val finalStates: Set<State>,
        val transitionFn: Map<State, Map<String, Set<State>>>,
        val stateDataHelper: StateDataHelper,
        val alphabet: Set<String>,
        val name: String = "") {
    companion object {
        /**
         * Returns the NFA created from the file at the provided file path and the type.
         * @param filePath The file path that contains the serialization of an NFA. Assumes that
         *      the file name has a ".dfa" extension.
         * @param alphabet The alphabet over which the NFA will run.
         * @param stateDataHelper The helper class that knows how to serialize the state data.
         * @param name The name of the NFA.
         * @return The deserialized NFA.
         */
        fun deserialize(
                filePath: String,
                stateDataHelper: StateDataHelper,
                alphabet: Set<String>,
                name: String): NFA {
            // Read the DFA file.
            val lines = File(filePath).readLines()

            // First line is the number of states.
            val numStates = lines[0].toInt()

            // Next number of states line is the states with their data.
            val fileName = filePath.substring(
                    filePath.lastIndexOf("/") + 1,
                    filePath.lastIndexOf(".dfa"))
            val statesByName: MutableMap<String, State> = mutableMapOf()
            val states = lines.subList(1, numStates + 1).map {
                val firstSpaceIndex =
                        if (it.indexOf(" ") >= 0) it.indexOf(" ") else it.length
                val stateName = fileName + it.substring(0, firstSpaceIndex)
                val stateData = stateDataHelper.deserialize(
                        if (firstSpaceIndex < it.length) it.substring(firstSpaceIndex + 1)
                        else "")
                val state = State(stateName, stateData)
                statesByName[stateName] = state
                state
            }.toSet()

            // Next line is the start state.
            val startState = statesByName[fileName + lines[numStates + 1]]!!

            // Next line contains a list of final states.
            val finalStates = lines[numStates + 2]
                    .split(" ")
                    .map { statesByName[fileName + it]!! }
                    .toSet()

            // Add the transitions.
            val transitionFn: MutableMap<State, MutableMap<String, MutableSet<State>>> =
                    mutableMapOf()
            lines.drop(numStates + 3).forEach {
                // Instead of using split, separate it by indexOf space so that the regex in the
                // transition can contain space.
                val firstSpaceIndex = it.indexOf(" ")
                val secondSpaceIndex = firstSpaceIndex + it.substring(firstSpaceIndex + 1)
                        .indexOf(" ") + 1
                val fromState = statesByName[fileName + it.substring(0, firstSpaceIndex)]!!
                val toState = statesByName[fileName + it.substring(firstSpaceIndex + 1,
                        secondSpaceIndex)]!!
                val transition = it.substring(secondSpaceIndex + 1)
                val transitionsTo = transitionFn.getOrDefault(fromState, mutableMapOf())
                val toStateSet = transitionsTo.getOrDefault(transition, mutableSetOf())
                toStateSet.add(toState)
                transitionsTo[transition] = toStateSet.toMutableSet()
                transitionFn[fromState] = transitionsTo.toMutableMap()
            }

            return NFA(
                    states,
                    startState,
                    finalStates,
                    transitionFn,
                    stateDataHelper,
                    alphabet,
                    name)
        }
    }

    /**
     * Serialize the NFA to the provided file path.
     * @param filePath The file path to write the serialization of NFA to.
     * @param stateDataHelper The helper class that knows how to serialize the state data.
     * @return The serialized string.
     */
    fun serialize(filePath: String): String {
        var serialization = "${states.size}\n"
        serialization +=
                states.map{ "${it.name} ${stateDataHelper.serialize(it.data)}" }.joinToString("\n")
        serialization += "\n${startState.name}\n"
        serialization += "${finalStates.map { it.name } .joinToString(" ")}\n"
        var transitionsString = ""
        transitionFn.keys.forEach { fromState ->
            transitionFn[fromState]?.keys?.forEach { transition ->
                transitionFn[fromState]?.get(transition)?.forEach { toState ->
                    transitionsString += "${fromState.name} ${toState.name} $transition\n"
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
        val newStartState = startState.combine(stateDataHelper, other.startState)
        val newStates = (states.union(other.states)).toMutableSet()
        newStates.add(newStartState)
        val newFinalStates = (finalStates.union(other.finalStates)).toMutableSet()
        val newTransitionFn: MutableMap<State, Map<String, Set<State>>> =
                HashMap(transitionFn)
        newTransitionFn.putAll(other.transitionFn)
        newTransitionFn[newStartState] =
                mutableMapOf("" to mutableSetOf(startState, other.startState))
        return NFA(
                newStates,
                newStartState,
                newFinalStates,
                newTransitionFn,
                stateDataHelper,
                alphabet)
    }

    /**
     * Uses subset construction to convert the NFA to a DFA and returns it.
     * @return The corresponding DFA.
     */
    fun toDFA(): NFA {
        var newStates: MutableSet<State> = mutableSetOf()
        var newStartState: State
        val newFinalStates: MutableSet<State> = mutableSetOf()
        val newTransitionFn: MutableMap<State, MutableMap<String, MutableSet<State>>> =
                mutableMapOf()
        val startStateEpsilonClosure = getEpsilonClosure(startState)
        newStartState = startStateEpsilonClosure.reduce { combinedStates, startState ->
            combinedStates.combine(stateDataHelper, startState)
        }
        newStates.add(newStartState)
        if (startStateEpsilonClosure.intersect(finalStates).isNotEmpty()) {
            newFinalStates.add(newStartState)
        }
        // List of sets, where each set is a state.
        val workList = mutableListOf(startStateEpsilonClosure)
        while (!workList.isEmpty()) {
            val currentStates = workList.last()
            val currentStatesCombined = currentStates.reduce { combinedStates, currentState ->
                combinedStates.combine(stateDataHelper, currentState)
            }
            workList.removeAt(workList.size - 1)
            for (a in alphabet) {
                var newState: MutableSet<State> = mutableSetOf()
                for (fromState in currentStates) {
                    for ((transition, toStates) in transitionFn[fromState].orEmpty()) {
                        if (transition == a) {
                            newState.addAll(toStates)
                        }
                    }
                }
                if (!newState.isEmpty()) {
                    newState = getEpsilonClosure(*newState.toTypedArray()).toMutableSet()
                    val newStateCombined = newState.reduce {combinedStates, currentState ->
                        combinedStates.combine(stateDataHelper, currentState)
                    }
                    // Create a state out of the current states (this is the from state), transition
                    // is a, and create a state out of the new state (this is the to state).
                    val transitionsTo =
                            if (newTransitionFn.contains(currentStatesCombined))
                                newTransitionFn[currentStatesCombined]
                            else mutableMapOf()
                    transitionsTo?.put(a, mutableSetOf(newStateCombined))
                    newTransitionFn[currentStatesCombined] = transitionsTo.orEmpty().toMutableMap()
                    if (!newStates.contains(newStateCombined)) {
                        newStates.add(newStateCombined)
                        workList.add(newState)
                        if (!newState.intersect(finalStates).isEmpty()) {
                            newFinalStates.add(newStateCombined)
                        }
                    }
                }
            }
        }
        return NFA(
                newStates,
                newStartState,
                newFinalStates,
                newTransitionFn,
                stateDataHelper,
                alphabet,
                "")
    }

    /**
     * Consume the character and move the DFA forward. If the NFA is not a DFA, don't do anything.
     * If reached an error state (i.e. cannot move from the current state on the given character),
     * return an empty string.
     * @param currentState The current state the DFA is in.
     * @param transition The character in the alphabet the DFA is supposed to read.
     * @return The new state taken from the old state on the passed transition.
     */
    fun getNextDFAState(currentState: State?, transition: String): State? {
        return when {
            currentState == null -> null
            !states.contains(currentState) -> throw NFAError("States doesn't contain state $currentState")
            else -> transitionFn[currentState]?.get(transition)?.first()
        }
    }

    /**
     * Returns whether or not the given state is a final state.
     * @param state The state to check.
     * @return True if the state is a final state, and false otherwise.
     */
    fun isFinalState(state: State?): Boolean {
        return state != null && finalStates.contains(state)
    }

    /**
     * Returns the epsilon closure of a set of states.
     * @param states The states of which epsilon closure to find.
     * @return The epsilon closure of the given states.
     */
    private fun getEpsilonClosure(vararg states: State): Set<State> {
        val workList = mutableListOf(*states)
        val resultSet = mutableSetOf(*states)
        while (!workList.isEmpty()) {
            val fromState = workList.last()
            workList.removeAt(workList.size - 1)
            for (toState in transitionFn[fromState]?.get("").orEmpty()) {
                if (!resultSet.contains(toState)) {
                    resultSet.add(toState)
                    workList.add(toState)
                }
            }
        }
        return resultSet
    }

    interface StateData

    interface StateDataHelper {
        /**
         * Deserializes the line to parse the state data based on the implementation.
         * @param line The part of line that contains data that represents the state data.
         * @return The state data for the state.
         */
        fun deserialize(line: String): StateData

        /**
         * Serializes the state data passed in to a string.
         * @param stateData The state data that needs to be serialized.
         * @return The serialized string.
         */
        fun serialize(stateData: StateData): String

        /**
         * Combine the state data passed and return the combined state data.
         * @param stateDataList The state data list to be combined.
         * @return The combined state data.
         */
        fun combine(vararg stateDataList: StateData): StateData
    }

    data class State(val name: String, val data: StateData) {
        /**
         * Combine the passed states with this state and return the combined state.
         * @param stateDataHelper The state data helper used to combine state data.
         * @param otherStates The other states that need to be combined with this state.
         * @return The combined state.
         */
        fun combine(stateDataHelper: StateDataHelper, vararg otherStates: State): State {
            val allStates = arrayOf(*otherStates, this)
            val newStateName = allStates.map { it.name }.joinToString("_")
            val newStateData = stateDataHelper.combine(*(allStates.map { it.data }.toTypedArray()))
            return State(newStateName, newStateData)
        }
    }

    class EmptyStateDataHelper : StateDataHelper {
        override fun deserialize(line: String): StateData {
            return EmptyStateData()
        }

        override fun serialize(stateData: StateData): String {
            return ""
        }

        override fun combine(vararg stateDataList: StateData): StateData {
            return EmptyStateData()
        }
    }

    data class EmptyStateData(val data: String = "") : StateData

    // TODO(ayushbhagat): Move to CFG.kt. Also convert each rule string to Rule.
    class RulesHelper() : StateDataHelper {
        override fun deserialize(line: String): StateData {
            return RulesStateData(line.split("  ").toSet())
        }

        override fun serialize(stateData: StateData): String {
            return if (stateData is RulesStateData)
                stateData.rules.joinToString("  ")
            else ""
        }

        override fun combine(vararg stateDataList: StateData): StateData {
            return stateDataList.fold(RulesStateData(setOf()), { combinedStateData, stateData ->
                if (stateData is RulesStateData) {
                    RulesStateData(combinedStateData.rules.union(stateData.rules))
                } else {
                    combinedStateData
                }
            })
        }
    }

    // TODO(ayushbhagat): Move to CFG.kt.
    data class RulesStateData(val rules: Set<String>) : StateData
}

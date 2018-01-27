package models

import java.io.File

data class NFA(
        val mStates: MutableSet<String>,
        val mStartState: String,
        val mFinalStates: MutableSet<String>,
        val mTransitionFn: MutableMap<String, MutableMap<String, MutableSet<String>>>) {
    companion object {
        fun new(filePath: String): NFA {
            // Read the DFA file.
            var lineList: MutableList<String> = mutableListOf()
            val inputStream = File(filePath).inputStream()
            inputStream.bufferedReader().useLines { lines -> lines.forEach { lineList.add(it) }}

            // Add states on the fly.
            val states: MutableSet<String> = mutableSetOf()

            // First line is the start state.
            var startState = lineList.get(0)

            // Second line contains a list of states.
            val finalStates: MutableSet<String> = mutableSetOf()
            lineList.get(1).split(" ").forEach { finalStates.add(it) }

            // Add the transitions.
            val transitionFn: MutableMap<String, MutableMap<String, MutableSet<String>>> =
                    mutableMapOf()
            lineList.drop(2).forEach {
                // Instead of using split, separate it by indexOf space so that the regex in the
                // transition can contain space.
                val firstSpaceIndex = it.indexOf(" ")
                val fromState = it.substring(0, firstSpaceIndex)
                val secondSpaceIndex = firstSpaceIndex + it.substring(firstSpaceIndex + 1)
                        .indexOf(" ") + 1
                val toState = it.substring(firstSpaceIndex + 1, secondSpaceIndex)
                val transition = it.substring(secondSpaceIndex + 1)
                val transitionsTo =
                        if (transitionFn.contains(fromState)) transitionFn[fromState]
                        else mutableMapOf()
                val toStateSet =
                        if (transitionsTo?.contains(transition) == true) transitionsTo[transition]
                        else mutableSetOf()
                toStateSet?.add(toState)
                if (toStateSet?.isEmpty() == false) {
                    transitionsTo?.put(transition, toStateSet)
                }
                if (transitionsTo?.contains(transition) == true) {
                    transitionFn[fromState] = transitionsTo
                }
                states.add(fromState)
                states.add(toState)
            }

            return NFA(states, startState, finalStates, transitionFn)
        }
    }
}
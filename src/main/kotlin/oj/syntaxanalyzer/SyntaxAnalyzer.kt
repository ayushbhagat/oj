package oj.syntaxanalyzer

import oj.models.CSTNode

class SyntaxAnalyzer {
    companion object {
        fun analyze(packages: Map<String, List<CSTNode>>) {
            val deadCodeDetector = DeadCodeDetector(packages)
            packages.forEach({(_, compilationUnits) ->
                compilationUnits.forEach({ compilationUnit ->
                    deadCodeDetector.visit(compilationUnit)
                })
            })
        }
    }
}
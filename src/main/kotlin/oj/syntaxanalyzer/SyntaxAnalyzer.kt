package oj.syntaxanalyzer

import oj.models.PackageManager

class SyntaxAnalyzer {
    companion object {
        fun analyze(packageManager: PackageManager) {
            val deadCodeDetector = DeadCodeDetector(packageManager)
            packageManager.packages.forEach({(_, compilationUnits) ->
                compilationUnits.forEach({ compilationUnit ->
                    deadCodeDetector.visit(compilationUnit)
                })
            })
        }
    }
}
package oj.models

data class Rule(val lhs: String, val rhs: List<String>) {
    override fun toString() : String {
        if (rhs.isEmpty()) {
            return lhs
        }

        return "$lhs ${rhs.joinToString(" ")}"
    }

    companion object {
        fun deserialize(line: String): Rule {
            val sides = line.split(" ")
            val lhs = sides.first()
            val rhs = sides.drop(1)
            return Rule(lhs, rhs)
        }
    }
}

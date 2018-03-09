package oj.models

val primitiveTypes = setOf("byte", "short", "int", "char", "boolean", "NULL")

class TypeError(reason: String): Exception(reason)

data class Type(val type: CSTNode, val isArray: Boolean) {
    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        if (other is Type) {
            val otherType = other.type
            val areTypesEqual =
                if (otherType.name in primitiveTypes && type.name in primitiveTypes) {
                    otherType.name == type.name
                } else if (otherType.name !in primitiveTypes && type.name !in primitiveTypes) {
                    otherType === type
                } else {
                    false
                }

            return areTypesEqual && isArray == other.isArray

        }

        return false
    }

    fun isReference(): Boolean {
        return type.name in listOf("ClassDeclaration", "InterfaceDeclaration") || isArray
    }

    fun isNull(): Boolean {
        return type.name == "NULL" && !isArray
    }

    fun isNumeric(): Boolean {
        return type.name in listOf("int", "short", "char", "byte") && !isArray
    }

    fun isBoolean(): Boolean {
        return type.name == "boolean" && !isArray
    }
}
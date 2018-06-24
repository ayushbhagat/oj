package oj.models

val primitiveTypes = setOf("byte", "short", "int", "char", "boolean")

class TypeError(reason: String): Exception(reason)

data class Type(val type: CSTNode, val isArray: Boolean) {
    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        if (other is Type) {
            val otherType = other.type
            val primiteTypesAndNull = primitiveTypes + "NULL"
            val areTypesEqual =
                if (otherType.name in primiteTypesAndNull && type.name in primiteTypesAndNull) {
                    otherType.name == type.name
                } else if (otherType.name !in primiteTypesAndNull && type.name !in primiteTypesAndNull) {
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

    fun isPrimitive(): Boolean {
        return type.name in primitiveTypes && !isArray
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

    override fun toString(): String {
        val typeName =
            if (type.name in listOf("ClassDeclaration", "InterfaceDeclaration")) {
                getDeclarationName(type)
            } else {
                type.name
            }

        return typeName + if (isArray) "[]" else ""
    }
}
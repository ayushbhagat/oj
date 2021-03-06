package oj.weeder

import oj.models.CSTNode

class Weeder {
    companion object {
        fun weed(root: CSTNode) : CSTNode {
            ClassWeeder().visit(root)
            GeneralModifiersWeeder().visit(root)
            MethodIsAbstractOrNativeIFFItHasNoMethodBodyWeeder().visit(root)
            InterfaceWeeder().visit(root)
            MethodModifiersWeeder().visit(root)
            FieldWeeder().visit(root)
            IntegerRangeWeeder().visit(root)
            CastExpressionWeeder().visit(root)
            PackageImportWeeder().visit(root)
            return root
        }
    }
}

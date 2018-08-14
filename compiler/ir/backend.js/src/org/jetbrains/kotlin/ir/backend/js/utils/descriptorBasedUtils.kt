/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.utils

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.isAny
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.referenceClassifier
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.tasks.isDynamic
import org.jetbrains.kotlin.resolve.descriptorUtil.isEffectivelyExternal
import org.jetbrains.kotlin.types.KotlinType

val IrConstructorSymbol.constructedClass get() = descriptor.constructedClass

fun createValueParameter(containingDeclaration: CallableDescriptor, index: Int, name: String, type: KotlinType): ValueParameterDescriptor {
    return ValueParameterDescriptorImpl(
        containingDeclaration = containingDeclaration,
        original = null,
        index = index,
        annotations = Annotations.EMPTY,
        name = Name.identifier(name),
        outType = type,
        declaresDefaultValue = false,
        isCrossinline = false,
        isNoinline = false,
        varargElementType = null,
        source = SourceElement.NO_SOURCE
    )
}
val IrConstructorSymbol.constructedClassType get() = (owner.parent as IrClass).thisReceiver?.type!!

fun ModuleDescriptor.getFunctions(fqName: FqName): List<FunctionDescriptor> {
    return getFunctions(fqName.parent(), fqName.shortName())
}

fun ModuleDescriptor.getFunctions(packageFqName: FqName, name: Name): List<FunctionDescriptor> {
    return getPackage(packageFqName).memberScope.getContributedFunctions(name, NoLookupLocation.FROM_BACKEND).toList()
}

fun ModuleDescriptor.getClassifier(fqName: FqName): ClassifierDescriptor? {
    return getClassifier(fqName.parent(), fqName.shortName())
}

fun ModuleDescriptor.getClassifier(packageFqName: FqName, name: Name): ClassifierDescriptor? {
    return getPackage(packageFqName).memberScope.getContributedClassifier(name, NoLookupLocation.FROM_BACKEND)
}

@Deprecated("Do not use descriptor-based utils")
val CallableMemberDescriptor.propertyIfAccessor
    get() = if (this is PropertyAccessorDescriptor)
        this.correspondingProperty
    else this

// Return is method has no real implementation except fake overrides from Any
fun IrFunction.isFakeOverriddenFromAny(): Boolean {
    if (origin != IrDeclarationOrigin.FAKE_OVERRIDE) {
        return (parent as? IrClass)?.thisReceiver?.type?.isAny() ?: false
    }

    return (this as IrSimpleFunction).overriddenSymbols.all { it.owner.isFakeOverriddenFromAny() }
}

fun IrClassifierSymbol.reference(symbolTable: SymbolTable) = if (!isBound) symbolTable.referenceClassifier(descriptor) else this

//fun IrDeclaration.isEffectivelyExternal() = descriptor.isEffectivelyExternal()

fun IrSymbol.isEffectivelyExternal() = descriptor.isEffectivelyExternal()

fun IrSymbol.isDynamic() = descriptor.isDynamic()


fun IrDeclaration.isEffectivelyExternal(): Boolean {
    return when (this) {
        is IrConstructor -> isExternal || parent is IrDeclaration && parent.isEffectivelyExternal()
        is IrFunction -> isExternal || parent is IrDeclaration && parent.isEffectivelyExternal()
        is IrField -> isExternal || parent is IrDeclaration && parent.isEffectivelyExternal()
        is IrClass -> isExternal || parent is IrDeclaration && parent.isEffectivelyExternal()
        else -> false
    }
}

private val IrDeclaration.isDynamic get() = this is IrFunction && dispatchReceiverParameter?.type is IrDynamicType

fun IrCall.isSuperToAny() =
    superQualifier?.let { this.symbol.owner.isFakeOverriddenFromAny() } ?: false


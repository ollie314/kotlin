/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.utils

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.js.backend.ast.JsName
import org.jetbrains.kotlin.js.naming.isES5IdentifierPart
import org.jetbrains.kotlin.js.naming.isES5IdentifierStart
import org.jetbrains.kotlin.resolve.calls.tasks.isDynamic
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isEffectivelyExternal
import org.jetbrains.kotlin.resolve.scopes.receivers.ExtensionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitClassReceiver

class SimpleNameGenerator : NameGenerator {

    private val nameCache = mutableMapOf<DeclarationDescriptor, JsName>()
    private val nameCache2 = mutableMapOf<IrDeclaration, JsName>()
    private val loopCache = mutableMapOf<IrLoop, JsName>()

//    override fun getNameForSymbol(symbol: IrSymbol, context: JsGenerationContext): JsName = getNameForDescriptor(symbol.descriptor, context)
    override fun getNameForSymbol(symbol: IrSymbol, context: JsGenerationContext): JsName =
        if (symbol.isBound) getNameForDeclaration(symbol.owner as IrDeclaration, context) else
            declareDynamic(symbol.descriptor, context)
    override fun getNameForLoop(loop: IrLoop, context: JsGenerationContext): JsName? = loop.label?.let {
        loopCache.getOrPut(loop) { context.currentScope.declareFreshName(sanitizeName(loop.label!!)) }
    }

    override fun getNameForType(type: IrType, context: JsGenerationContext): JsName {
        val classifier = type.classifierOrFail.run { reference(context.staticContext.backendContext.symbolTable) }
        return getNameForDeclaration(classifier.owner as IrDeclaration, context)
    }
//
//    private fun IrDeclaration.dynamicNameForDeclaration(irDeclaration: IrDeclaration) {
//
//        this is IrFunction && dispatchReceiverParameter?.type is IrDynamicType
//    }
    private fun externalNameForDeclaration(irDeclaration: IrDeclaration, context: JsGenerationContext): String? {
        val (isExternal, name) = when (irDeclaration) {
            is IrConstructor -> Pair(irDeclaration.isExternal, getNameForDeclaration(irDeclaration.parent as IrClass, context).ident)
            is IrFunction -> Pair(irDeclaration.isExternal, irDeclaration.name.asString())
            is IrField -> Pair(irDeclaration.isExternal, irDeclaration.name.asString())
            is IrClass -> Pair(irDeclaration.isExternal, irDeclaration.name.asString())
            else -> Pair(false, null)
        }
        return if (isExternal) name else null
    }

    private val IrDeclaration.isDynamic get() = this is IrFunction && dispatchReceiverParameter?.type is IrDynamicType

    private fun declareDynamic(descriptor: DeclarationDescriptor, context: JsGenerationContext): JsName {
        if (descriptor.isDynamic()) {
            return context.currentScope.declareName(descriptor.name.asString())
        }

        if (descriptor is MemberDescriptor && descriptor.isEffectivelyExternal()) {
            val descriptorForName = when (descriptor) {
                is ConstructorDescriptor -> descriptor.constructedClass
                is PropertyAccessorDescriptor -> descriptor.correspondingProperty
                else -> descriptor
            }
            return context.currentScope.declareName(descriptorForName.name.asString())
        }

        throw IllegalStateException("Unbound non-dynamic symbol")
    }

    private fun getNameForDeclaration(declaration: IrDeclaration, context: JsGenerationContext): JsName =
        nameCache2.getOrPut(declaration) {
            var nameDeclarator: (String) -> JsName = context.currentScope::declareName
            val nameBuilder = StringBuilder()

            val descriptor = declaration.descriptor

            if (declaration.isDynamic) {
                return@getOrPut nameDeclarator(declaration.descriptor.name.asString())
            }

            if (declaration.isEffectivelyExternal()) {
                val descriptorForName = when (descriptor) {
                    is ConstructorDescriptor -> descriptor.constructedClass
                    is PropertyAccessorDescriptor -> descriptor.correspondingProperty
                    else -> descriptor
                }
                return@getOrPut nameDeclarator(descriptorForName.name.asString())
            }
//
//
//            externalNameForDeclaration(declaration, context)?.let {
//                return@getOrPut nameDeclarator(it)
//            }


            when (declaration) {
                is IrValueParameter -> {
                    if ((context.currentFunction is IrConstructor && declaration.origin == IrDeclarationOrigin.INSTANCE_RECEIVER && declaration.name.isSpecial) ||
                        declaration == context.currentFunction?.dispatchReceiverParameter)
                        nameBuilder.append(Namer.IMPLICIT_RECEIVER_NAME)
                    else if (declaration == context.currentFunction?.extensionReceiverParameter) {
                        nameBuilder.append(Namer.EXTENSION_RECEIVER_NAME)
                    } else {
                        val declaredName = declaration.name.asString()
                        nameBuilder.append(declaredName)
                        if (declaredName.startsWith("\$")) {
                            nameBuilder.append('.')
                            nameBuilder.append(declaration.index)
                        }
                        if (nameBuilder.toString() == "import") nameBuilder.append("_")
                    }
                }
                is IrField -> {
                    nameBuilder.append(declaration.name.asString())
                    if (declaration.parent is IrDeclaration) {
                        nameBuilder.append('.')
                        nameBuilder.append(getNameForDeclaration(declaration.parent as IrDeclaration, context))
                    }
                }
                is IrClass -> {
                    if (declaration.isCompanion) {
                        nameBuilder.append(getNameForDeclaration(declaration.parent as IrDeclaration, context))
                        nameBuilder.append('.')
                    }

                    nameBuilder.append(declaration.name.asString())

                    (declaration.parent as? IrClass)?.let {
                        nameBuilder.append("$")
                        nameBuilder.append(getNameForDeclaration(it, context))
                    }


                    if (declaration.kind == ClassKind.OBJECT || declaration.name.isSpecial || declaration.visibility == Visibilities.LOCAL) {
                        nameDeclarator = context.staticContext.rootScope::declareFreshName
                    }
                }
                is IrConstructor -> {
                    nameBuilder.append(getNameForDeclaration(declaration.parent as IrClass, context))
                }
                is IrVariable -> {
                    nameBuilder.append(declaration.name.identifier)
                    nameDeclarator = context.currentScope::declareFreshName
                }
                is IrSimpleFunction -> {
                    nameBuilder.append(declaration.name.asString())
                    declaration.typeParameters.forEach { nameBuilder.append("_${it.name.asString()}") }
                    declaration.valueParameters.forEach { nameBuilder.append("_${it.type.render()}") }
                }

            }
            nameDeclarator(sanitizeName(nameBuilder.toString()))
        }


    private fun getNameForDescriptor(descriptor: DeclarationDescriptor, context: JsGenerationContext): JsName =
        nameCache.getOrPut(descriptor) {
            var nameDeclarator: (String) -> JsName = context.currentScope::declareName

            if (descriptor.isDynamic()) {
                return@getOrPut nameDeclarator(descriptor.name.asString())
            }

            if (descriptor is MemberDescriptor && descriptor.isEffectivelyExternal()) {
                val descriptorForName = when (descriptor) {
                    is ConstructorDescriptor -> descriptor.constructedClass
                    is PropertyAccessorDescriptor -> descriptor.correspondingProperty
                    else -> descriptor
                }
                return@getOrPut nameDeclarator(descriptorForName.name.asString())
            }

            val nameBuilder = StringBuilder()
            when (descriptor) {
                is ReceiverParameterDescriptor -> {
                    when (descriptor.value) {
                        is ExtensionReceiver ->
                            nameBuilder.append(Namer.EXTENSION_RECEIVER_NAME)
                        is ImplicitClassReceiver ->
                            nameBuilder.append(Namer.IMPLICIT_RECEIVER_NAME)
                        else -> TODO("name for $descriptor")
                    }
                }
                is ValueParameterDescriptor -> {
                    val declaredName = descriptor.name.asString()
                    nameBuilder.append(declaredName)
                    nameDeclarator = context.currentScope::declareFreshName
                }
                is PropertyDescriptor -> {
                    nameBuilder.append(descriptor.name.asString())
                    if (descriptor.visibility == Visibilities.PRIVATE || descriptor.modality != Modality.FINAL) {
                        nameBuilder.append('$')
                        nameBuilder.append(getNameForDescriptor(descriptor.containingDeclaration, context))
                    }
                }
                is PropertyAccessorDescriptor -> {
                    when (descriptor) {
                        is PropertyGetterDescriptor -> nameBuilder.append(Namer.GETTER_PREFIX)
                        is PropertySetterDescriptor -> nameBuilder.append(Namer.SETTER_PREFIX)
                    }
                    nameBuilder.append(descriptor.correspondingProperty.name.asString())
                    if (descriptor.visibility == Visibilities.PRIVATE) {
                        nameBuilder.append('$')
                        nameBuilder.append(getNameForDescriptor(descriptor.containingDeclaration, context))
                    }
                }
                is ClassDescriptor -> {
                    if (descriptor.name.isSpecial) {
                        nameBuilder.append(descriptor.name.asString().let {
                            it.substring(1, it.length - 1) + "${descriptor.hashCode()}"
                        })
                    } else {
                        nameBuilder.append(descriptor.fqNameSafe.asString().replace('.', '$'))
                    }
                }
                is ConstructorDescriptor -> {
                    nameBuilder.append(getNameForDescriptor(descriptor.constructedClass, context))
                }
                is VariableDescriptor -> {
                    nameBuilder.append(descriptor.name.identifier)
                    nameDeclarator = context.currentScope::declareFreshName
                }
                is CallableDescriptor -> {
                    nameBuilder.append(descriptor.name.asString())
                    descriptor.typeParameters.forEach { nameBuilder.append("_${it.name.asString()}") }
                    descriptor.valueParameters.forEach { nameBuilder.append("_${it.type}") }
                }

            }
            nameDeclarator(sanitizeName(nameBuilder.toString()))
        }

    private fun sanitizeName(name: String): String {
        if (name.isEmpty()) return "_"

        val first = name.first().let { if (it.isES5IdentifierStart()) it else '_' }
        return first.toString() + name.drop(1).map { if (it.isES5IdentifierPart()) it else '_' }.joinToString("")
    }
}
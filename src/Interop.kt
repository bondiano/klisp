package com.bondiano.klisp

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.isAccessible

/**
 * Java/Kotlin Interop for klisp
 *
 * Provides bidirectional conversion between klisp Values and JVM objects,
 * and support for calling Java/Kotlin methods, constructors, and accessing properties.
 *
 * Strategy:
 * 1. Try Kotlin reflection first (supports properties, default params, data classes)
 * 2. Fallback to Java reflection (for plain Java classes)
 */

/**
 * Convert klisp Value to Java/Kotlin object
 */
fun valueToJava(value: Value): Any? = when (value) {
    is Value.JavaObject -> value.obj
    is Value.JavaClass -> value.clazz
    is Value.Str -> value.text
    is Value.Integer -> value.value
    is Value.Float -> value.value
    is Value.Bool -> value.value
    Value.Nil -> null
    is Value.Cons -> value.toList().map { valueToJava(it) }
    else -> value
}

/**
 * Convert Java/Kotlin object to klisp Value
 *
 * Note: CharSequence is converted to Str to support Kotlin extension functions
 * that return CharSequence (like reversed(), substring()). This means StringBuilder
 * results are automatically converted to strings. To work with StringBuilder mutably,
 * keep it in a variable and call methods on it - the final .toString() will convert it.
 */
fun javaToValue(obj: Any?): Value = when (obj) {
    null -> Value.Nil
    is CharSequence -> Value.Str(obj.toString())  // Includes String, StringBuilder results
    is Long -> Value.Integer(obj)
    is Int -> Value.Integer(obj.toLong())
    is Short -> Value.Integer(obj.toLong())
    is Byte -> Value.Integer(obj.toLong())
    is Double -> Value.Float(obj)
    is Float -> Value.Float(obj.toDouble())
    is Boolean -> Value.Bool(obj)
    is Class<*> -> Value.JavaClass(obj)
    else -> Value.JavaObject(obj)
}

/**
 * Resolve class by fully qualified name
 */
fun resolveClass(className: String): Either<KlispError.EvalError, Class<*>> = either {
    try {
        Class.forName(className)
    } catch (_: ClassNotFoundException) {
        raise(KlispError.EvalError("Class not found: $className"))
    }
}

/**
 * Create an instance using Kotlin reflection (supports default parameters)
 */
fun createInstanceWithKotlinReflect(clazz: Class<*>, args: List<Any?>): Either<KlispError.EvalError, Any> = either {
    val kClass = clazz.kotlin
    val primaryConstructor =
        kClass.primaryConstructor ?: raise(KlispError.EvalError("No primary constructor found for ${clazz.name}"))

    val paramMap = buildParameterMap(primaryConstructor, args).bind()

    try {
        primaryConstructor.isAccessible = true
        primaryConstructor.callBy(paramMap)
    } catch (e: java.lang.reflect.InvocationTargetException) {
        raise(KlispError.EvalError("Constructor threw exception: ${e.cause?.message ?: e.message}"))
    } catch (e: IllegalArgumentException) {
        raise(KlispError.EvalError("Invalid constructor arguments: ${e.message}"))
    } catch (e: IllegalAccessException) {
        raise(KlispError.EvalError("Cannot access constructor: ${e.message}"))
    }
}

/**
 * Create an instance using Java reflection (fallback)
 */
fun createInstance(clazz: Class<*>, args: List<Any?>): Either<KlispError.EvalError, Any> = either {
    val constructor = findCompatibleConstructor(clazz, args).bind()
    val convertedArgs = convertArgsForConstructor(constructor, args)

    try {
        constructor.newInstance(*convertedArgs)
    } catch (e: java.lang.reflect.InvocationTargetException) {
        raise(KlispError.EvalError("Constructor threw exception: ${e.cause?.message ?: e.message}"))
    } catch (e: IllegalArgumentException) {
        raise(KlispError.EvalError("Invalid constructor arguments: ${e.message}"))
    } catch (e: InstantiationException) {
        raise(KlispError.EvalError("Cannot instantiate ${clazz.name}: ${e.message}"))
    } catch (e: IllegalAccessException) {
        raise(KlispError.EvalError("Cannot access constructor: ${e.message}"))
    }
}

private fun findCompatibleConstructor(clazz: Class<*>, args: List<Any?>): Either<KlispError.EvalError, Constructor<*>> =
    either {
        findCompatible(
            candidates = clazz.constructors.toList(),
            args = args,
            getParamTypes = { it.parameterTypes },
            errorMessage = "No compatible constructor found for ${clazz.name} with ${args.size} arguments"
        ).bind()
    }

/**
 * Invoke a method using Kotlin reflection first, fallback to Java reflection
 * Supports Kotlin properties, default parameters, and member functions
 */
fun invokeMethodWithKotlinReflect(
    target: Any, methodName: String, args: List<Any?>
): Either<KlispError.EvalError, Any?> = either {
    val kClass = target::class
    val memberFunction = kClass.memberFunctions.find { it.name == methodName }

    if (memberFunction != null) {
        try {
            memberFunction.isAccessible = true
            val convertedArgs = convertArgsForKFunction(memberFunction, args).bind()
            memberFunction.call(target, *convertedArgs)
        } catch (_: IllegalArgumentException) {
            invokeMethod(target, methodName, args).bind()
        } catch (e: java.lang.reflect.InvocationTargetException) {
            raise(KlispError.EvalError("Method threw exception: ${e.cause?.message ?: e.message}"))
        } catch (_: IllegalAccessException) {
            invokeMethod(target, methodName, args).bind()
        }
    } else {
        invokeMethod(target, methodName, args).bind()
    }
}

/**
 * Invoke a method using Java reflection (fallback)
 */
fun invokeMethod(target: Any, methodName: String, args: List<Any?>): Either<KlispError.EvalError, Any?> = either {
    val method = findCompatibleMethod(target.javaClass, methodName, args).bind()
    val convertedArgs = convertArgsForMethod(method, args)

    try {
        method.invoke(target, *convertedArgs)
    } catch (e: java.lang.reflect.InvocationTargetException) {
        raise(KlispError.EvalError("Method threw exception: ${e.cause?.message ?: e.message}"))
    } catch (e: IllegalArgumentException) {
        raise(KlispError.EvalError("Invalid method arguments: ${e.message}"))
    } catch (e: IllegalAccessException) {
        raise(KlispError.EvalError("Cannot access method: ${e.message}"))
    }
}

private fun findCompatibleMethod(
    clazz: Class<*>, methodName: String, args: List<Any?>
): Either<KlispError.EvalError, Method> = either {
    val methods = clazz.methods.filter { it.name == methodName }

    ensure(methods.isNotEmpty()) {
        KlispError.EvalError("Method not found: $methodName on ${clazz.name}")
    }

    findCompatible(
        candidates = methods,
        args = args,
        getParamTypes = { it.parameterTypes },
        errorMessage = "No compatible method $methodName found on ${clazz.name} with ${args.size} arguments"
    ).bind()
}

/**
 * Get property/field value using Kotlin reflection first, fallback to Java reflection
 */
fun getPropertyWithKotlinReflect(target: Any, propertyName: String): Either<KlispError.EvalError, Any?> = either {
    val kClass = target::class
    val property = kClass.memberProperties.find { it.name == propertyName }

    if (property != null) {
        try {
            property.isAccessible = true
            property.call(target)
        } catch (e: IllegalAccessException) {
            raise(KlispError.EvalError("Cannot access property: ${e.message}"))
        } catch (e: java.lang.reflect.InvocationTargetException) {
            raise(KlispError.EvalError("Property getter threw exception: ${e.cause?.message ?: e.message}"))
        }
    } else {
        getField(target, propertyName).bind()
    }
}

/**
 * Get field value using Java reflection (fallback)
 */
fun getField(target: Any, fieldName: String): Either<KlispError.EvalError, Any?> = either {
    try {
        val field = target.javaClass.getField(fieldName)
        field.get(target)
    } catch (_: NoSuchFieldException) {
        raise(KlispError.EvalError("Field not found: $fieldName on ${target.javaClass.name}"))
    } catch (e: IllegalAccessException) {
        raise(KlispError.EvalError("Cannot access field: ${e.message}"))
    }
}

/**
 * Get a companion object and its instance
 */
private fun getCompanionInstance(clazz: Class<*>): Either<KlispError.EvalError, Pair<KClass<*>, Any>> = either {
    val kClass = clazz.kotlin
    val companionObject = kClass.companionObject

    ensure(companionObject != null) {
        KlispError.EvalError("Class ${clazz.name} has no companion object")
    }

    val companionInstance =
        kClass.companionObjectInstance ?: raise(KlispError.EvalError("Cannot access companion object instance"))

    Pair(companionObject, companionInstance)
}

/**
 * Invoke a companion object method
 */
fun invokeCompanionMethod(
    clazz: Class<*>, methodName: String, args: List<Any?>
): Either<KlispError.EvalError, Any?> = either {
    val (companionObject, companionInstance) = getCompanionInstance(clazz).bind()

    val function = companionObject.memberFunctions.find { it.name == methodName }
        ?: raise(KlispError.EvalError("Method $methodName not found in companion object"))

    try {
        function.isAccessible = true
        val convertedArgs = convertArgsForKFunction(function, args).bind()
        return@either function.call(companionInstance, *convertedArgs)
    } catch (e: java.lang.reflect.InvocationTargetException) {
        raise(KlispError.EvalError("Companion method threw exception: ${e.cause?.message ?: e.message}"))
    } catch (e: IllegalArgumentException) {
        raise(KlispError.EvalError("Invalid companion method arguments: ${e.message}"))
    } catch (e: IllegalAccessException) {
        raise(KlispError.EvalError("Cannot access companion method: ${e.message}"))
    }
}

fun getCompanionProperty(
    clazz: Class<*>, propertyName: String
): Either<KlispError.EvalError, Any?> = either {
    val (companionObject, companionInstance) = getCompanionInstance(clazz).bind()

    val property = companionObject.memberProperties.find { it.name == propertyName }
        ?: raise(KlispError.EvalError("Property $propertyName not found in companion object"))

    try {
        property.isAccessible = true
        property.call(companionInstance)
    } catch (e: IllegalAccessException) {
        raise(KlispError.EvalError("Cannot access companion property: ${e.message}"))
    }
}

/**
 * Invoke extension function
 * Extension functions are compiled as static methods with receiver as the first parameter
 */
fun invokeExtensionFunction(
    receiver: Any, functionName: String, args: List<Any?>, extensionPackage: String = "kotlin.collections"
): Either<KlispError.EvalError, Any?> = either {
    val packages = listOf(
        extensionPackage, "kotlin.collections", "kotlin.text", "kotlin.sequences", "kotlin.ranges", "kotlin"
    )

    for (pkg in packages) {
        val result = tryInvokeExtensionFromPackage(receiver, functionName, args, pkg)
        if (result.isRight()) {
            return@either result.bind()
        }
    }

    raise(KlispError.EvalError("Extension function not found: $functionName"))
}

/**
 * Try to invoke an extension function from a specific package
 * Extension functions are compiled to static methods in classes like CollectionsKt, StringsKt
 * First parameter is the receiver (extension receiver)
 */
private fun tryInvokeExtensionFromPackage(
    receiver: Any, functionName: String, args: List<Any?>, packageName: String
): Either<KlispError.EvalError, Any?> = either {
    // Common Kotlin stdlib file names that contain extension functions
    // Note: internal classes like StringsKt___StringsKt exist but may not be accessible
    val fileNames = listOf(
        "Strings",
        "Collections",
        "Sequences",
        "Ranges",
        "Text",
        "Arrays",
        "Iterables",
        "Lists",
        "Sets",
        "Maps",
        "_Collections",
        "_Strings",
        "_Arrays",
        "_Sequences"
    )

    for (fileName in fileNames) {
        val className = "$packageName.${fileName}Kt"

        try {
            val clazz = Class.forName(className)

            // Find static method where first parameter matches receiver types
            val methods = clazz.methods.filter { method ->
                java.lang.reflect.Modifier.isStatic(method.modifiers) && method.name == functionName && method.parameterCount == args.size + 1 && // +1 for receiver
                        method.parameterTypes[0].isInstance(receiver)
            }

            for (method in methods) {
                try {
                    // Make accessible and invoke
                    method.isAccessible = true

                    // Build arguments: receiver + args
                    val allArgs = mutableListOf<Any?>(receiver)
                    args.forEachIndexed { index, arg ->
                        val paramType = method.parameterTypes[index + 1]
                        allArgs.add(convertToJavaType(arg, paramType))
                    }

                    val result = method.invoke(null, *allArgs.toTypedArray())
                    return@either result
                } catch (_: IllegalAccessException) {
                    continue
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    raise(KlispError.EvalError("Extension threw exception: ${e.cause?.message}"))
                }
            }
        } catch (_: ClassNotFoundException) {
            continue
        }
    }

    raise(KlispError.EvalError("Extension not found in $packageName"))
}


/**
 * Check if parameter types match arguments (exact match)
 */
private fun isExactParameterMatch(paramTypes: Array<Class<*>>, args: List<Any?>): Boolean {
    if (paramTypes.size != args.size) return false

    val argTypes = args.map { it?.javaClass ?: Object::class.java }
    return paramTypes.zip(argTypes).all { (param, arg) ->
        param.isAssignableFrom(arg) || isCompatiblePrimitive(param, arg)
    }
}

/**
 * Check if parameter types are compatible with arguments (with nulls and conversions)
 */
private fun isCompatibleParameterMatch(paramTypes: Array<Class<*>>, args: List<Any?>): Boolean {
    if (paramTypes.size != args.size) return false

    return paramTypes.zip(args).all { (param, arg) ->
        arg == null || param.isAssignableFrom(arg.javaClass) || isCompatiblePrimitive(param, arg.javaClass)
    }
}

/**
 * Find compatible callable (constructor or method) from candidates
 */
private fun <T> findCompatible(
    candidates: List<T>, args: List<Any?>, getParamTypes: (T) -> Array<Class<*>>, errorMessage: String
): Either<KlispError.EvalError, T> = either {
    candidates.find { candidate ->
        isExactParameterMatch(getParamTypes(candidate), args)
    } ?: candidates.find { candidate ->
        isCompatibleParameterMatch(getParamTypes(candidate), args)
    } ?: raise(KlispError.EvalError(errorMessage))
}

/**
 * Convert arguments for Java constructor
 */
private fun convertArgsForConstructor(constructor: Constructor<*>, args: List<Any?>): Array<Any?> {
    return constructor.parameterTypes.zip(args).map { (paramType, arg) ->
        convertToJavaType(arg, paramType)
    }.toTypedArray()
}

/**
 * Convert arguments for Java method
 */
private fun convertArgsForMethod(method: Method, args: List<Any?>): Array<Any?> {
    return method.parameterTypes.zip(args).map { (paramType, arg) ->
        convertToJavaType(arg, paramType)
    }.toTypedArray()
}

/**
 * Convert argument to Java type
 */
private fun convertToJavaType(arg: Any?, paramType: Class<*>): Any? {
    if (arg == null) return null

    return when (paramType) {
        Int::class.javaPrimitiveType if arg is Long -> arg.toInt()
        Int::class.java if arg is Long -> arg.toInt()

        Double::class.javaPrimitiveType if arg is Long -> arg.toDouble()
        Double::class.java if arg is Long -> arg.toDouble()

        Long::class.javaPrimitiveType if arg is Int -> arg.toLong()
        Long::class.java if arg is Int -> arg.toLong()
        else -> arg
    }
}

/**
 * Build parameter map for Kotlin constructor
 */
private fun buildParameterMap(
    constructor: KFunction<*>, args: List<Any?>
): Either<KlispError.EvalError, Map<KParameter, Any?>> = either {
    val paramMap = mutableMapOf<KParameter, Any?>()
    val valueParams = constructor.parameters

    args.forEachIndexed { index, arg ->
        if (index < valueParams.size) {
            val param = valueParams[index]
            val convertedArg = convertToKotlinType(arg, param.type)
            paramMap[param] = convertedArg
        }
    }

    paramMap
}

/**
 * Convert arguments for Kotlin function
 */
private fun convertArgsForKFunction(
    function: KFunction<*>, args: List<Any?>
): Either<KlispError.EvalError, Array<Any?>> = either {
    val valueParams = function.parameters.filter { it.kind == KParameter.Kind.VALUE }

    ensure(args.size >= valueParams.count { !it.isOptional }) {
        KlispError.EvalError("Expected at least ${valueParams.count { !it.isOptional }} arguments")
    }

    ensure(args.size <= valueParams.size) {
        KlispError.EvalError("Expected at most ${valueParams.size} arguments")
    }

    args.mapIndexed { index, arg ->
        val param = valueParams[index]
        convertToKotlinType(arg, param.type)
    }.toTypedArray()
}

/**
 * Convert argument to Kotlin type
 */
private fun convertToKotlinType(arg: Any?, kType: KType): Any? {
    if (arg == null) return null

    val classifier = kType.classifier as? KClass<*> ?: return arg

    return when (classifier) {
        Int::class if arg is Long -> arg.toInt()
        Long::class if arg is Int -> arg.toLong()
        Double::class if arg is Float -> arg.toDouble()
        Float::class if arg is Double -> arg.toFloat()
        String::class if arg is CharSequence -> arg.toString()
        else -> arg
    }
}

/**
 * Check if Java primitive types are compatible
 */
private fun isCompatiblePrimitive(paramType: Class<*>, argType: Class<*>): Boolean = when (paramType to argType) {
    java.lang.Long::class.java to Long::class.javaObjectType -> true
    Long::class.javaPrimitiveType to Long::class.javaObjectType -> true

    Integer::class.java to Int::class.javaObjectType -> true
    Int::class.javaPrimitiveType to Int::class.javaObjectType -> true
    Integer::class.java to Long::class.javaObjectType -> true
    Int::class.javaPrimitiveType to Long::class.javaObjectType -> true

    java.lang.Double::class.java to Double::class.javaObjectType -> true
    Double::class.javaPrimitiveType to Double::class.javaObjectType -> true
    java.lang.Float::class.java to Float::class.javaObjectType -> true
    Float::class.javaPrimitiveType to Float::class.javaObjectType -> true

    java.lang.Boolean::class.java to Boolean::class.javaObjectType -> true
    Boolean::class.javaPrimitiveType to Boolean::class.javaObjectType -> true

    Int::class.javaPrimitiveType to Long::class.javaObjectType -> true
    Double::class.javaPrimitiveType to Long::class.javaObjectType -> true
    Double::class.javaPrimitiveType to Int::class.javaObjectType -> true
    Long::class.javaPrimitiveType to Int::class.javaObjectType -> true
    else -> false
}

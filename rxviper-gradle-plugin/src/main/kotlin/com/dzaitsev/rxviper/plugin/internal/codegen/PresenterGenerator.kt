package com.dzaitsev.rxviper.plugin.internal.codegen

import com.dzaitsev.rxviper.Presenter
import com.dzaitsev.rxviper.ViperPresenter
import com.dzaitsev.rxviper.plugin.aClass
import com.dzaitsev.rxviper.plugin.internal.dsl.Screen
import com.dzaitsev.rxviper.plugin.internal.dsl.UseCase
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import rx.functions.Action1
import javax.lang.model.element.Modifier

internal class PresenterGenerator(screen: Screen) : Generator(screen) {
  override val typeName = "Presenter"

  override fun createSpec(): List<TypeSpec.Builder> {
    val constructorBuilder = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PUBLIC)
    val onDropViewMethodBuilder = MethodSpec.methodBuilder("onDropView")
        .addModifiers(Modifier.PROTECTED)
        .addParameter(ClassName.get(screen.fullPackage, "${screen.name}ViewCallbacks"), "view")
        .addAnnotation(aClass<Override>())
    val presenterBuilder = TypeSpec.classBuilder(typeSpecName)
        .superclass(superClass())
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
    val useCases = if (screen.useCases.isEmpty()) listOf(UseCase(screen.name)) else screen.useCases.map { it }

    if (useCases.isEmpty()) {
      onDropViewMethodBuilder.addComment("TODO: Release your resources here...")
    }

    useCases.forEach { useCase ->
      val methodBuilder = MethodSpec.methodBuilder("do${useCase.name}")
          .addModifiers(Modifier.PUBLIC)
          .addParameter(useCase.requestClass, "requestModel")

      if (screen.hasInteractor) {
        val className = "${useCase.name}Interactor"
        val argName = className.decapitalize()

        constructorBuilder
            .addParameter(ClassName.get(screen.fullPackage, className), argName)
            .addStatement("this.\$1N = \$1N", argName)

        onDropViewMethodBuilder.addStatement("\$N.unsubscribe()", argName)

        presenterBuilder.addField(ClassName.get(screen.fullPackage, className), argName, Modifier.PRIVATE, Modifier.FINAL)
        presenterBuilder.addMethod(when {
          screen.useLambdas -> methodBuilder.addStatement("\$N.execute(\$N -> {\n" +
              "  // TODO: Implement onNext here...\n" +
              "}, t -> {\n" +
              "  // TODO: Implement onError here...\n" +
              "}, \$N)", argName, useCase.responseClass.simpleName.first().toLowerCase().toString(), "requestModel").build()
          else -> methodBuilder.addStatement("\$N.execute(\$L, \$L, \$N)",
              argName,
              action1Anonymous(useCase.responseClass, useCase.responseClass.simpleName.first().toLowerCase().toString(), "TODO: Implement onNext here..."),
              action1Anonymous(aClass<Throwable>(), "t", "TODO: Implement onError here..."),
              "requestModel")
              .build()
        })
      } else {
        presenterBuilder.addMethod(methodBuilder.addComment("TODO: Implement your business logic here...")
            .build())
      }
    }

    return listOf(presenterBuilder.addMethod(constructorBuilder.build())
        .addMethod(onDropViewMethodBuilder.build()))
  }

  private fun action1Anonymous(clazz: Class<*>, paramName: String, comment: String): TypeSpec {
    return TypeSpec.anonymousClassBuilder("")
        .addSuperinterface(ParameterizedTypeName.get(aClass<Action1<*>>(), clazz))
        .addMethod(MethodSpec.methodBuilder("call")
            .addAnnotation(aClass<Override>())
            .addModifiers(Modifier.PUBLIC)
            .addParameter(clazz, paramName)
            .addComment(comment)
            .build())
        .build()
  }

  private fun superClass(): TypeName {
    val viewCallbacks = ClassName.get(screen.fullPackage, "${screen.name}ViewCallbacks")

    return when {
      screen.hasRouter -> ParameterizedTypeName.get(
          ClassName.get(aClass<ViperPresenter<*, *>>()), viewCallbacks, ClassName.get(screen.fullPackage, "${screen.name}Router"))
      else -> ParameterizedTypeName.get(ClassName.get(aClass<Presenter<*>>()), viewCallbacks)
    }
  }
}
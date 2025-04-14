package com.connecthid.intellij.models

import com.intellij.util.xmlb.Converter


enum class AuthenticationMethod(val value: Int ) {
    NONE(0),
    PASSWORD(1),
    PRIVATE_KEY(2);
}


  class AuthenticationMethodConverter : Converter<AuthenticationMethod>() {
      override fun fromString(value: String): AuthenticationMethod {
          try {
              val authMethod = value.toInt()
              return AuthenticationMethod.entries.first { it.value == authMethod }
          } catch (e: NumberFormatException){
              return AuthenticationMethod.PASSWORD
          }
      }

      override fun toString(value: AuthenticationMethod): String {
         return value.value.toString()
      }

  }
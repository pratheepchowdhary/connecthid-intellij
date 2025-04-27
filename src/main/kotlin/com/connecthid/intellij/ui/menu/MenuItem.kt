package com.connecthid.intellij.ui.menu

import javax.swing.Icon

 data class MenuItem(@JvmField val text: String, @JvmField val icon: Icon?=null, @JvmField val shortcut: String="")
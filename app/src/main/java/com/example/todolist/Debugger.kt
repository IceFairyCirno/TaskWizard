package com.example.todolist

import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class Debugger {
    fun displayNestedList(nestedList: MutableList<MutableList<String>>) {
        Log.d("Debugger", "Displaying Nested List")
        for (innerList in nestedList) {
            Log.d("Debugger", innerList.joinToString(", "))
        }
    }
}
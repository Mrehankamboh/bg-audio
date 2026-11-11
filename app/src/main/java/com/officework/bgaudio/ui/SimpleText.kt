package com.officework.bgaudio.ui

import android.text.Editable
import android.text.TextWatcher

class SimpleText(private val onChange: (String) -> Unit) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable?) {
        onChange(s?.toString().orEmpty())
    }
}

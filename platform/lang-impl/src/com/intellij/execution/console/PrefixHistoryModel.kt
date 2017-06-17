/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.console

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.AtomicFieldUpdater
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.FactoryMap
import org.pcollections.PVector
import org.pcollections.TreePVector

/**
 * @author Yuli Fiterman
 */
/**
 * @noinspection MismatchedQueryAndUpdateOfCollectionMasterModel
 */

private object MasterModels : FactoryMap<String, MasterModel>() {
  override fun createMap(): Map<String, MasterModel> {
    return ContainerUtil.createConcurrentWeakValueMap<String, MasterModel>()
  }

  override fun create(key: String): MasterModel {
    return MasterModel()
  }
}

fun createModel(persistenceId: String, console: LanguageConsoleView): ConsoleHistoryModel {


  val masterModel: MasterModel = MasterModels[persistenceId]!!
  val searchPrefixTracker = SearchPrefixTracker(console).apply { install() }
  return PrefixHistoryModel(masterModel) {
    searchPrefixTracker.getPrefixFromEditor()
  }

}

private class SearchPrefixTracker(private val console: LanguageConsoleView) {
  private var lastFirstCaretPosition = 0

  fun install() {
    val listener = object : CaretListener {
      override fun caretPositionChanged(e: CaretEvent) {
        if (e.oldPosition.line == 0) {
          lastFirstCaretPosition = e.oldPosition.column
        }
      }
    }
    val consoleEditor = console.consoleEditor
    consoleEditor.caretModel.addCaretListener(listener)
    Disposer.register(console, Disposable {
      consoleEditor.caretModel.removeCaretListener(listener)
    })
  }

  fun getPrefixFromEditor(): String {
    val editor = console.currentEditor
    val carretOffset = editor.caretModel.offset
    val document = editor.document
    val lineNumber = document.getLineNumber(carretOffset)
    if (lineNumber == 0) {
      return document.getText(TextRange(0, carretOffset))
    }

    val offsetInLine: Int = document.getLineEndOffset(0) - document.getLineStartOffset(0)
    return document.getText(TextRange(0, Math.min(offsetInLine, lastFirstCaretPosition)))

  }

}

private class PrefixHistoryModel constructor(private val masterModel: MasterModel, private val getPrefixFn: () -> String) : ConsoleHistoryModel, EntriesWithPositionHolder() {

  var userContent: String = ""
  override fun setContent(userContent: String) {
    this.userContent = userContent
  }

  private fun State.currentEntryOrContent(): String? {
    val (entries, index) = this
    return if (index == entries.size) {
      userContent
    }
    else {
      this.currentEntry()
    }
  }


  init {
    resetIndex()
  }

  override fun resetEntries(entries: MutableList<String>) {
    masterModel.resetEntries(entries)
    resetIndex()
  }

  override fun addToHistory(statement: String?) {
    if (statement.isNullOrEmpty()) {
      return
    }
    masterModel.addToHistory(statement)
    resetIndex()
  }


  override fun removeFromHistory(statement: String?) {
    if (statement.isNullOrEmpty()) {
      return
    }
    masterModel.removeFromHistory(statement)
    resetIndex()
  }

  private fun resetIndex() {
    updateAtomically {
      val newEntries = masterModel.entriesSnap
      return@updateAtomically State(newEntries, newEntries.size)
    }
  }


  override fun getHistoryNext(): ConsoleHistoryModel.TextWithOffset? {
    val searchPrefix = getPrefixFn()
    return updateAtomically { state ->
      val (entries, index) = state
      if (index <= 0) {
        return null
      }

      val indexOfLast = entries.subList(0, index).indexOfLast { it.startsWith(searchPrefix) || searchPrefix.isEmpty() }
      if (indexOfLast == -1) {
        return null
      }
      return@updateAtomically State(entries, indexOfLast)
    }.currentEntry()?.withOffset(searchPrefix.length)
  }


  override fun getHistoryPrev(): ConsoleHistoryModel.TextWithOffset? {
    val searchPrefix = getPrefixFn()
    return updateAtomically {
      state ->
      val (entries, index) = state
      if (index > entries.size - 1 || entries.isEmpty()) {
        return null
      }
      if (searchPrefix.isEmpty()) {
        return@updateAtomically State(entries, index + 1)
      }
      val prevOffset = entries.subList(index + 1, entries.size).indexOfFirst { it.startsWith(searchPrefix) }
      if (prevOffset == -1) {
        if (userContent.startsWith(searchPrefix)) {
          return@updateAtomically State(entries, entries.size)
        }
        else {
          return null
        }
      }

      return@updateAtomically State(entries, index + 1 + prevOffset)
    }.currentEntryOrContent()?.withOffset(searchPrefix.length)

  }

  override fun hasHistory(): Boolean = myState.let { state -> state.index >= 0 && state.index < state.entries.size - 1 }

  override fun getModificationCount(): Long = masterModel.modificationCount

  override fun getMaxHistorySize(): Int = masterModel.maxHistorySize

  override fun getEntries(): List<String> = masterModel.entries

  override fun isEmpty(): Boolean = masterModel.isEmpty

  override fun getHistorySize(): Int = masterModel.historySize

  override fun getCurrentIndex(): Int = masterModel.currentIndex

  override fun prevOnLastLine(): Boolean = true


}

private class MasterModel : ConsoleHistoryModel, EntriesWithPositionHolder() {
  override fun setContent(userContent: String) = throw IllegalStateException("Should not be invoked")

  val entriesSnap: PVector<String>
    get() = myState.entries



  override fun resetEntries(ent: MutableList<String>) {
    updateAtomically {
      state ->
      val newEntries = TreePVector.from(ent)
      return@updateAtomically State(newEntries, newEntries.size)
    }
  }


  override fun addToHistory(statement: String?) {
    val stmt = statement ?: return

    updateAtomically {
      state ->
      val maxHistorySize = maxHistorySize
      val entries = myState.entries
      var newEntries = entries.minus(stmt)
      if (newEntries.size >= maxHistorySize) {
        newEntries = newEntries.minus(0)
      }
      newEntries = newEntries.plus(stmt)

      return@updateAtomically State(newEntries, newEntries.size)
    }
    incModificationCount()
  }


  override fun removeFromHistory(statement: String?) {
    val stmt = statement ?: return
    updateAtomically {
      state ->
      val entries = myState.entries
      val newEntries = entries.minus(stmt)
      return@updateAtomically if (newEntries != entries) {
        State(newEntries, newEntries.size)
      }
      else {
        state
      }
    }
    incModificationCount()
  }

  override fun getMaxHistorySize(): Int = UISettings.instance.consoleCommandHistoryLimit

  override fun getEntries(): List<String> = myState.entries.toList()

  override fun isEmpty(): Boolean = myState.entries.isEmpty()

  override fun getHistorySize(): Int = myState.entries.size

  override fun getHistoryNext(): ConsoleHistoryModel.TextWithOffset? {
    return updateAtomically { state ->
      val (entries, index) = state
      return@updateAtomically if (index >= 0) {
        State(entries, index - 1)
      }
      else {
        State(entries, index)
      }
    }.currentEntry()?.defaultOffset()
  }

  override fun getHistoryPrev(): ConsoleHistoryModel.TextWithOffset? {
    return updateAtomically { state ->
      val (entries, index) = state
      return@updateAtomically if (index <= entries.size - 1) {
        State(entries, index + 1)
      }
      else {
        State(entries, index)
      }
    }.currentEntry()?.defaultOffset()

  }

  override fun hasHistory(): Boolean {
    val (entries, index) = myState
    return index < entries.size - 1

  }


  override fun prevOnLastLine(): Boolean = true

  override fun getCurrentIndex(): Int = myState.index


}

private fun String.defaultOffset() = ConsoleHistoryModel.TextWithOffset(this, -1)
private fun String.withOffset(offet: Int) = ConsoleHistoryModel.TextWithOffset(this, offet)

private open class EntriesWithPositionHolder : SimpleModificationTracker() {
  protected data class State(val entries: PVector<String>, val index: Int)

  protected fun State.currentEntry(): String? {
    val (entries, index) = this
    return entries.getOrNull(index)
  }

  @Volatile
  protected var myState = State(TreePVector.empty(), -1)

  protected inline fun updateAtomically(fn: (State) -> State): State {
    var newState: State
    do {
      val currentState = myState
      newState = fn(currentState)
    }
    while (!updater.compareAndSet(this, currentState, newState))
    return newState
  }

  companion object {
    val updater = AtomicFieldUpdater.forFieldOfType(EntriesWithPositionHolder::class.java, EntriesWithPositionHolder.State::class.java)
  }
}
/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import data.Trie
import elmish.elementById
import elmish.mountComponentAt
import elmish.tree.Tree
import elmish.tree.TreeView
import kotlin.js.JSON.stringify


fun main() {
    mountComponentAt(
        elementById("report"),
        ConfigurationCacheReportPage,
        reportPageModelFromJsModel(configurationCacheProblems())
    )
}


/**
 * External model defined in `configuration-cache-report-data.js`, a file generated by `InstantExecutionReport`.
 */
private
external val configurationCacheProblems: () -> JsModel


private
external interface JsModel {
    val cacheAction: String
    val documentationLink: String
    val problems: Array<JsProblem>
}


private
external interface JsProblem {
    val trace: Array<JsTrace>
    val message: Array<JsMessageFragment>
    val error: String?
    val documentationLink: String?
}


private
external interface JsTrace {
    val kind: String
}


private
external interface JsTraceTask : JsTrace {
    val path: String
    val type: String
}


private
external interface JsTraceBean : JsTrace {
    val type: String
}


private
external interface JsTraceField : JsTrace {
    val name: String
    val declaringType: String
}


private
external interface JsTraceProperty : JsTrace {
    val name: String
    val task: String
}


private
external interface JsMessageFragment {
    val text: String?
    val name: String?
}


private
data class ImportedProblem(
    val problem: JsProblem,
    val message: PrettyText,
    val trace: List<ProblemNode>
)


private
fun reportPageModelFromJsModel(jsModel: JsModel): ConfigurationCacheReportPage.Model {
    val problems = jsModel.problems.map { jsProblem ->
        ImportedProblem(
            jsProblem,
            jsProblem.message.let(::toPrettyText),
            jsProblem.trace.map(::toProblemNode)
        )
    }
    return ConfigurationCacheReportPage.Model(
        cacheAction = jsModel.cacheAction,
        documentationLink = jsModel.documentationLink,
        totalProblems = jsModel.problems.size,
        messageTree = treeModelFor(
            ProblemNode.Label("Problems grouped by message"),
            problemNodesByMessage(problems)
        ),
        taskTree = treeModelFor(
            ProblemNode.Label("Problems grouped by task"),
            problemNodesByTask(problems)
        )
    )
}


private
fun problemNodesByMessage(problems: List<ImportedProblem>): Sequence<MutableList<ProblemNode>> =
    problems.asSequence().map { imported ->
        mutableListOf<ProblemNode>().apply {
            add(
                errorOrWarningNodeFor(
                    imported.problem,
                    messageNodeFor(imported),
                    docLinkFor(imported.problem)
                )
            )
            imported.trace.forEach { part ->
                add(part)
            }
            exceptionNodeFor(imported.problem)?.let {
                add(it)
            }
        }
    }


private
fun problemNodesByTask(problems: List<ImportedProblem>): Sequence<List<ProblemNode>> =
    problems.asSequence().map { imported ->
        imported.trace.asReversed().mapIndexed { index, node ->
            when (index) {
                0 -> errorOrWarningNodeFor(imported.problem, node, null)
                else -> node
            }
        } + exceptionOrMessageNodeFor(imported)
    }


private
fun toPrettyText(message: Array<JsMessageFragment>) = PrettyText(
    message.map {
        it.text?.let(PrettyText.Fragment::Text)
            ?: it.name?.let(PrettyText.Fragment::Reference)
            ?: PrettyText.Fragment.Text("Unrecognised message fragment: ${stringify(it)}")
    }
)


private
fun toProblemNode(trace: JsTrace): ProblemNode = when (trace.kind) {
    "Task" -> trace.unsafeCast<JsTraceTask>().run {
        ProblemNode.Task(path, type)
    }
    "Bean" -> trace.unsafeCast<JsTraceBean>().run {
        ProblemNode.Bean(type)
    }
    "Field" -> trace.unsafeCast<JsTraceField>().run {
        ProblemNode.Property("field", name, declaringType)
    }
    "InputProperty" -> trace.unsafeCast<JsTraceProperty>().run {
        ProblemNode.Property("input property", name, task)
    }
    "OutputProperty" -> trace.unsafeCast<JsTraceProperty>().run {
        ProblemNode.Property("output property", name, task)
    }
    else -> ProblemNode.Label("Gradle runtime")
}


private
fun errorOrWarningNodeFor(problem: JsProblem, label: ProblemNode, docLink: ProblemNode?): ProblemNode =
    problem.error?.let {
        ProblemNode.Error(label, docLink)
    } ?: ProblemNode.Warning(label, docLink)


private
fun exceptionOrMessageNodeFor(importedProblem: ImportedProblem) =
    exceptionNodeFor(importedProblem.problem)
        ?: messageNodeFor(importedProblem)


private
fun messageNodeFor(importedProblem: ImportedProblem) =
    ProblemNode.Message(importedProblem.message)


private
fun exceptionNodeFor(it: JsProblem): ProblemNode? =
    it.error?.let(ProblemNode::Exception)


private
fun docLinkFor(it: JsProblem): ProblemNode? =
    it.documentationLink?.let { ProblemNode.Link(it, " ?") }


private
fun <T> treeModelFor(
    label: T,
    sequence: Sequence<List<T>>
): TreeView.Model<T> = TreeView.Model(
    treeFromTrie(
        label,
        Trie.from(sequence),
        Tree.ViewState.Collapsed
    )
)


private
fun <T> treeFromTrie(label: T, trie: Trie<T>, state: Tree.ViewState): Tree<T> {
    val subTreeState = if (trie.size == 1) Tree.ViewState.Expanded else Tree.ViewState.Collapsed
    return Tree(
        label,
        subTreesFromTrie(trie, subTreeState),
        // nodes with no children such as Exception nodes are considered `Collapsed` by default
        if (trie.size == 0) Tree.ViewState.Collapsed else state
    )
}


private
fun <T> subTreesFromTrie(trie: Trie<T>, state: Tree.ViewState): List<Tree<T>> =
    trie.entries.sortedBy { (label, _) -> label.toString() }.map { (label, subTrie) ->
        treeFromTrie(
            label,
            subTrie,
            state
        )
    }.toList()

/*
 * scala-exercises-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.client
package utils

import org.scalajs.dom.ext.KeyCode
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.raw.{ HTMLDivElement, HTMLElement, HTMLInputElement }
import org.scalajs.jquery.{ jQuery ⇒ $, JQuery }
import scala.scalajs.js.timers._

import monix.cats._
import monix.eval.Coeval

import cats.data.OptionT
import cats.implicits._

object DomHandler {

  /** Replaces text matched into html inputs
    */
  def replaceInputs(nodes: Seq[(HTMLElement, String)]): Coeval[Unit] = Coeval {
    nodes foreach { case (n, r) ⇒ $(n).html(r) }
  }

  /** Highlights every preformatted code block.
    */
  def highlightCodeBlocks: Coeval[Unit] = Coeval {
    $("pre").each((_: Any, code: dom.Element) ⇒ {
      js.Dynamic.global.hljs.highlightBlock(code)
    })
  }

  /** Converts emoji markup into inline emoji images.
    */
  def emojify: Coeval[Unit] = Coeval {
    $(".modal-body").each((_: Any, el: dom.Element) ⇒ {
      js.Dynamic.global.emojify.run(el)
    })
  }

  /** Set the class attribute to an exercise node
    */
  def setExerciseClass(e: HTMLElement, style: String): Coeval[Unit] = Coeval {
    $(e).attr("class", s"exercise $style")
  }

  /** Set the class attribute to an exercise code node
    */
  def setCodeClass(e: HTMLElement, style: String): Coeval[Unit] = Coeval {
    $(e).attr("class", s"exercise-pre $style")
  }

  /** Write a message in the log of an exercise
    */
  def writeLog(e: HTMLElement, msg: String): Coeval[Unit] = Coeval {
    $(e).find(".log").text(msg)
  }

  /** Assigns behaviors to the keyup event for inputs elements.
    */
  def onInputKeyUp(
    onkeyup:        (String, Seq[String]) ⇒ Coeval[Unit],
    onEnterPressed: String ⇒ Coeval[Unit]
  ): Coeval[Unit] = for {
    inputs ← allInputs
    _ ← inputs.map(input ⇒ attachKeyUpHandler(input, onkeyup, onEnterPressed)).sequence
  } yield ()

  /** Assigns behavior to an input element change event
    */
  def onInputChange(
    onchange: (String, Seq[String]) ⇒ Coeval[Unit]
  ): Coeval[Unit] = {
    for {
      inputs ← allInputs
      _ ← inputs.map(input ⇒ {
        attachOnInputChange(input, onchange)
        attachOnInputPaste(input, onchange)
      }).sequence
    } yield ()
  }

  /** Shows modal for signing up
    */
  def showSignUpModal: Coeval[Unit] = Coeval($("#mustSignUp").modal("show"))

  def updateExerciseInput(
    input:   HTMLInputElement,
    actions: ((String, Seq[String])) ⇒ Unit
  ) = {
    setInputWidth(input).value

    val maybeInputInfo = for {
      methodName ← methodParent(input)
      exercise ← findExerciseByMethod(methodName)
      inputValues = getInputsValues(exercise)
    } yield (methodName, inputValues)

    maybeInputInfo.foreach[Unit](actions)
  }

  def attachKeyUpHandler(
    input:          HTMLInputElement,
    onkeyup:        (String, Seq[String]) ⇒ Coeval[Unit],
    onEnterPressed: String ⇒ Coeval[Unit]
  ): Coeval[Unit] = Coeval {
    $(input).keyup((e: dom.KeyboardEvent) ⇒ {
      updateExerciseInput(
        input,
        (info: (String, Seq[String])) ⇒ {
          val (method, params) = info
          e.keyCode match {
            case KeyCode.Enter ⇒ onEnterPressed(method).value
            case _             ⇒ onkeyup(method, params).value
          }
          ()
        }
      )
    })
  }

  /** Provides support for input changes related to events like drag-and-drop and others (not pasting)
    */
  def attachOnInputChange(
    input:    HTMLInputElement,
    onchange: (String, Seq[String]) ⇒ Coeval[Unit]
  ): Coeval[Unit] = Coeval {
    $(input).change((e: dom.Event) ⇒ {
      updateExerciseInput(
        input,
        (info: (String, Seq[String])) ⇒ ()
      )
    })
  }

  /** Provides support for input changes related to a pasting event
    */
  def attachOnInputPaste(
    input:    HTMLInputElement,
    onchange: (String, Seq[String]) ⇒ Coeval[Unit]
  ): Coeval[Unit] = Coeval {
    $(input).bind("paste", (e: dom.Event) ⇒ {

      setTimeout(100.0) {
        updateExerciseInput(
          input,
          (info: (String, Seq[String])) ⇒ ()
        )
      }

    })
  }

  def onButtonClick(onClick: String ⇒ Coeval[Unit]): Coeval[Unit] =
    allExercises.map(attachClickHandler(_, onClick)).sequence.map(_ ⇒ ())

  def attachClickHandler(exercise: HTMLElement, onClick: String ⇒ Coeval[Unit]): Coeval[Unit] = Coeval {
    $(exercise).find(".compile button").click((e: dom.Event) ⇒ {
      onClick(getMethodAttr(exercise)).value
    })
  }

  def setInputWidth(input: HTMLInputElement): Coeval[JQuery] =
    Coeval($(input).width(inputSize(getInputLength(input))))

  def inputReplacements: Coeval[Seq[(HTMLElement, String)]] = for {
    blocks ← getCodeBlocks
  } yield blocks.map(code ⇒ code → replaceInputByRes(getTextInCode(code)))

  val resAssert = """(?s)(res\d+)""".r

  def allExercises: List[HTMLDivElement] = {
    ($(".exercise").divs filter isMethodDefined).toList
  }

  def getMethodAttr(e: HTMLElement): String = $(e).attr("data-method").getOrElse("").trim

  def isMethodDefined(e: HTMLElement): Boolean = getMethodAttr(e).nonEmpty

  def library: Option[String] = $("body").attr("data-library").toOption

  def section: Option[String] = $("body").attr("data-section").toOption

  def libraryAndSection: Option[(String, String)] = for {
    lib ← library
    sec ← section
  } yield (lib, sec)

  def methods: List[String] = allExercises.map(getMethodAttr(_))

  def methodName(e: HTMLElement): Option[String] = Option(getMethodAttr(e)) filter (_.nonEmpty)

  def methodParent(input: HTMLInputElement): Option[String] = methodName($(input).closest(".exercise").getDiv)

  def allInputs: Coeval[List[HTMLInputElement]] = Coeval { $(".exercise-code>input").inputs.toList }

  def inputs(el: HTMLElement): List[HTMLInputElement] = $(el).find("input").inputs.toList

  def findExerciseByMethod(method: String): Option[HTMLElement] = {
    allExercises.find(methodName(_) == Option(method))
  }

  def findExerciseCode(el: HTMLElement): Option[HTMLElement] = {
    $(el).find(".exercise-pre").all.headOption
  }

  def getInputsValues(exercise: HTMLElement): Seq[String] = inputsInExercise(exercise).map(_.value)

  def inputsInExercise(exercise: HTMLElement): Seq[HTMLInputElement] = $(exercise).find("input").inputs

  def getCodeBlocks: Coeval[Seq[HTMLElement]] = Coeval { $("code.exercise-code").elements }

  def getTextInCode(code: HTMLElement): String = $(code).text

  def replaceInputByRes(text: String): String = resAssert.replaceAllIn(text, """<input type="text" data-res="$1"/>""")

  def getInputLength(input: HTMLInputElement): Int = $(input).value.toString.length

  def isLogged: Boolean = $("#loggedUser").length > 0

  def setInputValue(input: HTMLInputElement, v: String): Coeval[Unit] = for {
    _ ← Coeval { $(input) `val` (v) }
    _ ← setInputWidth(input)
  } yield ()

  def inputSize(length: Int): Double = length match {
    case 0 ⇒ 12d
    case _ ⇒ 4d + length * 8.4
  }

  implicit class JQueryOps(j: JQuery) {

    def elements: Seq[HTMLElement] = all[HTMLElement]

    def divs: Seq[HTMLDivElement] = all[HTMLDivElement]

    def inputs: Seq[HTMLInputElement] = all[HTMLInputElement]

    def getDiv: HTMLDivElement = get[HTMLDivElement]

    def all[A <: dom.Element]: Seq[A] = j.toArray().collect { case d: A ⇒ d }

    def get[A <: dom.Element]: A = j.get().asInstanceOf[A]

  }

  @js.native
  trait BootstrapModal extends JQuery {
    def modal(action: String): BootstrapModal = js.native
  }

  implicit def jQueryToModal(jq: JQuery): BootstrapModal = jq.asInstanceOf[BootstrapModal]

}

/**
 * Copyright (C) 2013 Carnegie Mellon University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tbd.debug

import akka.pattern.ask
import scala.collection.mutable.{ListBuffer, Map}

import tbd.Constants._
import tbd.macros.{TbdMacros, functionToInvoke}
import tbd.ddg._

object TBD {
  import scala.language.experimental.macros

  type Adjustable[T] = tbd.Adjustable[T]
  type Changeable[T] = tbd.Changeable[T]
  type Context = tbd.Context
  type Mod[T] = tbd.Mod[T]

  val nodes = Map[Node, (Int, Tag, Array[StackTraceElement])]()

  @functionToInvoke("readInternal")
  def read[T, U]
      (mod: Mod[T])
      (reader: T => Changeable[U])
      (implicit c: Context): Changeable[U] =
    macro TbdMacros.readMacro[Changeable[U]]

  def readInternal[T, U]
      (mod: Mod[T],
       reader: T => Changeable[U],
       c: Context,
       readerId: Int,
       freeTerms: List[(String, Any)]): Changeable[U] = {
    val internalId = Node.getId()
    val tag = Tag.Read(c.read(mod), FunctionTag(readerId, freeTerms))(mod.id)
    val stack = Thread.currentThread().getStackTrace()

    val changeable = tbd.TBD.read(mod)(reader)(c)

    val readNode = c.ddg.reads(mod.id).last
    nodes(readNode) = (internalId, tag, stack)

    changeable
  }

  @functionToInvoke("read2Internal")
  def read2[T, U, V]
      (mod: Mod[T])
      (reader: T => (Changeable[U], Changeable[V]))
      (implicit c: Context): (Changeable[U], Changeable[V]) =
    macro TbdMacros.readMacro[(Changeable[U], Changeable[V])]

  def read2Internal[T, U, V](
      mod: Mod[T],
      reader: T => (Changeable[U], Changeable[V]),
      c: Context,
      readerId: Int,
      freeTerms: List[(String, Any)]): (Changeable[U], Changeable[V]) = {
    val internalId = Node.getId()
    val tag = Tag.Read(c.read(mod), FunctionTag(readerId, freeTerms))(mod.id)
    val stack = Thread.currentThread().getStackTrace()

    val changeable = tbd.TBD.read2(mod)(reader)(c)

    val readNode = c.ddg.reads(mod.id).last
    nodes(readNode) = (internalId, tag, stack)

    changeable
  }

  @functionToInvoke("modInternal")
  def mod[T](initializer: => Changeable[T])
     (implicit c: Context): Mod[T] = macro TbdMacros.modMacro[Mod[T]]

  def modInternal[T](
      initializer: => Changeable[T],
      c: Context,
      readerId: Int,
      freeTerms: List[(String, Any)]): Mod[T] = {
    val internalId = Node.getId()
    val stack = Thread.currentThread().getStackTrace()

    val mod = tbd.TBD.mod(initializer)(c)

    val tag = Tag.Mod(List(mod.id), FunctionTag(readerId, freeTerms))
    val modNode = c.currentTime.node
    nodes(modNode) = (internalId, tag, stack)

    mod
  }

  @functionToInvoke("mod2Internal")
  def mod2[T, U]
      (initializer: => (Changeable[T], Changeable[U]))
      (implicit c: Context): (Mod[T], Mod[U]) =
    macro TbdMacros.modMacro[(Mod[T], Mod[U])]

  def mod2Internal[T, U]
      (initializer: => (Changeable[T], Changeable[U]),
       c: Context,
       readerId: Int,
       freeTerms: List[(String, Any)]): (Mod[T], Mod[U]) = {
    val internalId = Node.getId()
    val stack = Thread.currentThread().getStackTrace()

    val (mod1, mod2) = tbd.TBD.mod2(initializer)(c)

    val tag = Tag.Mod(List(mod1.id, mod2.id), FunctionTag(readerId, freeTerms))
    val modNode = c.currentTime.node
    nodes(modNode) = (internalId, tag, stack)

    (mod1, mod2)
  }

  @functionToInvoke("modLeftInternal")
  def modLeft[T, U]
      (initializer: => (Changeable[T], Changeable[U]))
      (implicit c: Context): (Mod[T], Changeable[U]) =
    macro TbdMacros.modMacro[(Mod[T], Changeable[U])]

  def modLeftInternal[T, U]
      (initializer: => (Changeable[T], Changeable[U]),
       c: Context,
       readerId: Int,
       freeTerms: List[(String, Any)]): (Mod[T], Changeable[U]) = {
    val internalId = Node.getId()
    val stack = Thread.currentThread().getStackTrace()

    val (mod, changeable) = tbd.TBD.modLeft(initializer)(c)

    val tag = Tag.Mod(List(mod.id), FunctionTag(readerId, freeTerms))
    val modNode = c.currentTime.node
    nodes(modNode) = (internalId, tag, stack)

    (mod, changeable)
  }

  @functionToInvoke("modRightInternal")
  def modRight[T, U]
      (initializer: => (Changeable[T], Changeable[U]))
      (implicit c: Context): (Changeable[T], Mod[U]) =
    macro TbdMacros.modMacro[(Changeable[T], Mod[U])]

  def modRightInternal[T, U]
      (initializer: => (Changeable[T], Changeable[U]),
       c: Context,
       readerId: Int,
       freeTerms: List[(String, Any)]): (Changeable[T], Mod[U]) = {
    val internalId = Node.getId()
    val stack = Thread.currentThread().getStackTrace()
    val (changeable, mod) = tbd.TBD.modRight(initializer)(c)

    val tag = Tag.Mod(List(mod.id), FunctionTag(readerId, freeTerms))
    val modNode = c.currentTime.node
    nodes(modNode) = (internalId, tag, stack)

    (changeable, mod)
  }

  def write[T](value: T)(implicit c: Context): Changeable[T] = {
    val internalId = Node.getId()
    val stack = Thread.currentThread().getStackTrace()

    val changeable = tbd.TBD.write(value)
    val mod = changeable.mod.asInstanceOf[Mod[Any]]

    val writeNode = c.ddg.addWrite(mod, null, c)
    writeNode.endTime = c.ddg.nextTimestamp(writeNode, c)

    val writes = List(SingleWriteTag(mod.id, c.read(mod)))
    val tag = Tag.Write(writes)
    nodes(writeNode) = (internalId, tag, stack)

    changeable
  }

  def write2[T, U](value: T, value2: U)
      (implicit c: Context): (Changeable[T], Changeable[U]) = {
    val internalId = Node.getId()
    val stack = Thread.currentThread().getStackTrace()

    val changeables = tbd.TBD.write2(value, value2)
    val mod1 = c.currentMod.asInstanceOf[Mod[Any]]
    val mod2 = c.currentMod2.asInstanceOf[Mod[Any]]

    val writeNode = c.ddg.addWrite(mod1, mod2, c)

    writeNode.endTime = c.ddg.nextTimestamp(writeNode, c)

    val writes = List(
      SingleWriteTag(mod1.id, c.read(mod1)),
      SingleWriteTag(mod1.id, c.read(mod1)))
    val tag = Tag.Write(writes)
    nodes(writeNode) = (internalId, tag, stack)

    changeables
  }

  def writeLeft[T, U](value: T, changeable: Changeable[U])
     (implicit c: Context): (Changeable[T], Changeable[U]) = {
    val internalId = Node.getId()
    val stack = Thread.currentThread().getStackTrace()

    if (changeable.mod != c.currentMod2) {
      println("WARNING - mod parameter to writeLeft doesn't match currentMod2")
    }

    val changeables = tbd.TBD.writeLeft(value, changeable)
    val mod = c.currentMod.asInstanceOf[Mod[Any]]

    val writeNode = c.ddg.addWrite(mod, null, c)

    writeNode.endTime = c.ddg.nextTimestamp(writeNode, c)

    val writes = List(SingleWriteTag(mod.id, c.read(mod)))
    val tag = Tag.Write(writes)
    nodes(writeNode) = (internalId, tag, stack)

    changeables
  }

  def writeRight[T, U](changeable: Changeable[T], value2: U)
      (implicit c: Context): (Changeable[T], Changeable[U]) = {
    val internalId = Node.getId()
    val stack = Thread.currentThread().getStackTrace()

    if (changeable.mod != c.currentMod) {
      println("WARNING - mod parameter to writeRight doesn't match currentMod")
    }

    val changeables = tbd.TBD.writeRight(changeable, value2)
    val mod2 = c.currentMod2.asInstanceOf[Mod[Any]]

    val writeNode = c.ddg.addWrite(null, mod2, c)

    writeNode.endTime = c.ddg.nextTimestamp(writeNode, c)

    val writes = List(SingleWriteTag(mod2.id, c.read(mod2)))
    val tag = Tag.Write(writes)
    nodes(writeNode) = (internalId, tag, stack)

    changeables
  }

  @functionToInvoke("parInternal")
  def par[T](one: Context => T): Parizer[T] =
	macro TbdMacros.parOneMacro[Parizer[T]]

  def parInternal[T](
      one: Context => T,
      id: Int,
      closedTerms: List[(String, Any)]): Parizer[T] = {
    new Parizer(one, id, closedTerms)
  }
}

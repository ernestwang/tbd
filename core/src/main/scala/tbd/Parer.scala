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
package tbd

import akka.pattern.ask
import scala.concurrent.Await

import tbd.macros.{TbdMacros, functionToInvoke}

import tbd.Constants._
import tbd.messages._
import tbd.worker.Worker
import tbd.ddg.FunctionTag

class Parer[T](one: Context => T, id1: Int, closedTerms1: List[(String, Any)]) {
  import scala.language.experimental.macros

  @functionToInvoke("parTwoInternal")
  def and[U](two: Context => U)(implicit c: Context): (T, U) = macro TbdMacros.parTwoMacro[(T, U)]

  def parTwoInternal[U](
      two: Context => U,
      c: Context,
      id2: Int,
      closedTerms2: List[(String, Any)]): (T, U) = {

    val workerProps1 =
      Worker.props(c.id + "-" + c.workerId, c.worker.datastoreRef, c.worker.self)
    val workerRef1 = c.worker.context.system.actorOf(workerProps1, c.id + "-" + c.workerId)
    c.workerId += 1

    val adjust1 = new Adjustable[T] { def run(implicit c: Context) = one(c) }
    val oneFuture = workerRef1 ? RunTaskMessage(adjust1)

    val workerProps2 =
      Worker.props(c.id + "-" + c.workerId, c.worker.datastoreRef, c.worker.self)
    val workerRef2 = c.worker.context.system.actorOf(workerProps2, c.id + "-" + c.workerId)
    c.workerId += 1

    val adjust2 = new Adjustable[U] { def run(implicit c: Context) = two(c) }
    val twoFuture = workerRef2 ? RunTaskMessage(adjust2)

    c.worker.ddg.addPar(workerRef1, workerRef2, c.currentParent,
                      FunctionTag(id1, closedTerms1),
                      FunctionTag(id2, closedTerms2))

    val oneRet = Await.result(oneFuture, DURATION).asInstanceOf[T]
    val twoRet = Await.result(twoFuture, DURATION).asInstanceOf[U]
    (oneRet, twoRet)
  }
}

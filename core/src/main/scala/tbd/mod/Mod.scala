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
package tbd.mod

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._

import tbd.messages._

class Mod[T](datastoreRef: ActorRef) {
  val id = new ModId()

  def read(workerRef: ActorRef = null): T = {
    implicit val timeout = Timeout(5 seconds)
    val ret =
      if (workerRef != null) {
        val readFuture = datastoreRef ? ReadModMessage(id, workerRef)
        Await.result(readFuture, timeout.duration)
      } else {
        val readFuture = datastoreRef ? GetMessage("mods", id.value)
        Await.result(readFuture, timeout.duration)
      }

    ret match {
      case NullMessage => null.asInstanceOf[T]
      case _ => ret.asInstanceOf[T]
    }
  }

  def update(newValue: T) {
    datastoreRef ! UpdateModMessage(id, newValue)
  }

  override def toString = read().toString
}

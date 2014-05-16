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
import akka.actor.ActorRef
import akka.event.Logging
import scala.collection.mutable.{ArrayBuffer, Map, Set}
import scala.concurrent.Await

import tbd.Constants._
import tbd.ddg.{Node, Timestamp}
import tbd.master.Main
import tbd.memo.{DummyLift, Lift, MemoEntry}
import tbd.messages._
import tbd.mod.{Dest, Mod, ModId}
import tbd.worker.{Worker, Task}

object TBD {
  var id = 0
}

class TBD(
    id: String,
    aWorker: Worker) {
  val worker = aWorker
  var initialRun = true

  // The Node representing the currently executing reader.
  var currentParent: Node = worker.ddg.root
  val input = new Reader(worker)

  val log = Logging(worker.context.system, "TBD" + id)

  // Represents the number of PebblingFinishedMessages this worker is waiting
  // on before it can finish.
  var awaiting = 0

  // Maps ModIds to the mod's value for LocalMods created in this TBD.
  val mods = scala.collection.mutable.Map[ModId, Any]()

  // Maps modIds to the workers that have read the corresponding mod.
  val dependencies = Map[ModId, Set[ActorRef]]()

  // Contains a list of mods that have been updated since the last run of change
  // propagation, to determine when memo matches can be made.
  val updatedMods = Set[ModId]()

  // The timestamp of the read currently being reexecuting during change
  // propagation.
  var reexecutionStart: Timestamp = null

  // The timestamp of the node immediately after the end of the read being
  // reexecuted.
  var reexecutionEnd: Timestamp = null
  
  def read2[T, V, U](a: Mod[T], b: Mod[V])
                    (reader: (T, V) => (Changeable[U])): Changeable[U] = {
    read(a)((a) => {
      read(b)((b) => reader(a, b))
    })
  }

  def increment(mod: Mod[Int]): Mod[Int] = {
    this.mod((dest: Dest[Int]) =>
      read(mod)(mod => 
        write(dest, mod + 1)))
  }

  def read[T, U](mod: Mod[T])(reader: T => (Changeable[U])): Changeable[U] = {
    val readNode = worker.ddg.addRead(mod.asInstanceOf[Mod[Any]],
                                      currentParent,
                                      reader.asInstanceOf[Any => Changeable[Any]])

    val outerReader = currentParent
    currentParent = readNode

    val value =
      if (mods.contains(mod.id)) {
        mods(mod.id).asInstanceOf[T]
      } else {
        mod.read(worker.self)
      }

    val changeable = reader(value)
    currentParent = outerReader

    readNode.endTime = worker.ddg.nextTimestamp(readNode)

    changeable
  }

  def write[T](dest: Dest[T], value: T): Changeable[T] = {
    awaiting += dest.mod.update(value, worker.self, this)

    if (worker.ddg.reads.contains(dest.mod.id)) {
      worker.ddg.modUpdated(dest.mod.id)
      updatedMods += dest.mod.id
    }

    val changeable = new Changeable(dest.mod)
    if (Main.debug) {
      worker.ddg.addWrite(changeable.mod.asInstanceOf[Mod[Any]], currentParent)
    }

    changeable
  }

  def createMod[T](value: T): Mod[T] = {
    this.mod((dest: Dest[T]) => {
      write(dest, value)
    })
  }

  def mod[T](initializer: Dest[T] => Changeable[T]): Mod[T] = {
    val modId = new ModId(worker.id + "." + worker.nextModId)
    val d = new Dest[T](worker, modId)
    dependencies(d.mod.id) = Set()
    initializer(d).mod
    d.mod
  }

  var workerId = 0
  def par[T, U](one: TBD => T, two: TBD => U): Tuple2[T, U] = {
    val task1 =  new Task(((tbd: TBD) => one(tbd)))
    val workerProps1 =
      Worker.props(id + "-" + workerId, worker.datastoreRef, worker.self)
    val workerRef1 = worker.context.system.actorOf(workerProps1, id + "-" + workerId)
    workerId += 1
    val oneFuture = workerRef1 ? RunTaskMessage(task1)

    val task2 =  new Task(((tbd: TBD) => two(tbd)))
    val workerProps2 =
      Worker.props(id + "-" + workerId, worker.datastoreRef, worker.self)
    val workerRef2 = worker.context.system.actorOf(workerProps2, id + "-" +workerId)
    workerId += 1
    val twoFuture = workerRef2 ? RunTaskMessage(task2)

    worker.ddg.addPar(workerRef1, workerRef2, currentParent)

    val oneRet = Await.result(oneFuture, DURATION).asInstanceOf[T]
    val twoRet = Await.result(twoFuture, DURATION).asInstanceOf[U]
    new Tuple2(oneRet, twoRet)
  }

  def updated(args: List[ModId]): Boolean = {
    var updated = false

    for (arg <- args) {
      if (updatedMods.contains(arg)) {
	updated = true
      }
    }

    updated
  }

  var liftId = 0
  def makeLift[T](dummy:Boolean = false) = {
    if(dummy) {
      new DummyLift[T](this, 0)
    } else {
      liftId += 1
      new Lift[T](this, liftId)
    }
  }

  def map[T, U](arr: Array[Mod[T]], func: T => U): Array[Mod[U]] = {
    arr.map((elem) =>
      mod((dest: Dest[U]) => read(elem)((value) => write(dest, func(value)))))
  }


}

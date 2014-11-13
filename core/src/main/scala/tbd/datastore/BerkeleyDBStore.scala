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
package tbd.datastore

import akka.actor.{ActorRef, ActorContext, Props}
import akka.pattern.ask
import com.sleepycat.je.{Environment, EnvironmentConfig}
import com.sleepycat.persist.{EntityStore, StoreConfig}
import com.sleepycat.persist.model.{Entity, PrimaryKey, SecondaryKey}
import com.sleepycat.persist.model.Relationship
import java.io._
import scala.collection.mutable.Map
import scala.concurrent.Await

import tbd.Mod
import tbd.Constants._
import tbd.messages._

class LRUNode(
  val key: ModId,
  var value: Any,
  var previous: LRUNode,
  var next: LRUNode
)

class BerkeleyDBStore(cacheSize: Int, context: ActorContext) extends KVStore {
  println("new BerkeleyDBStore " + cacheSize)
  private var environment: Environment = null
  private var store: EntityStore = null

  private val envConfig = new EnvironmentConfig()
  envConfig.setAllowCreate(true)
  val storeConfig = new StoreConfig()
  storeConfig.setAllowCreate(true)

  val random = new scala.util.Random()
  private val envHome = new File("/tmp/tbd_berkeleydb" + random.nextInt())
  envHome.mkdir()

  try {
    // Open the environment and entity store
    environment = new Environment(envHome, envConfig)
    store = new EntityStore(environment, "EntityStore", storeConfig)
  } catch {
    case fnfe: FileNotFoundException => {
      System.err.println("setup(): " + fnfe.toString())
      System.exit(-1)
    }
  }

  val pIdx = store.getPrimaryIndex(classOf[ModId], classOf[ModEntity])

  // LRU cache
  private val values = Map[ModId, LRUNode]()
  private val tail = new LRUNode(null, null, null, null)
  private var head = tail

  // Statistics
  var readCount = 0
  var writeCount = 0
  var deleteCount = 0

  println("done initializing")

  def put(key: ModId, value: Any) {
    if (values.contains(key)) {
      values(key).value = value
    } else {
      val newNode = new LRUNode(key, value, null, head)
      values(key) = newNode

      head.previous = newNode
      head = newNode

      if (values.size > cacheSize) {
        evict()
      }
    }
  }

  private def evict() {
    while (values.size > cacheSize) {
      println("evicting")
      val toEvict = tail.previous

      val key = toEvict.key
      val value = toEvict.value

      writeCount += 1
      val entity = new ModEntity()
      entity.key = key

      val byteOutput = new ByteArrayOutputStream()
      val objectOutput = new ObjectOutputStream(byteOutput)
      objectOutput.writeObject(value)
      entity.value = byteOutput.toByteArray

      pIdx.put(entity)

      values -= toEvict.key

      tail.previous = toEvict.previous
      toEvict.previous.next = tail
    }
  }

  def get(key: ModId): Any = {
    if (values.contains(key)) {
      values(key).value
    } else {
      readCount += 1
      val byteArray = pIdx.get(key).value

      val byteInput = new ByteArrayInputStream(byteArray)
      val objectInput = new ObjectInputStream(byteInput)
      val obj = objectInput.readObject()

      // No idea why this is necessary.
      if (obj != null && obj.isInstanceOf[Tuple2[_, _]]) {
        obj.toString
      }

      obj
    }
  }

  def remove(key: ModId) {
    if (values.contains(key)) {
      values -= key
    }

    //deleteCount += 1
    //pIdx.delete(key)
  }

  def contains(key: ModId) = {
    values.contains(key) || pIdx.contains(key)
  }

  def shutdown() {
    println("Shutting down. writes = " + writeCount + ", reads = " +
            readCount + ", deletes = " + deleteCount)
    store.close()
    environment.close()
  }
}

@Entity
class ModEntity {
  @PrimaryKey
  var key: ModId = null

  var value: Array[Byte] = null
}

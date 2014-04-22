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
package tbd.test

import org.scalatest._
import scala.collection.mutable.Map

import tbd.{Adjustable, Mutator, TBD}
import tbd.datastore.Dataset

class DatasetTest extends Adjustable {
  def run(tbd: TBD): Dataset[Int] = {
    tbd.input.getDataset[Int](partitions = 1)
  }
}

class MutatorTests extends FlatSpec with Matchers {
  val rand = new scala.util.Random()

  def updateValue(mutator: Mutator, answer: Map[Int, Int]) {
    val index = rand.nextInt(answer.size)
    var i = 0
    for ((key, value) <- answer) {
      if (i == index) {
	val newValue = rand.nextInt(1000)
	mutator.update(key, newValue)
	answer(key) = newValue
      }

      i += 1
    }
  }

  def insertValue(mutator: Mutator, answer: Map[Int, Int], i: Int) {
    val value = rand.nextInt(1000)
    answer += (i -> value)
    mutator.put(i, value)
  }

  def removeValue(mutator: Mutator, answer: Map[Int, Int]) {
    val index = rand.nextInt(answer.size)
    var j = 0
    for ((key, value) <- answer) {
      if (j == index) {
	mutator.remove(key)
	answer -= key
      }
      j += 1
    }
  }

  "DatasetTests" should "update the dataset correctly" in {
    val mutator = new Mutator()
    val answer = Map[Int, Int]()

    var  i  = 0
    while (i < 100) {
      answer += (i -> i)
      mutator.put(i, i)
      i += 1
    }
    val output = mutator.run[Dataset[Int]](new DatasetTest())
    output.toBuffer().sortWith(_ < _) should be (answer.values.toBuffer.sortWith(_ < _))

    for (j <- 0 to i - 1) {
      updateValue(mutator, answer)
    }

    output.toBuffer().sortWith(_ < _) should be (answer.values.toBuffer.sortWith(_ < _))

    while (i < 200) {
      insertValue(mutator, answer, i)
      i += 1
    }

    output.toBuffer().sortWith(_ < _) should be (answer.values.toBuffer.sortWith(_ < _))

    for (j <- 0 to 99) {
      removeValue(mutator, answer)
    }

    output.toBuffer().sortWith(_ < _) should be (answer.values.toBuffer.sortWith(_ < _))

    for (j <- 0 to 99) {
      rand.nextInt(3) match {
	case 0 => {
	  insertValue(mutator, answer, i)
	  i += 1
	}
	case 1 => {
	  removeValue(mutator, answer)
	}
	case 2 => {
	  updateValue(mutator, answer)
	}
      }
    }

    output.toBuffer().sortWith(_ < _) should be (answer.values.toBuffer.sortWith(_ < _))
  }
}
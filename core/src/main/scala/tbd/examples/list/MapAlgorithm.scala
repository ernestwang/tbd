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
package tbd.examples.list

import scala.collection.{GenIterable, GenMap, Seq}
import scala.collection.mutable.Map
import scala.collection.parallel.{ForkJoinTaskSupport, ParIterable}
import scala.concurrent.forkjoin.ForkJoinPool

import tbd._
import tbd.datastore.StringData
import tbd.list._

object MapAlgorithm {
  def mapper(pair: (Int, String)): (Int, Int) = {
    var count = 0
    for (word <- pair._2.split("\\W+")) {
      count += 1
    }
    (pair._1, count)
  }
}

class MapAdjust(list: AdjustableList[Int, String])
  extends Adjustable[AdjustableList[Int, Int]] {
  def run(implicit c: Context) = {
    list.map(MapAlgorithm.mapper)
  }
}

class MapAlgorithm(_conf: Map[String, _], _listConf: ListConf)
    extends Algorithm[String, AdjustableList[Int, Int]](_conf, _listConf) {
  val input = mutator.createList[Int, String](listConf)

  val adjust = new MapAdjust(input.getAdjustableList())

  val data = new StringData(input, count, mutations, Experiment.check)

  var naiveTable: ParIterable[String] = _
  def generateNaive() {
    data.generate()
    naiveTable = Vector(data.table.values.toSeq: _*).par
    naiveTable.tasksupport =
      new ForkJoinTaskSupport(new ForkJoinPool(partitions * 2))
  }

  def runNaive() {
    naiveHelper(naiveTable)
  }

  private def naiveHelper(input: GenIterable[String]) = {
    input.map(MapAlgorithm.mapper(0, _)._2)
  }

  def checkOutput(table: Map[Int, String], output: AdjustableList[Int, Int]) = {
    val sortedOutput = output.toBuffer(mutator).map(_._2).sortWith(_ < _)
    val answer = naiveHelper(table.values)

    sortedOutput == answer.toBuffer.sortWith(_ < _)
  }
}

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

import scala.collection.GenIterable
import scala.collection.mutable.Map

trait Data[Input] {
  val table = Map[Int, Input]()

  // Fills in 'table' with the initial data set. This must be the first method
  // called on a new Data object.
  def generate()

  // Transfers to data in 'table' into an Input object.
  def load()

  // Removed all of the values in 'table' while keeping the keys - intended for
  // experiments with large datasets that don't care about checking the output.
  def clearValues()

  // Changes a single value (possibly an update, removal, or insertion).
  def update()
}

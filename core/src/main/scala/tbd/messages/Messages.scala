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
package tbd.messages

import tbd.{Adjustable, Changeable, TBD}
import tbd.ddg.ReadId
import tbd.mod.Mod
import tbd.mod.ModId
import tbd.worker.Task

// DDG
case class AddReadMessage(modId: ModId, readId: ReadId)
case class AddWriteMessage(readId: ReadId, modId: ModId)
case class AddCallMessage(outerCall: ReadId, innerCall: ReadId)
case class ToStringMessage

// Input
case class CreateTableMessage(table: String)
case class GetMessage(table: String, key: Int)
case class PutMessage(table: String, key: Int, value: Any)
case class PutModMessage(table: String, key: Int, value: Any)
case class CreateModMessage(value: Any)
case class UpdateModMessage(key: Int, value: Any)
case class PutMatrixMessage(table: String, key: Int, value: Array[Array[Int]])
case class GetArrayMessage(table: String)
case class GetListMessage(table: String)
case class NullMessage

// Master
case class RunMessage(adjust: Adjustable)
case class PropagateMessage
case class RunTaskMessage(func: Task)
case class ShutdownMessage

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

import akka.util.Timeout
import java.net.InetAddress
import scala.concurrent.duration._

object Constants {
  var DURATION = 10.seconds
  implicit var TIMEOUT = Timeout(DURATION)

  type ModId = String
  type InputId = Int

  val localhost = InetAddress.getLocalHost.getHostAddress

  val akkaConf = """
    akka.actor.provider = akka.remote.RemoteActorRefProvider

    akka.log-dead-letters = off

    akka.remote.enabled-transports = [akka.remote.netty.tcp]

    akka.remote.netty.tcp.maximum-frame-size = 8000000b
  """
}

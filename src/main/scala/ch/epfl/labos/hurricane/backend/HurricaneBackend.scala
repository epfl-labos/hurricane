/*
 * Copyright (c) 2018 EPFL IC LABOS.
 * 
 * This file is part of Hurricane
 * (see https://labos.epfl.ch/hurricane).
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ch.epfl.labos.hurricane.backend

import akka.actor._
import akka.pattern._
import akka.util._
import better.files.File
import ch.epfl.labos.hurricane.Config.HurricaneConfig.NodeAddress
import ch.epfl.labos.hurricane._
import ch.epfl.labos.hurricane.common._
import com.typesafe.config.ConfigValueFactory
import org.rogach.scallop.ScallopConf

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.duration._
import scala.language.postfixOps

object HurricaneBackendActor {

  val name: String = "master"

  def props(subDirectory: Option[String]): Props = Props(classOf[HurricaneBackendActor], subDirectory)

}

class HurricaneBackendActor(subDirectory: Option[String]) extends Actor with ActorLogging {

  import context.dispatcher

  implicit val timeout = Timeout(300 seconds)

  val rootFile = subDirectory match {
    case Some(sd) => File(Config.HurricaneConfig.BackendConfig.DataConfig.dataDirectory.getAbsolutePath) / sd
    case _ => File(Config.HurricaneConfig.BackendConfig.DataConfig.dataDirectory.getAbsolutePath)
  }

  val bags = MMap.empty[Bag, ActorRef]

  def receive = {
    case cmd: Command =>
      val senderId = (sender.path.address.host, sender.path.address.port) match {
        case (Some(host), Some(port)) => Config.HurricaneConfig.FrontendConfig.NodesConfig.nodes.indexOf(NodeAddress(host, port))
        case _ => -1
      }

      cmd match {
        case Create(fingerprint, bag) if bag.id.startsWith("work_") =>
          bags += bag -> context.actorOf(HurricaneWorkBag.props(bag, rootFile))
        case _ => // do nothing
      }

      (bagActor(cmd.bag) ? cmd) pipeTo sender
  }

  log.info(s"Started backend!")

  def bagActor(bag: Bag): ActorRef =
    bags getOrElseUpdate(bag, context.actorOf(HurricaneIO.props(bag, rootFile)))

}

class BackendConf(arguments: Seq[String]) extends ScallopConf(arguments) {

  val me = opt[Int](default = None)

  verify()

}

object HurricaneBackend {

  def main(args: Array[String]): Unit = {
    val arguments = new BackendConf(args)

    val config =
      if (arguments.me.supplied) {
        //Config.HurricaneConfig.me = arguments.me()
        Config.HurricaneConfig.BackendConfig.backendConfig
          .withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.nodes(arguments.me()).hostname))
          .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.nodes(arguments.me()).port))
          .withValue("akka.remote.netty.tcp.bind-hostname", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.nodes(arguments.me()).hostname))
          .withValue("akka.remote.netty.tcp.bind-port", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.nodes(arguments.me()).port))
          .withValue("akka.remote.artery.canonical.hostname", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.nodes(arguments.me()).hostname))
          .withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.nodes(arguments.me()).port))
          .withValue("akka.remote.artery.bind.hostname", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.nodes(arguments.me()).hostname))
          .withValue("akka.remote.artery.bind.port", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.nodes(arguments.me()).port))
      } else {
        Config.HurricaneConfig.BackendConfig.backendConfig
          .withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.localNode.hostname))
          .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.localNode.port))
          .withValue("akka.remote.artery.canonical.hostname", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.localNode.hostname))
          .withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(Config.HurricaneConfig.BackendConfig.NodesConfig.localNode.port))
      }

    val system = ActorSystem("HurricaneBackend", config)

    ch.epfl.labos.hurricane.frontend.Statistics.init(system.dispatchers.lookup("hurricane.backend.statistics-dispatcher"))

    val port =
      if (arguments.me.supplied) {
        Config.HurricaneConfig.BackendConfig.NodesConfig.nodes(arguments.me()).port
      } else {
        Config.HurricaneConfig.BackendConfig.NodesConfig.localNode.port
      }

    Config.ModeConfig.mode match {
      case Config.ModeConfig.Dev =>
        system.actorOf(HurricaneBackendActor.props(Some(port + "")), HurricaneBackendActor.name)
      case Config.ModeConfig.Prod =>
        system.actorOf(HurricaneBackendActor.props(None), HurricaneBackendActor.name)
    }
  }

}

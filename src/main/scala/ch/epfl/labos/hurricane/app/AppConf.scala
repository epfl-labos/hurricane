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
package ch.epfl.labos.hurricane.app

import ch.epfl.labos.hurricane.frontend.FrontendConf

object AppConf {

  def fromScallop(conf: FrontendConf): AppConf =
    AppConf(conf.file(), conf.size(), conf.textMode(), Map.empty[String, String] ++ conf.genprops, Map.empty[String, String] ++ conf.hashprops, conf.simulation())

}

case class AppConf(file: String, size: Long, textMode: Boolean, genprops: Map[String, String], hashprops: Map[String, String], simulation: Int)

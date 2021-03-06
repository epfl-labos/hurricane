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
package ch.epfl.labos.hurricane.serialization

import java.nio.ByteBuffer

import akka.serialization._
import ch.epfl.labos.hurricane.Config
import ch.epfl.labos.hurricane.common._

import io.jvm.uuid._


object WireSerializer {
  val COMMAND_LENGTH = 4
  val ID_LENGTH = 36
  val INT_LENGTH = 4
  val LONG_LENGTH = 8
  val DOUBLE_LENGTH = 8

  val CREATE_COMMAND = "CREA"
  val DRAIN_COMMAND = "DRAI"
  val FILL_COMMAND = "FILL"
  val SEEK_AND_FILL_COMMAND = "SFIL"
  val FLUSH_COMMAND = "FLSH"
  val TRUNC_COMMAND = "TRUN"
  val REWIND_COMMAND = "RWND"
  val PROGRESS_COMMAND = "PROG"
  val REPLAY_COMMAND = "RPLA"

  val FILLED_RESPONSE = "FLLD"
  val ACK_RESPONSE = "ACK!"
  val NACK_RESPONSE = "NACK"
  val EOF_RESPONSE = "EOF!"
  val PROGRESS_RESPONSE = "PRES"
}

class WireSerializer extends Serializer {

  import WireSerializer._

  // This is whether "fromBinary" requires a "clazz" or not
  def includeManifest: Boolean = true

  // Pick a unique identifier for your Serializer,
  // you've got a couple of billions to choose from,
  // 0 - 16 is reserved by Akka itself
  def identifier = 31415926

  // "toBinary" serializes the given object to an Array of Bytes
  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case Create(fingerprint, bag) =>
      val buf = ByteBuffer.wrap(new Array[Byte](COMMAND_LENGTH + 2 * ID_LENGTH))
      buf.put(CREATE_COMMAND.getBytes)
      buf.put(fingerprint.getBytes)
      buf.put(bag.id.getBytes)
      buf.array
    case Drain(fingerprint, bag, chunk) =>
      chunk.cmd(DRAIN_COMMAND)
      chunk.bag(bag)
      // not fingerprinting chunk here
      chunk.array
    case Fill(fingerprint, bag, count) =>
      val buf = ByteBuffer.wrap(new Array[Byte](COMMAND_LENGTH + 2 * ID_LENGTH + INT_LENGTH))
      buf.put(FILL_COMMAND.getBytes)
      buf.put(fingerprint.getBytes)
      buf.put(bag.id.getBytes)
      buf.putInt(COMMAND_LENGTH + 2 * ID_LENGTH, count)
      buf.array
    case SeekAndFill(fingerprint, bag, offset, count) =>
      val buf = ByteBuffer.wrap(new Array[Byte](COMMAND_LENGTH + 2 * ID_LENGTH + INT_LENGTH + INT_LENGTH))
      buf.put(FILL_COMMAND.getBytes)
      buf.put(fingerprint.getBytes)
      buf.put(bag.id.getBytes)
      buf.putInt(COMMAND_LENGTH + 2 * ID_LENGTH, offset)
      buf.putInt(COMMAND_LENGTH + 2 * ID_LENGTH + INT_LENGTH, count)
      buf.array
    case Flush(fingerprint, bag) =>
      val buf = ByteBuffer.wrap(new Array[Byte](COMMAND_LENGTH + 2 * ID_LENGTH))
      buf.put(FLUSH_COMMAND.getBytes)
      buf.put(fingerprint.getBytes)
      buf.put(bag.id.getBytes)
      buf.array
    case Trunc(fingerprint, bag) =>
      val buf = ByteBuffer.wrap(new Array[Byte](COMMAND_LENGTH + 2 * ID_LENGTH))
      buf.put(TRUNC_COMMAND.getBytes)
      buf.put(fingerprint.getBytes)
      buf.put(bag.id.getBytes)
      buf.array
    case Rewind(fingerprint, bag) =>
      val buf = ByteBuffer.wrap(new Array[Byte](COMMAND_LENGTH + 2 * ID_LENGTH))
      buf.put(REWIND_COMMAND.getBytes)
      buf.put(fingerprint.getBytes)
      buf.put(bag.id.getBytes)
      buf.array
    case Progress(fingerprint, bag) =>
      val buf = ByteBuffer.wrap(new Array[Byte](COMMAND_LENGTH + 2 * ID_LENGTH))
      buf.put(PROGRESS_COMMAND.getBytes)
      buf.put(fingerprint.getBytes)
      buf.put(bag.id.getBytes)
      buf.array
    case Replay(oldWorker, newWorker, bag) =>
      val buf = ByteBuffer.wrap(new Array[Byte](COMMAND_LENGTH + 3 * ID_LENGTH + ID_LENGTH))
      buf.put(REPLAY_COMMAND.getBytes)
      buf.put(oldWorker.getBytes)
      buf.put(newWorker.getBytes)
      buf.put(bag.id.getBytes)
      buf.array

    case Filled(chunk) =>
      chunk.cmd(FILLED_RESPONSE)
      chunk.array
    case Ack =>
      ACK_RESPONSE.getBytes
    case Nack =>
      NACK_RESPONSE.getBytes
    case EOF =>
      EOF_RESPONSE.getBytes
    case ProgressReport(done, size) =>
      val buf = ByteBuffer.wrap(new Array[Byte](COMMAND_LENGTH + DOUBLE_LENGTH + LONG_LENGTH))
      buf.put(PROGRESS_RESPONSE.getBytes)
      buf.putDouble(COMMAND_LENGTH, done)
      buf.putLong(COMMAND_LENGTH + DOUBLE_LENGTH, size)
      buf.array

  }

  // "fromBinary" deserializes the given array,
  // using the type hint (if any, see "includeManifest" above)
  def fromBinary(
                  bytes: Array[Byte],
                  clazz: Option[Class[_]]): AnyRef = {
    if(bytes.length >= Config.HurricaneConfig.BackendConfig.DataConfig.chunkSize) {
      val chunk = Chunk.wrap(bytes)
      chunk.cmd match {
        case DRAIN_COMMAND => Drain(UUID(0, 0).toString, chunk.bag, chunk)
        case FILLED_RESPONSE => Filled(chunk)
        case other => throw new RuntimeException("Unknown large message received! " + other)
      }
    } else {
      val buf = ByteBuffer.wrap(bytes)
      val cmd = new Array[Byte](COMMAND_LENGTH)
      buf.get(cmd)
      new String(cmd) match {
        case CREATE_COMMAND =>
          val fingerprintBytes = new Array[Byte](ID_LENGTH)
          buf.get(fingerprintBytes)
          val bagBytes = new Array[Byte](ID_LENGTH)
          buf.get(bagBytes)
          Create(new String(fingerprintBytes).trim, Bag(new String(bagBytes).trim))
        case FILL_COMMAND =>
          val fingerprintBytes = new Array[Byte](ID_LENGTH)
          buf.get(fingerprintBytes)
          val bagBytes = new Array[Byte](ID_LENGTH)
          buf.get(bagBytes)
          val count = buf.getInt(COMMAND_LENGTH + ID_LENGTH)
          Fill(new String(fingerprintBytes).trim, Bag(new String(bagBytes).trim), count)
        case SEEK_AND_FILL_COMMAND =>
          val fingerprintBytes = new Array[Byte](ID_LENGTH)
          buf.get(fingerprintBytes)
          val bagBytes = new Array[Byte](ID_LENGTH)
          buf.get(bagBytes)
          val offset = buf.getInt(COMMAND_LENGTH + ID_LENGTH)
          val count = buf.getInt(COMMAND_LENGTH + ID_LENGTH + INT_LENGTH)
          SeekAndFill(new String(fingerprintBytes).trim, Bag(new String(bagBytes).trim), offset, count)
        case FLUSH_COMMAND =>
          val fingerprintBytes = new Array[Byte](ID_LENGTH)
          buf.get(fingerprintBytes)
          val bagBytes = new Array[Byte](ID_LENGTH)
          buf.get(bagBytes)
          Flush(new String(fingerprintBytes).trim, Bag(new String(bagBytes).trim))
        case TRUNC_COMMAND =>
          val fingerprintBytes = new Array[Byte](ID_LENGTH)
          buf.get(fingerprintBytes)
          val bagBytes = new Array[Byte](ID_LENGTH)
          buf.get(bagBytes)
          Trunc(new String(fingerprintBytes).trim, Bag(new String(bagBytes).trim))
        case REWIND_COMMAND =>
          val fingerprintBytes = new Array[Byte](ID_LENGTH)
          buf.get(fingerprintBytes)
          val bagBytes = new Array[Byte](ID_LENGTH)
          buf.get(bagBytes)
          Rewind(new String(fingerprintBytes).trim, Bag(new String(bagBytes).trim))
        case PROGRESS_COMMAND =>
          val fingerprintBytes = new Array[Byte](ID_LENGTH)
          buf.get(fingerprintBytes)
          val bagBytes = new Array[Byte](ID_LENGTH)
          buf.get(bagBytes)
          Progress(new String(fingerprintBytes).trim, Bag(new String(bagBytes).trim))
        case PROGRESS_RESPONSE =>
          ProgressReport(buf.getDouble(COMMAND_LENGTH), buf.getLong(COMMAND_LENGTH + DOUBLE_LENGTH))
        case REPLAY_COMMAND =>
          val oldWorkerBytes = new Array[Byte](ID_LENGTH)
          buf.get(oldWorkerBytes)
          val newWorkerBytes = new Array[Byte](ID_LENGTH)
          buf.get(newWorkerBytes)
          val bagBytes = new Array[Byte](ID_LENGTH)
          buf.get(bagBytes)
          Replay(new String(oldWorkerBytes).trim, new String(newWorkerBytes).trim, Bag(new String(bagBytes).trim))

        case ACK_RESPONSE => Ack
        case NACK_RESPONSE => Nack
        case EOF_RESPONSE => EOF
      }
    }
  }
}


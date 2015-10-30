/*
 * Licensed to Cloudera, Inc. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Cloudera, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.hue.livy.spark

import com.cloudera.hue.livy.spark.SparkProcessBuilder.{RelativePath, AbsolutePath, Path}
import com.cloudera.hue.livy.{LivyConf, Logging}

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

object SparkProcessBuilder {
  def apply(livyConf: LivyConf): SparkProcessBuilder = {
    new SparkProcessBuilder(livyConf)
  }

  /**
   * Represents a path that is either allowed to reference a local file, or must exist in our
   * cache directory or on hdfs.
   */
  sealed trait Path
  case class AbsolutePath(path: String) extends Path
  case class RelativePath(path: String) extends Path
}

class SparkProcessBuilder(livyConf: LivyConf) extends Logging {
  private[this] val fsRoot = livyConf.filesystemRoot()

  private[this] var _executable: Path = AbsolutePath(livyConf.sparkSubmit())
  private[this] var _master: Option[String] = None
  private[this] var _deployMode: Option[String] = None
  private[this] var _className: Option[String] = None
  private[this] var _name: Option[String] = Some("Livy")
  private[this] var _jars: ArrayBuffer[Path] = ArrayBuffer()
  private[this] var _pyFiles: ArrayBuffer[Path] = ArrayBuffer()
  private[this] var _files: ArrayBuffer[Path] = ArrayBuffer()
  private[this] var _conf: ArrayBuffer[(String, String)] = ArrayBuffer()
  private[this] var _driverMemory: Option[String] = None
  private[this] var _driverJavaOptions: Option[String] = None
  private[this] var _driverClassPath: ArrayBuffer[String] = ArrayBuffer()
  private[this] var _executorMemory: Option[String] = None
  private[this] var _proxyUser: Option[String] = None

  private[this] var _driverCores: Option[String] = None
  private[this] var _executorCores: Option[String] = None
  private[this] var _queue: Option[String] = None
  private[this] var _numExecutors: Option[String] = None
  private[this] var _archives: ArrayBuffer[Path] = ArrayBuffer()

  private[this] var _env: ArrayBuffer[(String, String)] = ArrayBuffer()
  private[this] var _redirectOutput: Option[ProcessBuilder.Redirect] = None
  private[this] var _redirectError: Option[ProcessBuilder.Redirect] = None
  private[this] var _redirectErrorStream: Option[Boolean] = None

  def executable(executable: Path): SparkProcessBuilder = {
    _executable = executable
    this
  }

  def master(masterUrl: String): SparkProcessBuilder = {
    _master = Some(masterUrl)
    this
  }

  def deployMode(deployMode: String): SparkProcessBuilder = {
    _deployMode = Some(deployMode)
    this
  }

  def className(className: String): SparkProcessBuilder = {
    _className = Some(className)
    this
  }

  def name(name: String): SparkProcessBuilder = {
    _name = Some(name)
    this
  }

  def jar(jar: Path): SparkProcessBuilder = {
    this._jars += jar
    this
  }

  def jars(jars: Traversable[Path]): SparkProcessBuilder = {
    this._jars ++= jars
    this
  }

  def pyFile(pyFile: Path): SparkProcessBuilder = {
    this._pyFiles += pyFile
    this
  }

  def pyFiles(pyFiles: Traversable[Path]): SparkProcessBuilder = {
    this._pyFiles ++= pyFiles
    this
  }

  def file(file: Path): SparkProcessBuilder = {
    this._files += file
    this
  }

  def files(files: Traversable[Path]): SparkProcessBuilder = {
    this._files ++= files
    this
  }

  def conf(key: String, value: String): SparkProcessBuilder = {
    this._conf += ((key, value))
    this
  }

  def conf(conf: Traversable[(String, String)]): SparkProcessBuilder = {
    this._conf ++= conf
    this
  }

  def driverMemory(driverMemory: String): SparkProcessBuilder = {
    _driverMemory = Some(driverMemory)
    this
  }

  def driverJavaOptions(driverJavaOptions: String): SparkProcessBuilder = {
    _driverJavaOptions = Some(driverJavaOptions)
    this
  }

  def driverClassPath(classPath: String): SparkProcessBuilder = {
    _driverClassPath += classPath
    this
  }

  def driverClassPaths(classPaths: Traversable[String]): SparkProcessBuilder = {
    _driverClassPath ++= classPaths
    this
  }

  def executorMemory(executorMemory: String): SparkProcessBuilder = {
    _executorMemory = Some(executorMemory)
    this
  }

  def proxyUser(proxyUser: String): SparkProcessBuilder = {
    _proxyUser = Some(proxyUser)
    this
  }

  def driverCores(driverCores: Int): SparkProcessBuilder = {
    this.driverCores(driverCores.toString)
  }

  def driverCores(driverCores: String): SparkProcessBuilder = {
    _driverCores = Some(driverCores)
    this
  }

  def executorCores(executorCores: Int): SparkProcessBuilder = {
    this.executorCores(executorCores.toString)
  }

  def executorCores(executorCores: String): SparkProcessBuilder = {
    _executorCores = Some(executorCores)
    this
  }


  def numExecutors(numExecutors: Int): SparkProcessBuilder = {
    this.numExecutors(numExecutors.toString)
  }

  def numExecutors(numExecutors: String): SparkProcessBuilder = {
    _numExecutors = Some(numExecutors)
    this
  }

  def queue(queue: String): SparkProcessBuilder = {
    _queue = Some(queue)
    this
  }

  def archive(archive: Path): SparkProcessBuilder = {
    _archives += archive
    this
  }

  def archives(archives: Traversable[Path]): SparkProcessBuilder = {
    archives.foreach(archive)
    this
  }

  def env(key: String, value: String): SparkProcessBuilder = {
    _env += ((key, value))
    this
  }

  def redirectOutput(redirect: ProcessBuilder.Redirect): SparkProcessBuilder = {
    _redirectOutput = Some(redirect)
    this
  }

  def redirectError(redirect: ProcessBuilder.Redirect): SparkProcessBuilder = {
    _redirectError = Some(redirect)
    this
  }

  def redirectErrorStream(redirect: Boolean): SparkProcessBuilder = {
    _redirectErrorStream = Some(redirect)
    this
  }

  def start(file: Path, args: Traversable[String]): SparkProcess = {
    var args_ = ArrayBuffer(fromPath(_executable))

    def addOpt(option: String, value: Option[String]): Unit = {
      value.foreach { v =>
        args_ += option
        args_ += v
      }
    }

    def addList(option: String, values: Traversable[String]): Unit = {
      if (values.nonEmpty) {
        args_ += option
        args_ += values.mkString(",")
      }
    }

    addOpt("--master", _master)
    addOpt("--deploy-mode", _deployMode)
    addOpt("--name", _name)
    addList("--jars", _jars.map(fromPath))
    addList("--py-files", _pyFiles.map(fromPath))
    addList("--files", _files.map(fromPath))
    addOpt("--class", _className)
    addList("--conf", _conf.map { case (key, value) => f"$key=$value" })
    addOpt("--driver-memory", _driverMemory)
    addOpt("--driver-java-options", _driverJavaOptions)
    addList("--driver-class-path", _driverClassPath)
    addOpt("--driver-cores", _driverCores)
    addOpt("--executor-memory", _executorMemory)

    if (livyConf.getBoolean(LivyConf.IMPERSONATION_ENABLED_KEY, true)) {
      addOpt("--proxy-user", _proxyUser)
    }

    addOpt("--driver-cores", _driverCores)
    addOpt("--executor-cores", _executorCores)
    addOpt("--num-executors", _numExecutors)
    addOpt("--queue", _queue)
    addList("--archives", _archives.map(fromPath))

    args_ += fromPath(file)
    args_ ++= args

    val argsString = args_
      .map("'" + _.replace("'", "\\'") + "'")
      .mkString(" ")

    info(s"Running $argsString")

    val pb = new ProcessBuilder(args_)
    val env = pb.environment()

    for ((key, value) <- _env) {
      env.put(key, value)
    }

    _redirectOutput.foreach(pb.redirectOutput)
    _redirectError.foreach(pb.redirectError)
    _redirectErrorStream.foreach(pb.redirectErrorStream)

    SparkProcess(pb.start())
  }

  private def fromPath(path: Path) = path match {
    case AbsolutePath(p) => p
    case RelativePath(p) =>
      if (p.startsWith("hdfs://")) {
        p
      } else {
        fsRoot + "/" + p
      }
  }
}
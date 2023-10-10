/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.alstanchev.pekko.persistence.inmemory.util

import java.io.InputStream
import javax.xml.stream.{ XMLInputFactory, XMLEventReader }

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.scaladsl.{ Source, StreamConverters }
import org.apache.pekko.util.ByteString

import scala.concurrent.Future
import scala.io.{ Source => ScalaIOSource }
import scala.util.Try

object ClasspathResources extends ClasspathResources

trait ClasspathResources {
  def withInputStream[T](fileName: String)(f: InputStream => T): T = {
    val is = fromClasspathAsStream(fileName)
    try f(is) finally Try(is.close())
  }

  def withXMLEventReader[T](fileName: String)(f: XMLEventReader => T): T =
    withInputStream(fileName) { is =>
      val factory = XMLInputFactory.newInstance()
      val reader = factory.createXMLEventReader(is)
      f(reader)
    }

  def withXMLEventSource[T](fileName: String)(f: Source[javax.xml.stream.events.XMLEvent, NotUsed] => T): T =
    withXMLEventReader(fileName) { reader =>
      f(Source.fromIterator(() => new Iterator[javax.xml.stream.events.XMLEvent] {
        override def hasNext: Boolean = reader.hasNext

        override def next(): javax.xml.stream.events.XMLEvent = reader.nextEvent()
      }))
    }

  def withByteStringSource[T](fileName: String)(f: Source[ByteString, Future[IOResult]] => T): T =
    withInputStream(fileName) { inputStream =>
      f(StreamConverters.fromInputStream(() => inputStream))
    }

  def streamToString(is: InputStream): String =
    ScalaIOSource.fromInputStream(is).mkString

  def fromClasspathAsString(fileName: String): String =
    streamToString(fromClasspathAsStream(fileName))

  def fromClasspathAsStream(fileName: String): InputStream =
    getClass.getClassLoader.getResourceAsStream(fileName)
}

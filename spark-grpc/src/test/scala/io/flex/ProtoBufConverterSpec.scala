package io.flex

import collection.mutable.Stack
import org.scalatest._
import flatspec._
import io.flex.Sample._
import matchers._
import org.scalatest.funsuite.AnyFunSuite
import scala.jdk.CollectionConverters._

class ProtoBufConverterSpec extends AnyFunSuite with should.Matchers {
  val converter = new ProtoBufConverter
  converter.registerMessage(classOf[Address])
  converter.registerEnum(classOf[AddressType])
  converter.registerMessage(classOf[Subject])
  converter.registerMessage(classOf[Person])
  class JSubject(val name: String, val priority: Int)
  class JSubjectWithOption(val name: Option[String], val priority: Option[Int])
  object JAddressType extends Enumeration {
    val HOME, WORK = Value;
  }
  class JAddress (val address: String, val nums: Array[Int], val `type`: JAddressType.Value)
  class JPerson (val name: String, val id: List[Int], val addr: JAddress, val subjects: Map[String, JSubject])

  val j1 = new JAddress("Hanoi, Vietnam", Array(100,101), JAddressType.WORK)
  val s1 = new JSubject("math", 0)
  val s2 = new JSubject("eng", 1)
  val p1 = new JPerson("duan", List(1,2), j1, Map("math" -> s1,"eng" -> s2))

  test("convert enum") {
    val work = converter.convert("io.flex.AddressType", "WORK")
    assert(work == AddressType.WORK)
    val home = converter.convert("io.flex.AddressType", JAddressType.HOME)
    assert (home == AddressType.HOME)
  }

  test("convert simple message") {
    val sub: Subject = converter
      .convert("io.flex.Subject", new JSubject("math", 1))
      .asInstanceOf[Subject]
    val osub = Subject.newBuilder().setName("math").build()
    assert (sub.isInstanceOf[Subject])

    assert (sub.getName == osub.getName)
    assert (sub.getPriority == 1)
  }

  test("convert simple message with optional type") {
    val sub1: Subject = converter
      .convert("io.flex.Subject", new JSubjectWithOption(Some("math"), None))
      .asInstanceOf[Subject]
    assert (sub1.isInstanceOf[Subject])
    assert (sub1.getName == "math")
    assert (sub1.getPriority == 0)
    val sub2: Subject = converter
      .convert("io.flex.Subject", new JSubjectWithOption(None,Some(3)))
      .asInstanceOf[Subject]
    assert (sub2.isInstanceOf[Subject])
    assert (sub2.getName == "")
    assert (sub2.getPriority == 3)
  }

  test("convert map field and nested message") {
    val pobj = converter.convert("io.flex.Person", p1)
      .asInstanceOf[Person]
    assert (pobj.getName == "duan")
    assert (pobj.getIdList.asScala == p1.id)
    assert (pobj.getAddr == converter.convert("io.flex.Address", j1))
    assert(pobj.getSubjectsMap.get("math").getPriority == 0)
    assert(pobj.getSubjectsMap.keySet().asScala.toList == List("math","eng"))
  }

}

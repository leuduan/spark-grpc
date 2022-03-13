package io.flex

import com.google.protobuf.Descriptors.{Descriptor, EnumDescriptor}
import com.google.protobuf.{Descriptors, DynamicMessage, GeneratedMessageV3}

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Try

class ProtoBufConverter {
  private val registry: mutable.Map[String, Any => Any] = mutable.Map()

  registry("string") = (in: Any) => in.toString
  registry("int32") = (in: Any) => in.asInstanceOf[Int]
  registry("int64") = (in: Any) => in.asInstanceOf[Long]
  registry("double") = (in: Any) => in.asInstanceOf[Double]
  registry("bool") = (in: Any) => in.asInstanceOf[Boolean]
  registry("float") = (in: Any) => in.asInstanceOf[Float]

  def registerMessage(cls: Class[_]): Unit = {
    val newBuilderMethod = cls.getDeclaredMethod("newBuilder")
    val getDescriptorMethod = cls.getDeclaredMethod("getDescriptor")
    val builder = newBuilderMethod.invoke(null).asInstanceOf[GeneratedMessageV3.Builder[_]]
    val descriptor = getDescriptorMethod.invoke(null).asInstanceOf[Descriptor]

    def fn(in: Any): Any = {
      descriptor.getFields.forEach { fdesc =>
        val jfield = in.getClass.getDeclaredField(fdesc.getName)
        jfield.setAccessible(true)
        val jvalue = jfield.get(in)
        import Descriptors.FieldDescriptor.Type
        val converterFn = fdesc.getType match {
          case Type.INT32 | Type.UINT32 | Type.SINT32 => registry("int32")
          case Type.INT64 | Type.UINT64 | Type.SINT64 => registry("int64")
          case Type.STRING => registry("string")
          case Type.DOUBLE => registry("double")
          case Type.BOOL => registry("bool")
          case Type.FLOAT => registry("float")
          case Type.ENUM => {
            val enumName = fdesc.getEnumType.getFullName
            val fn = (in: Any) => {
              val enumValue = registry(enumName)(in)
              val getValueDescriptorMethod = enumValue.getClass.getDeclaredMethod("getValueDescriptor")
              getValueDescriptorMethod.invoke(enumValue)
            }
            fn
          }
          case Type.MESSAGE => {
            if (!fdesc.isMapField) {
              val messageType = fdesc.getMessageType.getFullName
              registry(messageType)
            } else {
              val entryMessageType = fdesc.getMessageType
              (in: Any) => {
                val keyField = entryMessageType.findFieldByName("key")
                val keyType: String = keyField.getType match {
                  case Type.STRING => "string"
                  case Type.INT32 => "int32"
                  case Type.INT64 => "int64"
                  case Type.MESSAGE => keyField.getMessageType.getFullName
                  case _ => ???
                }
                val valueField = entryMessageType.findFieldByName("value")
                val valueType: String = valueField.getType match {
                  case Type.STRING => "string"
                  case Type.INT32 => "int32"
                  case Type.INT64 => "int64"
                  case Type.MESSAGE => valueField.getMessageType.getFullName
                  case _ => ???
                }
                val l: java.util.List[DynamicMessage] = new util.LinkedList()
                in.asInstanceOf[Map[_, _]].map { it =>
                  val builder = DynamicMessage.newBuilder(entryMessageType)
                  builder.setField(entryMessageType.findFieldByName("key"), registry(keyType)(it._1))
                  builder.setField(entryMessageType.findFieldByName("value"), registry(valueType)(it._2))
                  builder.build()
                }.foreach(l.add)
                l
              }
            }
          }

          case _ => ???
        }
        val isRepeated = fdesc.isRepeated && !fdesc.isMapField
        if (isRepeated) {
          val _jlist: Iterable[_] = jvalue match {
            case arr: Array[_] => Iterable.from(arr)
            case iterable: Iterable[_] => iterable
          }
          val jlist: java.util.List[Any] = _jlist.map(converterFn).toList.asJava
          builder.setField(fdesc, jlist)
        } else {
          // handle Option[T]
          val pvalue = jvalue match {
            case o: Option[_] => o match {
              case Some(v) => Some(v)
              case None => None
            }
            case _ => Some(jvalue)
          }
          if
          (pvalue.nonEmpty) builder.setField(fdesc, converterFn(pvalue.get))
          else builder.clearField(fdesc)
        }
      }
      val pobject = builder.build()
      pobject
    }

    registry(descriptor.getFullName) = fn
  }

  def registerEnum(cls: Class[_]): Unit = {
    val valueOfMethod = cls.getDeclaredMethod("valueOf", classOf[String])
    val getDescriptorMethod = cls.getDeclaredMethod("getDescriptor")
    val descriptor = getDescriptorMethod.invoke(null).asInstanceOf[EnumDescriptor]

    def fn(in: Any): Any = {
      val nameMethod = Try {
        in.getClass.getDeclaredMethod("toString")
      }.getOrElse {
        in.getClass.getMethod("toString")
      }
      valueOfMethod.invoke(null, nameMethod.invoke(in))
    }
    registry(descriptor.getFullName) = fn
  }

  def convert(protobufType: String, obj: Any): Any = {
    registry(protobufType)(obj)
  }

  def registerAny(protobufType: String, fn: Any => Any) = {
    registry(protobufType) = fn
  }
}
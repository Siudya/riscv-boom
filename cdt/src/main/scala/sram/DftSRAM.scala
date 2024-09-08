package xs.utils.sram
import chisel3._
import chisel3.util._

object DftSRAM {
  def apply[T <: Bits] (
    name: String,
    desc: String,
    size: BigInt, // depth
    data: T,
  ):CdtSram[T] = {
    val sram = Module(new CdtSram(data, size.toInt))
    sram.io.r.req := DontCare
    sram.io.w := DontCare
    sram.io.w.req.valid := false.B
    sram
  }

  def apply[T <: Data] (
    name: String,
    desc: String,
    size: BigInt, // depth
    data: Vec[T]
  ): CdtSramMask[T] = {
    val sram = Module(new CdtSramMask(data.head, data.size, size.toInt))
    sram.io.r.req := DontCare
    sram.io.w := DontCare
    sram.io.w.req.valid := false.B
    sram
  }
}

class CdtSram[T <: Data](
  gen:          T,
  set:          Int
) extends SRAMTemplate(
  gen = gen,
  set = set,
  bypassWrite = true,
  hasMbist = true,
  foundry = "UNKNOWN"
) {
  def read(x: UInt, en: Bool): T = {
    io.r.req.valid := en
    io.r.req.bits.setIdx := x
    io.r.resp.data.head
  }

  def read(x:UInt):T = {
    io.r.req.valid := true.B
    io.r.req.bits.setIdx := x
    io.r.resp.data.head
  }

  def write(idx: UInt, data: T): Unit = {
    io.w.req.valid := true.B
    io.w.req.bits.setIdx := idx
    io.w.req.bits.data.head := data
  }
}

class CdtSramMask[T <: Data](
  gen:          T,
  way:          Int,
  set:          Int
) extends SRAMTemplate(
  gen = gen,
  way = way,
  set = set,
  bypassWrite = true,
  hasMbist = true,
  foundry = "UNKNOWN"
) {
  def read(x: UInt, en: Bool): Vec[T] = {
    io.r.req.valid := en
    io.r.req.bits.setIdx := x
    io.r.resp.data
  }

  def read(x:UInt): Vec[T] = {
    io.r.req.valid := true.B
    io.r.req.bits.setIdx := x
    io.r.resp.data
  }

  def write(idx: UInt, data: Vec[T], mask: UInt): Unit = {
    io.w.req.valid := true.B
    io.w.req.bits.setIdx := idx
    io.w.req.bits.data := data
    io.w.req.bits.waymask.foreach(_ := mask)
  }

  def write(idx: UInt, data: Vec[T], mask:Seq[Bool]): Unit = {
    io.w.req.valid := true.B
    io.w.req.bits.setIdx := idx
    io.w.req.bits.data := data
    io.w.req.bits.waymask.foreach(_ := Cat(mask.reverse))
  }
}
package boom.v3
import boom.v3.common._
import boom.v3.exu.{BoomCore, IssueParams}
import boom.v3.ifu.{BoomFrontend, FtqParameters}
import boom.v3.lsu.{BoomNonBlockingDCache, LSU}
import boom.v3.util.BoomCoreStringPrefix
import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{ChiselStage, FirtoolOption}
import freechips.rocketchip.devices.tilelink.{BasicBusBlocker, BasicBusBlockerParams}
import freechips.rocketchip.diplomacy.RegionType.TRACKED
import freechips.rocketchip.diplomacy.{AddressSet, TransferSizes}
import freechips.rocketchip.interrupts._
import freechips.rocketchip.resources.{BindingScope, SimpleBus}
import org.chipsalliance.diplomacy.lazymodule.{LazyModule, LazyModuleImp}
import org.chipsalliance.diplomacy.bundlebridge._
import freechips.rocketchip.rocket.{DCacheParams, HellaCacheArbiter, HellaCacheIO, ICacheParams, PTW}
import freechips.rocketchip.tile.{HasNonDiplomaticTileParameters, MaxHartIdBits, PriorityMuxHartIdFromSeq, TileKey, TileVisibilityNodeKey, TraceBundle}
import org.chipsalliance.cde.config.{Config, Field, Parameters}
import freechips.rocketchip.tilelink._
import org.chipsalliance.diplomacy.{DisableMonitors, ValName}

import scala.collection.mutable.ListBuffer

case object MngParamsKey extends Field[TLSlavePortParameters]

class TileCfg extends Config((site, here, up) => {
  case TileKey => BoomTileParams(
    core = BoomCoreParams(
      fetchWidth = 8,
      decodeWidth = 4,
      numRobEntries = 128,
      issueParams = Seq(
        IssueParams(issueWidth=2, numEntries=24, iqType=IQT_MEM.litValue, dispatchWidth=4),
        IssueParams(issueWidth=4, numEntries=40, iqType=IQT_INT.litValue, dispatchWidth=4),
        IssueParams(issueWidth=2, numEntries=32, iqType=IQT_FP.litValue , dispatchWidth=4)),
      numIntPhysRegisters = 128,
      numFpPhysRegisters = 128,
      numLdqEntries = 32,
      numStqEntries = 32,
      maxBrCount = 20,
      numFetchBufferEntries = 32,
      enablePrefetching = true,
      ftq = FtqParameters(nEntries=40),
      fpu = Some(freechips.rocketchip.tile.FPUParams(sfmaLatency=4))
    ),
    dcache = Some(
      DCacheParams(rowBits = 128, nSets=64, nWays=8, nMSHRs=8, nTLBWays=32)
    ),
    icache = Some(
      ICacheParams(rowBits = 128, nSets=64, nWays=8, fetchBytes=4*4)
    ),
    tileId = 0
  )
  case MaxHartIdBits => 16
  case MngParamsKey => TLSlavePortParameters.v1(
    managers = Seq(TLSlaveParameters.v1(
      address            = AddressSet.misaligned(0, 1024 * 1024 * 128),
      resources          = new SimpleBus("master", Nil).ranges,
      executable         = true,
      supportsAcquireT   = TransferSizes(64, 64),
      supportsAcquireB   = TransferSizes(64, 64),
      supportsGet        = TransferSizes(1, 16),
      supportsPutFull    = TransferSizes(1, 16),
      supportsPutPartial = TransferSizes(1, 16),
      regionType         = TRACKED)),
    beatBytes = 32,
    endSinkId = 1024,
    minLatency = 16
  )
})

class RawTileTop(implicit p:Parameters) extends LazyModule with BindingScope
  with HasNonDiplomaticTileParameters {
  private val q = p.alterMap(Map(
    TileVisibilityNodeKey -> TLEphemeralNode()(ValName("tile_master")),
  ))
  private val tlMasterXbar = LazyModule(new TLXbar(nameSuffix = Some(s"MasterXbar")))
  lazy private val dcache: BoomNonBlockingDCache = LazyModule(new BoomNonBlockingDCache(tileId)(q))
  private val frontend = LazyModule(new BoomFrontend(tileParams.icache.get, tileId)(q))
  private val tlMasterBuffer = LazyModule(new TLBuffer())
  private val resetVectorNode = BundleBridgeSource(frontend.resetVectorSinkNode.genOpt)
  private val masterNode = TLManagerNode(Seq(p(MngParamsKey)))

  tlMasterXbar.node := TLWidthWidget(tileParams.dcache.get.rowBits/8) := q(TileVisibilityNodeKey) := dcache.node
  tlMasterXbar.node := TLWidthWidget(tileParams.icache.get.rowBits/8) := frontend.masterNode
  masterNode := tlMasterBuffer.node := tlMasterXbar.node
  frontend.resetVectorSinkNode := resetVectorNode

  lazy val module = new Impl

  class Impl extends LazyModuleImp(this) {
    val tl = masterNode.makeIOs()
    val reset_vector = resetVectorNode.makeIOs()

    private val core = Module(new BoomCore()(q))
    private val lsu = Module(new LSU()(q, dcache.module.edge))
    private val ptwPorts         = ListBuffer(lsu.io.ptw, frontend.module.io.ptw, core.io.ptw_tlb)
    private val hellaCachePorts  = ListBuffer[HellaCacheIO]()

    val io = IO(new Bundle {
      val intr = Input(core.io.interrupts.cloneType)
      val trace = Output(core.io.trace.cloneType)
      val hartid = Input(UInt(p(MaxHartIdBits).W))
    })
    core.io.interrupts := io.intr
    io.trace <> core.io.trace
    core.io.hartid := io.hartid

    frontend.module.io.cpu <> core.io.ifu
    core.io.lsu <> lsu.io.core
    core.io.rocc := DontCare

    private val ptw  = Module(new PTW(ptwPorts.length)(dcache.node.edges.out.head, q))
    core.io.ptw <> ptw.io.dpath
    ptw.io.requestor <> ptwPorts.toSeq
    ptw.io.mem +=: hellaCachePorts

    private val hellaCacheArb = Module(new HellaCacheArbiter(hellaCachePorts.length)(q))
    hellaCacheArb.io.requestor <> hellaCachePorts.toSeq
    lsu.io.hellacache <> hellaCacheArb.io.mem
    dcache.module.io.lsu <> lsu.io.dmem

    private val frontendStr = frontend.module.toString
    private val coreStr = core.toString
    private val boomTileStr =
      (BoomCoreStringPrefix(s"======BOOM Tile $tileId Params======") + "\n"
        + frontendStr
        + coreStr + "\n")
    override def toString: String = boomTileStr
    print(boomTileStr)
  }
}

object TopMain extends App {
  val cfg = new TileCfg
  private val tile = DisableMonitors(p => LazyModule(new RawTileTop()(p)))(cfg)
  (new ChiselStage).execute(args, Seq(
    FirtoolOption("-O=release"),
    FirtoolOption("--disable-all-randomization"),
    FirtoolOption("--disable-annotation-unknown"),
    FirtoolOption("--strip-debug-info"),
    FirtoolOption("--lower-memories"),
    FirtoolOption("--add-vivado-ram-address-conflict-synthesis-bug-workaround"),
    FirtoolOption("--lowering-options=noAlwaysComb," +
      " disallowPortDeclSharing, disallowLocalVariables," +
      " emittedLineLength=120, explicitBitcast, locationInfoStyle=plain," +
      " disallowExpressionInliningInPorts, disallowMuxInlining"),
    ChiselGeneratorAnnotation(() => tile.module)
  ))
}
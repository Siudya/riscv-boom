idea:
	mill -i mill.idea.GenIdea/idea

init:
	git submodule update --init
	cd rocket-chip/dependencies && git submodule update --init cde hardfloat diplomacy

comp:
	mill -i boom.compile

v3-firrtl:
	@mkdir -p build/v3/firrtl
	mill -i boom.runMain boom.v3.TopMain --full-stacktrace -td build/v3/firrtl --target firrtl

v4-firrtl:
	@mkdir -p build/v4/firrtl
	mill -i boom.runMain boom.v4.TopMain --full-stacktrace -td build/v4/firrtl --target firrtl

FIRTOOL_OPT = --disable-all-randomization --disable-annotation-unknown --strip-debug-info
FIRTOOL_OPT += --lower-memories --add-vivado-ram-address-conflict-synthesis-bug-workaround
v3: v3-firrtl
	@mkdir -p build/v3/rtl
	firtool -o build/v3/rtl $(FIRTOOL_OPT) --format mlir --split-verilog build/v3/firrtl/RawTileTop.fir.mlir

v4: v4-firrtl
	@mkdir -p build/v4/rtl
	firtool -o build/v4/rtl $(FIRTOOL_OPT) --format mlir --split-verilog build/v4/firrtl/RawTileTop.fir.mlir

help:
	mill -i boom.runMain boom.v3.TopMain --help

clean:
	rm -r build

.PHONY: clean
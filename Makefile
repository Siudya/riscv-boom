idea:
	mill -i mill.idea.GenIdea/idea

init:
	git submodule update --init
	cd rocket-chip/dependencies && git submodule update --init cde hardfloat diplomacy

comp:
	mill -i boom.compile
	
v3:
	mkdir -p build/v3/rtl
	mill -i boom.runMain boom.v3.TopMain --full-stacktrace -td build/v3/rtl --target systemverilog --split-verilog

v4:
	mkdir -p build/v4/rtl
	mill -i boom.runMain boom.v4.TopMain --full-stacktrace -td build/v4/rtl --target systemverilog --split-verilog

help:
	mill -i boom.runMain boom.v3.TopMain --help

clean:
	rm -r build

.PHONY: clean
# Define where to find the ".raw" files,
# i.e. the output from the rx433 C program.
DATA=data

# Define your Java and Groovy versions
JAVA=${JAVA_HOME}/bin/java
JAVAC=${JAVA_HOME}/bin/javac
GROOVY=${GROOVY_HOME}/bin/groovy

# Source file directories
JSRC=src/main/java
GSRC=src/main/groovy
# Class file directory
BIN=bin

RAW=$(shell ls $(DATA)/*.raw)
PULSE=$(RAW:.raw=.pulse)
VCD=$(RAW:.raw=.vcd)
CLEAN=$(RAW:.raw=.clean)

all: $(CLEAN)

$(CLEAN): $(PULSE)
	$(GROOVY) $(GSRC)/Analyze.groovy $(PULSE)

$(PULSE): $(BIN)/VcdWriter.class $(RAW)
	$(JAVA) -classpath $(BIN) VcdWriter $(DATA)

$(BIN)/%.class: $(JSRC)/%.java
	mkdir -p $(BIN)
	$(JAVAC) -d $(BIN) -classpath $(JSRC):$(BIN) $(JSRC)/VcdWriter.java $(JSRC)/Edge.java

.PHONY: clean

clean:
	rm -f $(BIN)/*.class
	rm -f $(PULSE) $(CLEAN) $(VCD)

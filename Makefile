EXE = MyEngine
JAR = target/Peperobot_chess-1.0-SNAPSHOT.jar

$(EXE):
	mvn -q install:install-file -Dfile=libs/JCB-1.3.0.jar -DgroupId=com.pepero -DartifactId=jcb -Dversion=1.3.0 -Dpackaging=jar
	mvn -q -B package
	cp $(JAR) ./$(EXE).jar
	printf '#!/bin/sh\nDIR="$$(cd "$$(dirname "$$0")" && pwd)"\nexec java -jar "$$DIR/$(EXE).jar" "$$@"\n' > $(EXE)
	chmod +x $(EXE)
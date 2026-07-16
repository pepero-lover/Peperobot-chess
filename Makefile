EXE = MyEngine
JAR = target/Peperobot_chess-1.0-SNAPSHOT.jar

$(EXE):
	mvn -q install:install-file -Dfile=libs/JCB-1.3.0.jar -DgroupId=com.pepero -DartifactId=jcb -Dversion=1.3.0 -Dpackaging=jar
	mvn -q -B package
	printf '#!/bin/sh\nexec java -jar "$(CURDIR)/$(JAR)" "$$@"\n' > $(EXE)
	chmod +x $(EXE)

clean:
	mvn -q clean
	rm -f $(EXE)
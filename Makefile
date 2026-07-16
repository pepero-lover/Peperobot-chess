EXE = MyEngine
JAR = target/Peperobot_chess-1.0-SNAPSHOT.jar

$(EXE):
	mvn -q install:install-file -Dfile=libs/JCB-1.3.0.jar -DgroupId=com.pepero -DartifactId=jcb -Dversion=1.3.0 -Dpackaging=jar
	mvn -q -B package
	echo '#!/bin/sh' > $(EXE)
	echo 'TMPJAR=$$(mktemp /tmp/engine-XXXXXX.jar)' >> $(EXE)
	echo 'trap "rm -f $$TMPJAR" EXIT' >> $(EXE)
	echo -n 'base64 -d << '"'"'EOF'"'"' > "$$TMPJAR"' >> $(EXE)
	echo '' >> $(EXE)
	base64 -w0 $(JAR) >> $(EXE)
	echo '' >> $(EXE)
	echo 'EOF' >> $(EXE)
	echo 'exec java -jar "$$TMPJAR" "$$@"' >> $(EXE)
	chmod +x $(EXE)
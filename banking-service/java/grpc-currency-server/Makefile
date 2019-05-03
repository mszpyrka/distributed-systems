idl:
	mvn -Dskiptests package

run-rates-provider:
	mvn -DskipTests package exec:java -Dexec.mainClass=banking.App -Dexec.args="127.0.0.1 9990"

clean:
	rm -rf ./target

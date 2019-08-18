.PHONY: test

test:
	COAST_ENV=test clj -Atest

repl:
	clj -Arepl

clean:
	rm -rf target

pom:
	clj -Spom

release: test
	mvn deploy

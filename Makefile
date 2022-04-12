.PHONY: test

test:
	COAST_ENV=test clj -M\:test

repl:
	clj -M\:repl

clean:
	rm -rf target

pom:
	clj -Spom

deploy: test
	mvn deploy

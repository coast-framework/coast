.PHONY: test

test:
	COAST_ENV=test clj -A\:test

repl:
	clj -A\:repl

clean:
	rm -rf target

pom:
	clj -Spom

deploy: test
	mvn deploy

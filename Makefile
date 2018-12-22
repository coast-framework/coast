.PHONY: test

test:
	clj -A\:test

repl:
	clj -R:nrepl bin/repl.clj

clean:
	rm -rf target

pom:
	clj -Spom

deploy: test
	mvn deploy

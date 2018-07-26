.PHONY: test

test:
	clj -A\:test

repl:
	clj -R:nrepl bin/repl.clj

clean:
	rm -rf target

deploy: test
	clj -Spom
	mvn deploy

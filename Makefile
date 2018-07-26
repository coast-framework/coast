.PHONY: test

test:
	clj -A\:test

repl:
	clj -R:nrepl bin/repl.clj

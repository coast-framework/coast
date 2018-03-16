.PHONY: test

test:
	clj -A\:test

nrepl:
	clj -R:nrepl:cider bin/nrepl.clj

.PHONY: test

run:
	clj -m server

test:
	clj -A\:test

clean:
	rm -rf target

uberjar:
	clj -A\:uberjar

server: uberjar
	java -jar target/test-project.jar -m server

nrepl:
	clj -R:nrepl:cider bin/nrepl.clj

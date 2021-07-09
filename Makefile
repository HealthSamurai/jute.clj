.PHONY: test repl js jstest

repl:
	clj -A:test:nrepl -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]"

test:
	clojure -A:test:runner

js/jute.js:
	clojure -A:cljs -m cljs.main --target webworker --optimizations advanced --output-to "js/jute.js" --output-dir "js" -c jute.js

jstest: js/jute.js
	node --version && cd test && `npm bin`/tap --no-coverage runner.js

js: js/jute.js

jar:
	clojure -A:jar

clojars-push:
	clojure -Spom && clojure -A:deploy

demo: js/jute.js
	cp js/jute.js demo/jute.js

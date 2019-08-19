.PHONY: test repl js jstest

repl:
	clj -A:test:nrepl -e "(-main)" -r

test:
	clojure -A:test:runner

js:
	clojure -A:cljs -m cljs.main --optimizations advanced \
				--target nodejs --output-to "js/jute.js" --output-dir "js" -c jute.js

jstest:
	node --version && cd test && npm install && `npm bin`/tap --no-coverage runner.js

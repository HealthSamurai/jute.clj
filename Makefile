.PHONY: test repl

repl:
	clj -A:test:nrepl -e "(-main)" -r

test:
	clojure -A:test:runner

jstest:
	echo "JS tests goes here" && node --version

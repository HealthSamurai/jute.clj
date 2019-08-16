.PHONY: test repl

repl:
	clj -A:test:nrepl -e "(-main)" -r

test:
	clojure -A:test:runner

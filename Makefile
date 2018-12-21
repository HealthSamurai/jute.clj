IMAGE_TAG  = healthsamurai/jute-clj-demo:demo
build:
	clj -A:uberjar
	docker build -f Dockerfile -t ${IMAGE_TAG} .
	docker push ${IMAGE_TAG}

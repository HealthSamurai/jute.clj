IMAGE_TAG  = healthsamurai/jute-clj-demo:$(shell git rev-parse --short HEAD)
build:
	clj -A:uberjar
	docker build -f Dockerfile -t ${IMAGE_TAG} .
	docker push ${IMAGE_TAG}

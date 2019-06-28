.PHONY: help clean compile test
.DEFAULT_GOAL := help

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

clean: ## clean
	@sbt clean

compile: ## compile
	 @sbt compile

cross-compile: ## cross-compile
	@sbt +compile

test: ## test
	@sbt test

cross-test: ## cross test
	@sbt +test
